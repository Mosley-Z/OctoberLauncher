package com.shuiyu.octoberrain.feature.reader.pdf

import com.shuiyu.octoberrain.core.common.ReaderLocator
import kotlin.math.min

/** 固定视口渲染，放大只改变矩阵，不申请整页放大位图。 */
internal object PdfViewport {
    const val MAX_BYTES = 12 * 1024 * 1024

    fun bytes(
        width: Int,
        height: Int,
    ): Int {
        require(width in 1..4096 && height in 1..4096)
        val bytes = width.toLong() * height * 4
        require(bytes <= MAX_BYTES)
        return bytes.toInt()
    }

    fun normalize(
        value: ReaderLocator.Pdf,
        pages: Int,
    ): ReaderLocator.Pdf =
        value.copy(
            page = value.page.coerceIn(0, (pages - 1).coerceAtLeast(0)),
            zoom = value.zoom.takeIf { it.isFinite() }?.coerceIn(1f, 4f) ?: 1f,
            centerX = value.centerX.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
            centerY = value.centerY.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
        )

    fun scale(
        pageWidth: Int,
        pageHeight: Int,
        width: Int,
        height: Int,
        zoom: Float,
        crop: Boolean,
    ): Float {
        val content = if (crop) 0.9f else 1f
        return min(width / (pageWidth * content), height / (pageHeight * content)) * zoom
    }
}

internal enum class PdfAdjustment { ZOOM_IN, ZOOM_OUT, LEFT, RIGHT, UP, DOWN, CROP, FIT }
