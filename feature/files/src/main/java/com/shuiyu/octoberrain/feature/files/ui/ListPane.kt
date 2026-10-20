package com.shuiyu.octoberrain.feature.files.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.feature.files.data.FileItem
import com.shuiyu.octoberrain.feature.files.data.FilePrefs
import kotlinx.coroutines.CoroutineScope

/**
 * 列表浏览面板。
 *
 * 每行左侧绘制缩略图或文件夹图标，右侧绘制名称与大小、时间，行间以细线分隔。
 * 每页行数由 [FileMetrics.listRows] 按内容区高度计算，三档设备分别得到 10 行与 16 行。
 *
 * 参数与 [BrowsePane] 一致。
 */
internal class ListPane(
    context: Context,
    tokens: FileTokens,
    prefs: FilePrefs,
    scope: CoroutineScope,
    callbacks: Callbacks,
) : BrowsePane(context, tokens, prefs, scope, callbacks) {
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
    private val badgePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.smallText * 0.7f
            color = InkColors.INK
        }
    private val iconPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
            color = InkColors.INK
        }
    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
            color = InkColors.INK
        }
    private val highlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK_4
        }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** 列表每页行数按内容区高度计算。 */
    override val pageSize: Int
        get() = geometry?.listRows ?: DEFAULT_ROWS

    override fun onDrawContent(
        canvas: Canvas,
        area: Rect,
        start: Int,
        end: Int,
    ) {
        if (drawNoticeIfEmpty(canvas, area)) return
        for (index in start until end) {
            val item = items.getOrNull(index) ?: continue
            drawRow(canvas, area, index - start, item)
        }
    }

    override fun indexAt(
        x: Int,
        y: Int,
        area: Rect,
    ): Int {
        if (y < area.top || y >= area.top + tokens.rowH * pageSize) return -1
        val row = (y - area.top) / tokens.rowH
        val index = pagerState.firstIndex + row
        return if (index in items.indices) index else -1
    }

    override fun thumbEdge(metrics: FileMetrics): Int = tokens.rowThumb

    /** 绘制单行：选中底色、缩略图或图标、名称、大小与时间、行分隔线。 */
    private fun drawRow(
        canvas: Canvas,
        area: Rect,
        row: Int,
        item: FileItem,
    ) {
        val top = area.top + row * tokens.rowH
        val rowRect = Rect(area.left, top, area.right, top + tokens.rowH)
        if (item === pressedItemOrNull) canvas.drawRect(rowRect, highlightPaint)
        val thumbSize = tokens.rowThumb
        val thumbTop = top + (tokens.rowH - thumbSize) / 2
        val thumb = Rect(area.left + tokens.padH, thumbTop, area.left + tokens.padH + thumbSize, thumbTop + thumbSize)
        when {
            item.dir -> drawFolder(canvas, thumb)
            item.isImage -> drawThumbnail(canvas, thumb, item)
            else -> drawFilePlaceholder(canvas, thumb, item)
        }
        drawTexts(canvas, area, rowRect, thumb, item)
        canvas.drawLine(
            (area.left + tokens.padH).toFloat(),
            (top + tokens.rowH).toFloat(),
            (area.right - tokens.padH).toFloat(),
            (top + tokens.rowH).toFloat(),
            linePaint,
        )
    }

    /** 绘制文件夹图标。 */
    private fun drawFolder(
        canvas: Canvas,
        thumb: Rect,
    ) {
        InkGlyphs.draw(
            canvas = canvas,
            kind = InkGlyphs.Kind.FOLDER,
            cx = thumb.centerX().toFloat(),
            cy = thumb.centerY().toFloat(),
            size = minOf(thumb.width(), thumb.height()) * 0.9f,
            paint = iconPaint,
        )
    }

    /** 绘制图片缩略图，未就绪时绘制占位框。 */
    private fun drawThumbnail(
        canvas: Canvas,
        thumb: Rect,
        item: FileItem,
    ) {
        val bitmap = bitmapOf(item)
        if (bitmap == null) {
            canvas.drawRect(thumb, linePaint)
        } else {
            canvas.drawBitmap(bitmap, null, PaneDraw.fitInside(thumb, bitmap), bitmapPaint)
        }
    }

    /** 绘制非图片文件占位框，框内显示大写扩展名。 */
    private fun drawFilePlaceholder(
        canvas: Canvas,
        thumb: Rect,
        item: FileItem,
    ) {
        canvas.drawRect(thumb, linePaint)
        val extension = item.extension().removePrefix(".")
        val label = if (extension.isEmpty()) BrowseStrings.OPEN else extension.uppercase()
        PaneDraw.centered(canvas, label, badgePaint, thumb)
    }

    /** 绘制名称与右侧的大小时间，宽度不足时截断名称。 */
    private fun drawTexts(
        canvas: Canvas,
        area: Rect,
        rowRect: Rect,
        thumb: Rect,
        item: FileItem,
    ) {
        val meta = FileText.meta(item)
        val metaWidth = smallPaint.measureText(meta)
        val metaLeft = area.right - tokens.padH - metaWidth
        val nameLeft = thumb.right + tokens.gapLarge
        val nameWidth = (metaLeft - tokens.gap - nameLeft).toInt()
        val name = PaneDraw.fit(item.name, bodyPaint, nameWidth)
        val baseline = rowRect.centerY() + (bodyPaint.descent() - bodyPaint.ascent()) / 2f - bodyPaint.descent()
        canvas.drawText(name, nameLeft.toFloat(), baseline, bodyPaint)
        val metaBaseline = rowRect.centerY() + (smallPaint.descent() - smallPaint.ascent()) / 2f - smallPaint.descent()
        canvas.drawText(meta, metaLeft, metaBaseline, smallPaint)
    }

    private companion object {
        /** 尚未完成布局时的兜底行数。 */
        const val DEFAULT_ROWS = 10
    }
}
