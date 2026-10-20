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
 * 网格浏览面板。
 *
 * 固定四列四行、每页 16 项；单元格按内容区宽度均分，缩略图高度取四分之三宽度，
 * 内容区不足以容纳四行时按 [FileMetrics.gridCellDrawH] 压缩缩略图。
 * 目录绘制文件夹图标，图片绘制居中裁剪的缩略图，其余文件绘制文件名与扩展名标签。
 *
 * 参数与 [BrowsePane] 一致。
 */
internal class GridPane(
    context: Context,
    tokens: FileTokens,
    prefs: FilePrefs,
    scope: CoroutineScope,
    callbacks: Callbacks,
) : BrowsePane(context, tokens, prefs, scope, callbacks) {
    private val smallPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.smallText
            color = InkColors.INK
        }
    private val badgePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.smallText * 0.72f
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
    private val paperPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.PAPER
        }
    private val highlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK_4
        }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** 网格每页固定 16 项。 */
    override val pageSize: Int get() = FileMetrics.GRID_PAGE

    override fun onDrawContent(
        canvas: Canvas,
        area: Rect,
        start: Int,
        end: Int,
    ) {
        if (drawNoticeIfEmpty(canvas, area)) return
        val metrics = geometry ?: return
        for (index in start until end) {
            val item = items.getOrNull(index) ?: continue
            drawCell(canvas, area, metrics, index - start, item)
        }
    }

    override fun indexAt(
        x: Int,
        y: Int,
        area: Rect,
    ): Int {
        val metrics = geometry ?: return -1
        var index = -1
        if (y >= area.top && y < area.top + metrics.gridDrawH) {
            for (slot in 0 until metrics.pageSize) {
                if (cellRect(area, metrics, slot).contains(x, y)) {
                    index = pagerState.firstIndex + slot
                    break
                }
            }
        }
        return index
    }

    override fun thumbEdge(metrics: FileMetrics): Int = minOf(metrics.cellW, metrics.gridCellDrawH)

    /** 绘制单个单元格：选中高亮、缩略图或文件夹图标、名称标签。 */
    private fun drawCell(
        canvas: Canvas,
        area: Rect,
        metrics: FileMetrics,
        slot: Int,
        item: FileItem,
    ) {
        val cell = cellRect(area, metrics, slot)
        if (item === pressedItemOrNull) canvas.drawRect(cell, highlightPaint)
        when {
            item.dir -> drawFolder(canvas, cell)
            item.isImage -> drawImage(canvas, cell, item)
            else -> drawFilePlaceholder(canvas, cell, item)
        }
        drawLabel(canvas, cell, item)
    }

    /** 绘制文件夹图标，图标取单元格短边的四成。 */
    private fun drawFolder(
        canvas: Canvas,
        cell: Rect,
    ) {
        val size = minOf(cell.width(), cell.height()) * 0.4f
        InkGlyphs.draw(
            canvas = canvas,
            kind = InkGlyphs.Kind.FOLDER,
            cx = cell.centerX().toFloat(),
            cy = cell.centerY().toFloat(),
            size = size,
            paint = iconPaint,
        )
    }

    /** 绘制图片缩略图，未就绪时绘制占位框，完成后绘制图片角标。 */
    private fun drawImage(
        canvas: Canvas,
        cell: Rect,
        item: FileItem,
    ) {
        val bitmap = bitmapOf(item)
        if (bitmap == null) {
            canvas.drawRect(cell, linePaint)
            PaneDraw.centered(canvas, BrowseStrings.IMAGE_LOADING, badgePaint, cell)
        } else {
            PaneDraw.cropped(canvas, bitmap, cell, bitmapPaint)
        }
        drawBadge(canvas, cell)
    }

    /** 绘制非图片文件占位框，框内显示大写扩展名。 */
    private fun drawFilePlaceholder(
        canvas: Canvas,
        cell: Rect,
        item: FileItem,
    ) {
        canvas.drawRect(cell, linePaint)
        val extension = item.extension().removePrefix(".")
        val label = if (extension.isEmpty()) BrowseStrings.OPEN else extension.uppercase()
        PaneDraw.centered(canvas, label, badgePaint, cell)
    }

    /** 在单元格右下角绘制图片角标。 */
    private fun drawBadge(
        canvas: Canvas,
        cell: Rect,
    ) {
        val width = tokens.dp(18f)
        val height = tokens.dp(14f)
        val badge = Rect(cell.right - width, cell.top, cell.right, cell.top + height)
        canvas.drawRect(badge, paperPaint)
        canvas.drawRect(badge, linePaint)
        PaneDraw.centered(canvas, BrowseStrings.IMAGE_BADGE, badgePaint, badge)
    }

    /** 在缩略图下方居中绘制名称，超出宽度时截断。 */
    private fun drawLabel(
        canvas: Canvas,
        cell: Rect,
        item: FileItem,
    ) {
        val labelRect = Rect(cell.left, cell.bottom, cell.right, cell.bottom + tokens.cellLabelH)
        val text = PaneDraw.fit(item.name, smallPaint, cell.width())
        PaneDraw.centered(canvas, text, smallPaint, labelRect)
    }

    /** 计算单元格缩略图区域，标签另占一行。 */
    private fun cellRect(
        area: Rect,
        metrics: FileMetrics,
        slot: Int,
    ): Rect {
        val row = slot / FileMetrics.GRID_COLS
        val column = slot % FileMetrics.GRID_COLS
        val left = area.left + tokens.padH + column * (metrics.cellW + tokens.gap)
        val top = area.top + row * (metrics.gridCellDrawH + tokens.cellLabelH + tokens.gap)
        return Rect(left, top, left + metrics.cellW, top + metrics.gridCellDrawH)
    }
}
