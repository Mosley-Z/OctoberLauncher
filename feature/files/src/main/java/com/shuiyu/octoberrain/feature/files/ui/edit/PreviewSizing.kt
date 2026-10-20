package com.shuiyu.octoberrain.feature.files.ui.edit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 预览尺寸计算。
 *
 * 编辑页只在缩小后的像素数组上重复计算滤镜，避免整图滤波造成卡顿。解码阶段按内容区
 * 长边取目标像素，滤镜预览再限制到 [MAX_PREVIEW_EDGE]。
 */
object PreviewSizing {
    /** 滤镜预览使用的最大长边像素，兼顾清晰度与计算量。 */
    const val MAX_PREVIEW_EDGE = 360

    /** 解码目标的最小长边，内容区异常过小时保持可辨识。 */
    private const val MIN_DECODE_EDGE = 32

    /**
     * 解码阶段的目标长边。
     *
     * 参数 contentWidth 与 contentHeight 为预览内容区尺寸；返回内容区较长边，
     * 结果不小于 32、不大于 [MAX_PREVIEW_EDGE] 的三倍。
     */
    fun decodeEdge(
        contentWidth: Int,
        contentHeight: Int,
    ): Int {
        val longEdge = max(contentWidth, contentHeight)
        return longEdge.coerceIn(MIN_DECODE_EDGE, MAX_PREVIEW_EDGE * 3)
    }

    /**
     * 滤镜预览的缩放比例。
     *
     * 参数 width 与 height 为解码后位图尺寸；返回不超过 1 的缩放系数，
     * 使长边不超过 [MAX_PREVIEW_EDGE]。
     */
    fun previewScale(
        width: Int,
        height: Int,
    ): Float {
        if (width <= 0 || height <= 0) return 1f
        val longEdge = max(width, height)
        return min(1f, MAX_PREVIEW_EDGE.toFloat() / longEdge)
    }

    /**
     * 滤镜预览的位图尺寸。
     *
     * 参数 width 与 height 为解码后位图尺寸；返回宽高至少为 1 的预览尺寸，
     * 长边不超过 [MAX_PREVIEW_EDGE]。
     */
    fun previewSize(
        width: Int,
        height: Int,
    ): IntArray {
        if (width <= 0 || height <= 0) return intArrayOf(1, 1)
        val scale = previewScale(width, height)
        return intArrayOf(
            max(1, (width * scale).roundToInt()),
            max(1, (height * scale).roundToInt()),
        )
    }
}
