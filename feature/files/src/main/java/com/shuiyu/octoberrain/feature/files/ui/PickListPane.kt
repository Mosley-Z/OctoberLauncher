package com.shuiyu.octoberrain.feature.files.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.feature.files.data.FileItem

/**
 * 选择页列表排布。
 *
 * 每行左置边长 30dp 的缩略图或图标，右侧绘制名称与字节数，行间绘制浅色分隔线；
 * 行高与文字规格沿用文件页列表，目录名称追加斜线以便与文件区分。
 * 本类只负责几何换算与绘制，缩略图由调用方经 lambda 提供，未就绪时绘制描边占位框。
 *
 * 参数 tokens 为文件页尺寸令牌。
 */
internal class PickListPane(
    private val tokens: FileTokens,
) {
    private val bodyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.bodyText
            color = InkColors.INK
        }
    private val smallPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.smallText
            color = InkColors.INK
        }
    private val glyphPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
            color = InkColors.INK
        }
    private val framePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
            color = InkColors.INK_4
        }
    private val dividerPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
            color = InkColors.INK_4
        }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * 绘制当前页列表。
     *
     * 参数 canvas 为画布；area 为内容区；items 为目录全部条目；
     * start 与 end 为页首下标与页末下标（不含）；
     * bitmapOf 按条目取已就绪的缩略图，返回 null 表示尚未就绪。
     */
    fun draw(
        canvas: Canvas,
        area: Rect,
        items: List<FileItem>,
        start: Int,
        end: Int,
        bitmapOf: (FileItem) -> Bitmap?,
    ) {
        for (index in start until end) {
            val item = items.getOrNull(index) ?: continue
            drawRow(canvas, area, index - start, item, if (item.isImage) bitmapOf(item) else null)
        }
    }

    /**
     * 反查坐标命中的条目下标。
     *
     * 参数 x 与 y 为视图坐标；metrics 为本次布局几何；start 为页首下标。
     * 坐标落在内容区之外时返回 -1，行号超出条目数量时由调用方按空位处理。
     */
    fun indexAt(
        x: Int,
        y: Int,
        metrics: FileMetrics,
        start: Int,
    ): Int =
        when {
            x < 0 || x >= metrics.widthPx -> -1
            y < metrics.contentTop || y >= metrics.contentBottom -> -1
            else -> start + (y - metrics.contentTop) / tokens.rowH
        }

    /** 绘制单行：行首图标或缩略图、名称、字节数与分隔线。 */
    private fun drawRow(
        canvas: Canvas,
        area: Rect,
        row: Int,
        item: FileItem,
        bitmap: Bitmap?,
    ) {
        val top = area.top + row * tokens.rowH
        val thumbTop = top + (tokens.rowH - tokens.rowThumb) / 2
        val thumb =
            Rect(
                area.left + tokens.padH,
                thumbTop,
                area.left + tokens.padH + tokens.rowThumb,
                thumbTop + tokens.rowThumb,
            )
        when {
            item.dir ->
                InkGlyphs.draw(
                    canvas = canvas,
                    kind = InkGlyphs.Kind.FOLDER,
                    cx = thumb.centerX().toFloat(),
                    cy = thumb.centerY().toFloat(),
                    size = minOf(thumb.width(), thumb.height()).toFloat(),
                    paint = glyphPaint,
                )
            bitmap != null -> canvas.drawBitmap(bitmap, null, PaneDraw.fitInside(thumb, bitmap), bitmapPaint)
            else -> canvas.drawRect(thumb, framePaint)
        }
        drawTexts(canvas, area, top, thumb, item)
        if (top + tokens.rowH < area.bottom) {
            val lineY = (top + tokens.rowH).toFloat()
            canvas.drawLine(
                (area.left + tokens.padH).toFloat(),
                lineY,
                (area.right - tokens.padH).toFloat(),
                lineY,
                dividerPaint,
            )
        }
    }

    /** 绘制行内名称与右侧字节数，名称超出可用宽度时截断。 */
    private fun drawTexts(
        canvas: Canvas,
        area: Rect,
        top: Int,
        thumb: Rect,
        item: FileItem,
    ) {
        val meta = if (item.dir) "" else FileText.size(item.size)
        val metaWidth = if (meta.isEmpty()) 0f else smallPaint.measureText(meta)
        val nameLeft = thumb.right + tokens.gapLarge
        val nameWidth = (area.right - tokens.padH - metaWidth - tokens.gap - nameLeft).toInt()
        val text = if (item.dir) "${item.name}/" else item.name
        val baseline = top + tokens.rowH / 2f - (bodyPaint.descent() + bodyPaint.ascent()) / 2f
        canvas.drawText(PaneDraw.fit(text, bodyPaint, nameWidth), nameLeft.toFloat(), baseline, bodyPaint)
        if (meta.isNotEmpty()) {
            val metaBaseline = top + tokens.rowH / 2f - (smallPaint.descent() + smallPaint.ascent()) / 2f
            canvas.drawText(meta, area.right - tokens.padH - metaWidth, metaBaseline, smallPaint)
        }
    }
}
