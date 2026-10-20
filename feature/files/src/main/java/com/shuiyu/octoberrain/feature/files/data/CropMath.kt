package com.shuiyu.octoberrain.feature.files.data

import kotlin.math.ceil
import kotlin.math.floor

/**
 * 归一化裁剪矩形。
 *
 * 四个分量取 0 至 1，相对当前方向后的图片边界；左上角为原点，向右与向下为正方向。
 * 旋转与镜像只改变分量，不改变图片本身，因此预览与导出可以共用同一份矩形。
 */
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** 矩形宽度，单位与分量一致。 */
    val width: Float get() = right - left

    /** 矩形高度，单位与分量一致。 */
    val height: Float get() = bottom - top

    /** 面积是否为正值，用于判断裁剪操作是否可执行。 */
    val isValid: Boolean get() = width > 0f && height > 0f

    companion object {
        /** 覆盖整幅图片的矩形。 */
        val FULL: CropRect = CropRect(0f, 0f, 1f, 1f)

        /**
         * 构造并夹取到 0 与 1 之间的矩形。
         *
         * 参数顺序为左、上、右、下；任一分量越界时按边界夹取。
         */
        fun of(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
        ): CropRect = CropMath.clamp(CropRect(left, top, right, bottom))
    }
}

/** 归一化矩形落到像素网格后的结果，坐标为整数像素。 */
data class PixelRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * 裁剪矩形的几何变换与像素换算。
 *
 * 纯计算对象，不引用 Android 类型，可在本地 JVM 单元测试中直接验证。
 * 旋转换算按顺时针 90 度定义：点 (x, y) 映射为 (1 - y, x)；镜像按水平翻转，
 * 点 (x, y) 映射为 (1 - x, y)。四次旋转或两次镜像回到原矩形。
 */
object CropMath {
    /** 参考小屏宽度，用于选择最小裁剪边长。 */
    private const val SMALL_WIDTH_PX = 480

    /** 小屏最小裁剪边长，像素。 */
    private const val SMALL_MIN_EDGE = 32

    /** 常规最小裁剪边长，像素。 */
    private const val DEFAULT_MIN_EDGE = 48

    /**
     * 把矩形分量夹取到 0 与 1 之间。
     *
     * 参数 rect 为待夹取的矩形；返回新矩形，不修改输入。
     */
    fun clamp(rect: CropRect): CropRect =
        CropRect(
            left = rect.left.coerceIn(0f, 1f),
            top = rect.top.coerceIn(0f, 1f),
            right = rect.right.coerceIn(0f, 1f),
            bottom = rect.bottom.coerceIn(0f, 1f),
        )

    /**
     * 顺时针旋转 90 度后的矩形。
     *
     * 宽高互换，分量按点映射 (x, y) 到 (1 - y, x) 推导。
     */
    fun rotate90(rect: CropRect): CropRect {
        val safe = clamp(rect)
        return CropRect(
            left = 1f - safe.bottom,
            top = safe.left,
            right = 1f - safe.top,
            bottom = safe.right,
        )
    }

    /**
     * 水平镜像后的矩形。
     *
     * 左右分量互换并围绕 0.5 取反，上下保持不变。
     */
    fun mirror(rect: CropRect): CropRect {
        val safe = clamp(rect)
        return CropRect(
            left = 1f - safe.right,
            top = safe.top,
            right = 1f - safe.left,
            bottom = safe.bottom,
        )
    }

    /**
     * 连续顺时针旋转指定次数后的矩形。
     *
     * 参数 quarters 为旋转次数，负数按对 4 取模归一到 0 至 3。
     */
    fun rotate(
        rect: CropRect,
        quarters: Int,
    ): CropRect {
        val turns = ((quarters % 4) + 4) % 4
        var result = clamp(rect)
        repeat(turns) { result = rotate90(result) }
        return result
    }

    /**
     * 把归一化矩形换算为像素矩形。
     *
     * 参数 width 与 height 为原始图片尺寸。左、上边界向下取整，右、下边界向上取整，
     * 结果宽高至少为 1，并夹取在图片范围内。尺寸非正时返回全零矩形。
     */
    fun pixels(
        rect: CropRect,
        width: Int,
        height: Int,
    ): PixelRect {
        if (width <= 0 || height <= 0) return PixelRect(0, 0, 0, 0)
        val safe = clamp(rect)
        val left = floor(safe.left * width).toInt().coerceIn(0, width - 1)
        val top = floor(safe.top * height).toInt().coerceIn(0, height - 1)
        val right = ceil(safe.right * width).toInt().coerceIn(left + 1, width)
        val bottom = ceil(safe.bottom * height).toInt().coerceIn(top + 1, height)
        return PixelRect(left, top, right - left, bottom - top)
    }

    /**
     * 最小裁剪边长。
     *
     * 参数 widthPx 为设备内容区宽度；480 像素及以下取 32，其余取 48。
     */
    fun minEdge(widthPx: Int): Int = if (widthPx <= SMALL_WIDTH_PX) SMALL_MIN_EDGE else DEFAULT_MIN_EDGE

    /**
     * 判断裁剪结果的像素宽高是否都达到最小边长。
     *
     * 参数 rect 为归一化矩形；width 与 height 为原始图片尺寸；minEdge 为最小边长的像素值。
     */
    fun isLargeEnough(
        rect: CropRect,
        width: Int,
        height: Int,
        minEdge: Int,
    ): Boolean {
        val bounds = pixels(rect, width, height)
        return bounds.width >= minEdge && bounds.height >= minEdge
    }
}
