package com.shuiyu.octoberrain.feature.files.ui.edit

import com.shuiyu.octoberrain.feature.files.data.FrameMath
import com.shuiyu.octoberrain.feature.files.data.FrameMode
import com.shuiyu.octoberrain.feature.files.data.FrameSpec
import com.shuiyu.octoberrain.feature.files.ui.GrayKernels

/** 编辑页的四个页签，顺序与底栏一致。 */
enum class EditTab(
    /** 页签显示文案。 */
    val label: String,
) {
    /** 裁剪。 */
    CROP("裁剪"),

    /** 旋转。 */
    ROTATE("旋转"),

    /** 滤镜。 */
    FILTER("滤镜"),

    /** 另存。 */
    SAVE("另存"),
}

/**
 * 裁剪纵横比选项。
 *
 * 参数 ratio 为宽高比，null 表示自由比例；标签用于控制条绘制。
 */
enum class CropAspect(
    /** 控制条显示文案。 */
    val label: String,
    /** 宽高比，null 表示自由。 */
    val ratio: Float?,
) {
    /** 自由比例。 */
    FREE("自由", null),

    /** 正方形。 */
    SQUARE("1:1", 1f),

    /** 横向四比三。 */
    LANDSCAPE("4:3", 4f / 3f),

    /** 纵向三比四。 */
    PORTRAIT("3:4", 3f / 4f),

    /** 本机尺寸，比例由当前目标屏幕尺寸决定。 */
    NATIVE("本机尺寸", null),
}

/**
 * 本机尺寸目标。
 *
 * 在进入本机尺寸时读取一次完整屏幕逻辑像素并固定，会话期间不重新读取，
 * 避免导出尺寸随窗口或方向变化跳变。
 */
data class NativeTarget(
    /** 目标宽度像素。 */
    val widthPx: Int,
    /** 目标高度像素。 */
    val heightPx: Int,
) {
    /** 目标宽高比。 */
    val ratio: Float get() = widthPx.toFloat() / heightPx

    /** 宽高是否均为正值。 */
    val isValid: Boolean get() = widthPx > 0 && heightPx > 0

    /** 方向文案。 */
    val orientationLabel: String get() = if (widthPx > heightPx) "横向" else "纵向"

    /** 像素文案。 */
    val label: String get() = "$widthPx × $heightPx"
}

/**
 * 归一化裁剪矩形。
 *
 * 四边取 0 至 1，相对当前旋转方向后的图片边界。与 [com.shuiyu.octoberrain.feature.files.data.CropRect]
 * 分量语义一致，此处保留独立类型，便于界面状态参与纯函数测试而不依赖 Android 类。
 */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** 宽度。 */
    val width: Float get() = right - left

    /** 高度。 */
    val height: Float get() = bottom - top

    /** 面积是否为正。 */
    val isValid: Boolean get() = width > 0f && height > 0f

    /** 夹取到 0 与 1 之间，并保证右边界大于左边界、下边界大于上边界。 */
    fun clamp(): NormRect {
        val safeLeft = left.coerceIn(0f, 1f)
        val safeTop = top.coerceIn(0f, 1f)
        val safeRight = right.coerceIn(0f, 1f)
        val safeBottom = bottom.coerceIn(0f, 1f)
        return NormRect(
            left = minOf(safeLeft, safeRight - MIN_SIZE),
            top = minOf(safeTop, safeBottom - MIN_SIZE),
            right = maxOf(safeRight, safeLeft + MIN_SIZE),
            bottom = maxOf(safeBottom, safeTop + MIN_SIZE),
        ).coerceInside()
    }

    /** 把越界的左右或上下边界推回 0 与 1 之内，保持宽高不变。 */
    private fun coerceInside(): NormRect {
        val safeWidth = width.coerceIn(MIN_SIZE, 1f)
        val safeHeight = height.coerceIn(MIN_SIZE, 1f)
        val startLeft = left.coerceIn(0f, 1f - safeWidth)
        val startTop = top.coerceIn(0f, 1f - safeHeight)
        return NormRect(startLeft, startTop, startLeft + safeWidth, startTop + safeHeight)
    }

    companion object {
        /** 归一化坐标下的最小边长。 */
        const val MIN_SIZE: Float = 0.02f

        /** 覆盖整幅图片的矩形。 */
        val FULL: NormRect = NormRect(0f, 0f, 1f, 1f)
    }
}

/**
 * 编辑页状态。
 *
 * 保存页签的操作序列由会话维护，本类型只保存界面选择与待应用裁剪框；
 * 页签切换按固定顺序循环，滤镜与对比度档位在构造状态时完成夹取。
 *
 * 参数 tab 为当前页签；crop 为待应用裁剪框；aspect 为纵横比选项；
 * filterLevel 为滤镜档位；contrastLevel 为对比度档位；rotationQuarters 为已旋转次数；
 * operationCount 为会话内已提交操作数，仅用于另存页摘要；
 * native 为本机尺寸目标；frameMode 为待应用的本机尺寸模式；frameGray 为背景亮度。
 *
 * 状态更新方法成对服务于裁剪、滤镜与本机尺寸三组选择，拆分会把同一批默认值分散到多个
 * 类型，故保留在本类型内。
 */
@Suppress("TooManyFunctions")
data class EditUiState(
    val tab: EditTab = EditTab.CROP,
    val crop: NormRect = NormRect.FULL,
    val aspect: CropAspect = CropAspect.FREE,
    val filterLevel: Int = 0,
    val contrastLevel: Int = 1,
    val rotationQuarters: Int = 0,
    val operationCount: Int = 0,
    val native: NativeTarget? = null,
    val frameMode: FrameMode? = null,
    val frameGray: Int = FrameMath.MAX_GRAY,
) {
    /** 本机尺寸待应用参数，未选择目标或模式时为空。 */
    val pendingFrame: FrameSpec?
        get() {
            val target = native ?: return null
            val mode = frameMode ?: return null
            return FrameSpec(target.widthPx, target.heightPx, mode, FrameMath.clampGray(frameGray))
        }

    /** 滤镜档位文案，取五档名称。 */
    val filterLabel: String get() = FILTER_LABELS[filterLevel.coerceIn(0, FILTER_LABELS.lastIndex)]

    /** 对比度档位文案。 */
    val contrastLabel: String get() = CONTRAST_LABELS[contrastLevel.coerceIn(0, CONTRAST_LABELS.lastIndex)]

    /** 是否存在待保存的修改。 */
    val hasEdits: Boolean get() = operationCount > 0 || filterLevel != 0 || contrastLevel != 1 || pendingFrame != null

    /** 当前裁剪框使用的实际比例，本机尺寸下取目标比例，其余情况取选项比例。 */
    fun effectiveRatio(): Float? = if (aspect == CropAspect.NATIVE) native?.ratio else aspect.ratio

    /** 切换到指定页签。 */
    fun withTab(value: EditTab): EditUiState = copy(tab = value)

    /** 按方向切换页签，步长为 1 或 -1，循环移动。 */
    fun cycleTab(step: Int): EditUiState {
        val values = EditTab.entries
        val index = (values.indexOf(tab) + step).mod(values.size)
        return copy(tab = values[index])
    }

    /**
     * 选择纵横比，裁剪框保持当前矩形。
     *
     * 本机尺寸由 [withNativeTarget] 设置目标，单独选择本机尺寸不改变已有目标。
     * 离开本机尺寸时清除待应用的本机参数，避免留到其他比例下误用。
     */
    fun withAspect(value: CropAspect): EditUiState =
        if (value == CropAspect.NATIVE) {
            copy(aspect = value)
        } else {
            copy(aspect = value, native = null, frameMode = null)
        }

    /** 进入本机尺寸：固定目标尺寸并切换比例选项。 */
    fun withNativeTarget(target: NativeTarget): EditUiState =
        copy(aspect = CropAspect.NATIVE, native = target, frameMode = null)

    /** 选择本机尺寸处理模式。 */
    fun withFrameMode(mode: FrameMode): EditUiState = copy(frameMode = mode, aspect = CropAspect.NATIVE)

    /** 调整背景灰度，越界值夹取到 0 至 255。 */
    fun withFrameGray(value: Int): EditUiState = copy(frameGray = FrameMath.clampGray(value))

    /** 选择滤镜档位，越界值夹取到 0 至 4。 */
    fun withFilterLevel(value: Int): EditUiState = copy(filterLevel = value.coerceIn(0, GrayKernels.LEVELS - 1))

    /** 选择对比度档位，越界值夹取到 0 至 2。 */
    fun withContrastLevel(value: Int): EditUiState = copy(contrastLevel = value.coerceIn(0, GrayKernels.CONTRASTS - 1))

    /** 拖动裁剪框后写入新矩形并夹取。 */
    fun withCrop(value: NormRect): EditUiState = copy(crop = value.clamp())

    /** 顺时针旋转一次。 */
    fun withRotation(): EditUiState = copy(rotationQuarters = (rotationQuarters + 1) % 4, crop = NormRect.FULL)

    /** 重置界面选择，保留已提交操作。 */
    fun resetSelection(): EditUiState =
        copy(
            aspect = CropAspect.FREE,
            crop = NormRect.FULL,
            native = null,
            frameMode = null,
            frameGray = FrameMath.MAX_GRAY,
        )

    companion object {
        /** 五档滤镜名称，顺序与过滤核一致。 */
        val FILTER_LABELS: List<String> = listOf("灰阶", "16 级", "高反差", "有序", "柔和")

        /** 三档对比度名称。 */
        val CONTRAST_LABELS: List<String> = listOf("低", "标准", "高")
    }
}
