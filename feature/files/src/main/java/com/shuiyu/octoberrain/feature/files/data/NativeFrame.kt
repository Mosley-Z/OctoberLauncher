package com.shuiyu.octoberrain.feature.files.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 本机尺寸处理模式。
 *
 * 三种模式都以当前已应用的编辑结果为基础，输出统一为本次目标屏幕像素尺寸，
 * 区别在于如何处理图片与目标宽高的比例关系。
 */
enum class FrameMode(
    /** 控制条显示文案。 */
    val label: String,
) {
    /** 按本机比例裁剪，另存时缩放至目标像素。 */
    CROP("裁剪"),

    /** 完整图片拉伸到目标宽高，允许变形。 */
    STRETCH("拉伸"),

    /** 完整图片等比居中，另外两边补纯色。 */
    BACKGROUND("背景"),
}

/**
 * 本机尺寸输出参数。
 *
 * 宽高在进入本机尺寸时固定，会话期间保持不变；灰度仅背景模式使用，
 * 取值为 0 至 255 的纯色亮度。
 */
data class FrameSpec(
    /** 目标宽度像素。 */
    val width: Int,
    /** 目标高度像素。 */
    val height: Int,
    /** 处理模式。 */
    val mode: FrameMode,
    /** 背景亮度，0 为黑，255 为白。 */
    val gray: Int = FrameMath.MAX_GRAY,
) {
    /** 宽高是否为正值。 */
    val isValid: Boolean get() = width > 0 && height > 0

    /** 目标宽高比，宽度除以高度。 */
    val ratio: Float get() = width.toFloat() / height

    /** 夹取后的背景亮度。 */
    val safeGray: Int get() = FrameMath.clampGray(gray)

    /** 供界面显示的像素文案。 */
    val label: String get() = "$width × $height"
}

/** 等比放置结果的整数像素矩形。 */
data class FrameFit(
    /** 左边距。 */
    val left: Int,
    /** 上边距。 */
    val top: Int,
    /** 缩放后宽度。 */
    val width: Int,
    /** 缩放后高度。 */
    val height: Int,
)

/**
 * 本机尺寸的纯几何计算。
 *
 * 不依赖 Android 位图，可在本地 JVM 单元测试中直接验证比例约束与贴边结果。
 */
object FrameMath {
    /** 灰度下界，对应纯黑。 */
    const val MIN_GRAY: Int = 0

    /** 灰度上界，对应纯白。 */
    const val MAX_GRAY: Int = 255

    /** 把任意整数夹取到灰度范围。 */
    fun clampGray(value: Int): Int = value.coerceIn(MIN_GRAY, MAX_GRAY)

    /**
     * 计算背景模式的等比放置矩形。
     *
     * 缩放比取宽高比例中的较小者，保证整幅图片被包含；结果至少一组对边贴住画布，
     * 剩余两边均分补色。尺寸取整后夹取在画布内，避免一像素缝隙。
     */
    fun fit(
        width: Int,
        height: Int,
        sourceWidth: Int,
        sourceHeight: Int,
    ): FrameFit {
        val targetReady = width > 0 && height > 0
        val sourceReady = sourceWidth > 0 && sourceHeight > 0
        if (!targetReady || !sourceReady) return FrameFit(0, 0, 0, 0)
        val scale = min(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        val scaledWidth = (sourceWidth * scale).roundToInt().coerceIn(1, width)
        val scaledHeight = (sourceHeight * scale).roundToInt().coerceIn(1, height)
        return FrameFit(
            left = (width - scaledWidth) / 2,
            top = (height - scaledHeight) / 2,
            width = scaledWidth,
            height = scaledHeight,
        )
    }

    /**
     * 在图片内取宽高比为目标比例的最大居中内接矩形。
     *
     * 参数 ratio 为目标宽高比，imageWidth 与 imageHeight 为图片真实像素宽高；
     * 返回相对图片边界的归一化裁剪矩形。比例判断在像素宽高下进行，
     * 不能直接比较归一化矩形的宽高。输入非法时返回整幅图片。
     */
    fun centeredCrop(
        ratio: Float,
        imageWidth: Int,
        imageHeight: Int,
    ): CropRect {
        if (ratio <= 0f || imageWidth <= 0 || imageHeight <= 0) return CropRect.FULL
        val imageRatio = imageWidth.toFloat() / imageHeight
        val cropWidth: Int
        val cropHeight: Int
        if (imageRatio > ratio) {
            cropHeight = imageHeight
            cropWidth = (imageHeight * ratio).roundToInt().coerceIn(1, imageWidth)
        } else {
            cropWidth = imageWidth
            cropHeight = (imageWidth / ratio).roundToInt().coerceIn(1, imageHeight)
        }
        val left = (imageWidth - cropWidth) / 2
        val top = (imageHeight - cropHeight) / 2
        return CropRect(
            left = left.toFloat() / imageWidth,
            top = top.toFloat() / imageHeight,
            right = (left + cropWidth).toFloat() / imageWidth,
            bottom = (top + cropHeight).toFloat() / imageHeight,
        )
    }

    /**
     * 判断选区放大后是否会明显损失清晰度。
     *
     * 参数 cropWidth 为选区原始像素宽度，targetWidth 为目标输出宽度；
     * 选区需要放大时返回 true，用于提示清晰度下降。
     */
    fun upscaled(
        cropWidth: Int,
        cropHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Boolean {
        val sourceReady = cropWidth > 0 && cropHeight > 0
        val targetReady = targetWidth > 0 && targetHeight > 0
        val wider = targetWidth > cropWidth
        val taller = targetHeight > cropHeight
        val bothReady = sourceReady && targetReady
        return bothReady && (wider || taller)
    }
}

/**
 * 本机尺寸的位图合成。
 *
 * 三种模式最终都产出目标宽高的不透明位图；背景模式的底色由灰度值决定，
 * 图片自身透明像素透出底色，最终不保留 Alpha。
 */
object FrameCompose {
    /**
     * 按参数把位图合成为目标尺寸。
     *
     * 参数 source 为当前编辑结果；spec 为目标参数。返回新位图，源位图不被回收；
     * 参数非法时返回源位图本身。
     */
    fun apply(
        source: Bitmap,
        spec: FrameSpec,
    ): Bitmap {
        if (!spec.isValid || source.width <= 0 || source.height <= 0) return source
        return when (spec.mode) {
            FrameMode.CROP, FrameMode.STRETCH ->
                Bitmap.createScaledBitmap(source, spec.width, spec.height, true)
            FrameMode.BACKGROUND -> background(source, spec)
        }
    }

    /** 建立纯色画布并等比居中绘制图片。 */
    private fun background(
        source: Bitmap,
        spec: FrameSpec,
    ): Bitmap {
        val canvasBitmap = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        val gray = spec.safeGray
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                color = Color.rgb(gray, gray, gray)
                style = Paint.Style.FILL
            }
        val fit = FrameMath.fit(spec.width, spec.height, source.width, source.height)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.rgb(gray, gray, gray))
        if (fit.width > 0 && fit.height > 0) {
            canvas.drawBitmap(
                source,
                null,
                RectF(
                    fit.left.toFloat(),
                    fit.top.toFloat(),
                    (fit.left + fit.width).toFloat(),
                    (fit.top + fit.height).toFloat(),
                ),
                paint,
            )
        }
        return canvasBitmap
    }
}
