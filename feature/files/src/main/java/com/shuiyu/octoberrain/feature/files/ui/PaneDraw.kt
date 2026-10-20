package com.shuiyu.octoberrain.feature.files.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * 面板绘制公共操作。
 *
 * 网格与列表共享文本居中、文本截断、等比内嵌与居中裁剪四类绘制，集中实现以避免重复；
 * 所有函数只读取传入的画布与位图，不持有状态，也不访问文件系统。
 */
internal object PaneDraw {
    /** 在矩形内居中绘制单行文本。 */
    fun centered(
        canvas: Canvas,
        text: String,
        paint: Paint,
        rect: Rect,
    ) {
        val width = paint.measureText(text)
        val baseline = rect.centerY() + (paint.descent() - paint.ascent()) / 2f - paint.descent()
        canvas.drawText(text, rect.centerX() - width / 2f, baseline, paint)
    }

    /**
     * 按像素宽度截断文本并追加省略号。
     *
     * 参数 text 为原文；paint 提供字号；maxWidth 为可用宽度。宽度非正或原文为空时返回空串。
     */
    fun fit(
        text: String,
        paint: Paint,
        maxWidth: Int,
    ): String =
        when {
            maxWidth <= 0 || text.isEmpty() -> ""
            paint.measureText(text) <= maxWidth -> text
            else -> truncate(text, paint, maxWidth)
        }

    /** 逐字符回退直到可容纳省略号，返回截断结果。 */
    private fun truncate(
        text: String,
        paint: Paint,
        maxWidth: Int,
    ): String {
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) {
            end -= 1
        }
        return if (end <= 0) "…" else text.substring(0, end) + "…"
    }

    /** 在矩形内右对齐绘制单行文本。 */
    fun rightCentered(
        canvas: Canvas,
        text: String,
        paint: Paint,
        rect: Rect,
    ) {
        val width = paint.measureText(text)
        val baseline = rect.centerY() + (paint.descent() - paint.ascent()) / 2f - paint.descent()
        canvas.drawText(text, rect.right - width, baseline, paint)
    }

    /** 以给定水平起点与垂直中心绘制单行文本。 */
    fun lineCentered(
        canvas: Canvas,
        text: String,
        paint: Paint,
        left: Float,
        centerY: Float,
    ) {
        if (text.isEmpty()) return
        val baseline = centerY + (paint.descent() - paint.ascent()) / 2f - paint.descent()
        canvas.drawText(text, left, baseline, paint)
    }

    /** 按单元格宽高比居中裁剪后绘制，避免图片被拉伸。 */
    fun cropped(
        canvas: Canvas,
        bitmap: Bitmap,
        cell: Rect,
        paint: Paint,
    ) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return
        val cellRatio = cell.width().toFloat() / cell.height().toFloat()
        val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val source =
            if (imageRatio > cellRatio) {
                val cropWidth = (bitmap.height * cellRatio).toInt().coerceIn(1, bitmap.width)
                val left = (bitmap.width - cropWidth) / 2
                Rect(left, 0, left + cropWidth, bitmap.height)
            } else {
                val cropHeight = (bitmap.width / cellRatio).toInt().coerceIn(1, bitmap.height)
                val top = (bitmap.height - cropHeight) / 2
                Rect(0, top, bitmap.width, top + cropHeight)
            }
        canvas.drawBitmap(bitmap, source, cell, paint)
    }

    /** 按参考矩形等比缩放并居中，返回目标矩形。 */
    fun fitInside(
        box: Rect,
        bitmap: Bitmap,
    ): Rect {
        if (bitmap.width <= 0 || bitmap.height <= 0) return box
        val scale = minOf(box.width().toFloat() / bitmap.width, box.height().toFloat() / bitmap.height)
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val left = box.centerX() - width / 2
        val top = box.centerY() - height / 2
        return Rect(left, top, left + width, top + height)
    }
}
