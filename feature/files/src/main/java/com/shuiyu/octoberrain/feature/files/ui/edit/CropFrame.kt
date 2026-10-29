package com.shuiyu.octoberrain.feature.files.ui.edit

import kotlin.math.abs
import kotlin.math.max

/** 以像素为单位的矩形，左闭右开。 */
data class IntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    /** 宽度，至少为 0。 */
    val width: Int get() = (right - left).coerceAtLeast(0)

    /** 高度，至少为 0。 */
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    /** 面积是否为正。 */
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    /** 判断点是否落在矩形内。 */
    fun contains(
        x: Int,
        y: Int,
    ): Boolean = x in left until right && y in top until bottom
}

/** 裁剪框命中目标，顺序即命中优先级。 */
enum class CropTarget {
    /** 左上角手柄。 */
    TOP_LEFT,

    /** 右上角手柄。 */
    TOP_RIGHT,

    /** 左下角手柄。 */
    BOTTOM_LEFT,

    /** 右下角手柄。 */
    BOTTOM_RIGHT,

    /** 框内区域。 */
    INSIDE,

    /** 框外区域。 */
    OUTSIDE,
}

/**
 * 裁剪框命中判定。
 *
 * 输入为像素坐标与裁剪框像素矩形。四角手柄的判定先于框内，避免靠近角落时选中整体拖动；
 * 手柄判定范围以框角为中心、边长为两倍手柄半径的正方形。
 */
object CropHitTest {
    /**
     * 判定触点对应的裁剪目标。
     *
     * 参数 x 与 y 为触点坐标；frame 为裁剪框像素矩形；handlePx 为手柄半径，非正时按 1 处理。
     */
    fun hit(
        x: Int,
        y: Int,
        frame: IntRect,
        handlePx: Int,
    ): CropTarget {
        val radius = handlePx.coerceAtLeast(1)
        return when {
            near(x, y, frame.left, frame.top, radius) -> CropTarget.TOP_LEFT
            near(x, y, frame.right, frame.top, radius) -> CropTarget.TOP_RIGHT
            near(x, y, frame.left, frame.bottom, radius) -> CropTarget.BOTTOM_LEFT
            near(x, y, frame.right, frame.bottom, radius) -> CropTarget.BOTTOM_RIGHT
            frame.contains(x, y) -> CropTarget.INSIDE
            else -> CropTarget.OUTSIDE
        }
    }

    /** 判断点是否落在以手柄位置为中心的正方形内。 */
    private fun near(
        x: Int,
        y: Int,
        handleX: Int,
        handleY: Int,
        radius: Int,
    ): Boolean = abs(x - handleX) <= radius && abs(y - handleY) <= radius
}

/**
 * 裁剪框拖动计算。
 *
 * 所有坐标均为归一化值，输入框先经 [NormRect.clamp] 处理。整体拖动保持宽高并夹取到边界；
 * 手柄拖动固定对角，按纵横比与最小边长约束调整矩形。
 */
object CropDrag {
    /**
     * 整体平移裁剪框。
     *
     * 参数 rect 为当前框；dx 与 dy 为归一化位移；返回平移后夹取到图片范围内的新框。
     */
    fun move(
        rect: NormRect,
        dx: Float,
        dy: Float,
    ): NormRect {
        val safe = rect.clamp()
        val width = safe.width
        val height = safe.height
        val left = (safe.left + dx).coerceIn(0f, 1f - width)
        val top = (safe.top + dy).coerceIn(0f, 1f - height)
        return NormRect(left, top, left + width, top + height)
    }

    /**
     * 拖动手柄调整裁剪框。
     *
     * 参数 rect 为当前框；target 为手柄方向；dx 与 dy 为归一化位移；
     * aspectRatio 为宽高比，null 表示自由；minSize 为归一化最小边长。
     * 返回固定对角、满足最小尺寸与纵横比约束的新框；目标不是四角时原样返回夹取结果。
     */
    fun resize(
        rect: NormRect,
        target: CropTarget,
        dx: Float,
        dy: Float,
        aspectRatio: Float?,
        minSize: Float,
    ): NormRect {
        val safe = rect.clamp()
        if (target == CropTarget.INSIDE || target == CropTarget.OUTSIDE) return safe
        val anchorX = if (target == CropTarget.TOP_LEFT || target == CropTarget.BOTTOM_LEFT) safe.right else safe.left
        val anchorY = if (target == CropTarget.TOP_LEFT || target == CropTarget.TOP_RIGHT) safe.bottom else safe.top
        val cornerX =
            (if (target == CropTarget.TOP_LEFT || target == CropTarget.BOTTOM_LEFT) safe.left else safe.right) + dx
        val cornerY =
            (if (target == CropTarget.TOP_LEFT || target == CropTarget.TOP_RIGHT) safe.top else safe.bottom) + dy
        return buildRect(anchorX, anchorY, cornerX, cornerY, aspectRatio, minSize)
    }

    /** 由固定对角与拖动角构造矩形，按需修正纵横比并夹取到图片范围。 */
    private fun buildRect(
        anchorX: Float,
        anchorY: Float,
        cornerX: Float,
        cornerY: Float,
        aspectRatio: Float?,
        minSize: Float,
    ): NormRect {
        val minEdge = minSize.coerceIn(0.001f, 0.9f)
        val rawWidth = abs(cornerX - anchorX).coerceAtLeast(minEdge)
        val rawHeight = abs(cornerY - anchorY).coerceAtLeast(minEdge)
        val width = if (aspectRatio == null) rawWidth else rawHeight * aspectRatio
        val height = if (aspectRatio == null) rawHeight else width / aspectRatio
        val directionWidth = if (cornerX < anchorX) anchorX else 1f - anchorX
        val directionHeight = if (cornerY < anchorY) anchorY else 1f - anchorY
        val constrainedHeight =
            if (aspectRatio != null) {
                val minimum = max(minEdge, minEdge / aspectRatio)
                val maximum = minOf(directionHeight, directionWidth / aspectRatio)
                height.coerceIn(minOf(minimum, maximum), maximum)
            } else {
                null
            }
        val sizedWidth = constrainedHeight?.let { it * aspectRatio!! } ?: max(width, minEdge)
        val sizedHeight = constrainedHeight ?: max(height, minEdge)
        val directionX = if (cornerX < anchorX) -1f else 1f
        val directionY = if (cornerY < anchorY) -1f else 1f
        val left = if (directionX > 0) anchorX else anchorX - sizedWidth
        val top = if (directionY > 0) anchorY else anchorY - sizedHeight
        return NormRect(left, top, left + sizedWidth, top + sizedHeight).clamp()
    }
}

/**
 * 归一化矩形映射到像素区域。
 *
 * 参数 rect 为归一化框；area 为图片在屏幕上的绘制区域。返回四舍五入后的像素矩形，
 * 宽高至少为 1，避免绘制时出现空矩形。
 */
fun NormRect.toIntRect(area: IntRect): IntRect {
    val left = (area.left + left * area.width).toInt().coerceIn(area.left, area.right - 1)
    val top = (area.top + top * area.height).toInt().coerceIn(area.top, area.bottom - 1)
    val right = (area.left + right * area.width).toInt().coerceIn(left + 1, area.right)
    val bottom = (area.top + bottom * area.height).toInt().coerceIn(top + 1, area.bottom)
    return IntRect(left, top, right, bottom)
}

/** 把像素矩形换算为归一化矩形，供拖动开始前记录状态。 */
fun IntRect.toNormRect(area: IntRect): NormRect {
    if (area.isEmpty) return NormRect.FULL
    val safeLeft = left.coerceIn(area.left, area.right)
    val safeTop = top.coerceIn(area.top, area.bottom)
    val safeRight = right.coerceIn(safeLeft, area.right)
    val safeBottom = bottom.coerceIn(safeTop, area.bottom)
    return NormRect(
        (safeLeft - area.left).toFloat() / area.width,
        (safeTop - area.top).toFloat() / area.height,
        (safeRight - area.left).toFloat() / area.width,
        (safeBottom - area.top).toFloat() / area.height,
    ).clamp()
}
