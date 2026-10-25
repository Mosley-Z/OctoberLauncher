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
 * 竖屏三列、横屏五列，行数按实测可用高度选择；单元格按内容区宽度均分，缩略图始终为列格内居中的正方形，
 * 可用内容区变化时按 [FileMetrics.gridCellDrawH] 压缩缩略图。
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
            textSize = tokens.gridLabelText
            color = InkColors.INK
        }
    private val placeholderPaint =
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
    private val highlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK_4
        }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Grid capacity follows the currently measured geometry. */
    override val pageSize: Int get() = geometry?.pageSize ?: 1

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
                if (metrics.gridCell(slot, area.left, area.top).hit.contains(x, y)) {
                    index = pagerState.firstIndex + slot
                    break
                }
            }
        }
        return index
    }

    override val includeEpubThumbnails: Boolean get() = true

    override fun thumbEdge(metrics: FileMetrics): Int = minOf(metrics.cellW, metrics.gridCellDrawH)

    /** 绘制单个单元格：选中高亮、缩略图或文件夹图标、名称标签。 */
    private fun drawCell(
        canvas: Canvas,
        area: Rect,
        metrics: FileMetrics,
        slot: Int,
        item: FileItem,
    ) {
        val geometry = metrics.gridCell(slot, area.left, area.top)
        val cell = geometry.thumbnail.toAndroidRect()
        if (item === pressedItemOrNull) canvas.drawRect(geometry.hit.toAndroidRect(), highlightPaint)
        when {
            item.dir -> drawFolder(canvas, cell)
            item.isImage || (item.isEpub && bitmapOf(item) != null) -> drawImage(canvas, cell, item)
            else -> drawFilePlaceholder(canvas, cell, item)
        }
        drawLabel(canvas, geometry.label.toAndroidRect(), item, cell.width())
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

    /** 绘制图片缩略图，未就绪时绘制占位框，不叠加图片角标。 */
    private fun drawImage(
        canvas: Canvas,
        cell: Rect,
        item: FileItem,
    ) {
        val bitmap = bitmapOf(item)
        if (bitmap == null) {
            canvas.drawRect(cell, linePaint)
            PaneDraw.centered(canvas, BrowseStrings.IMAGE_LOADING, placeholderPaint, cell)
        } else {
            PaneDraw.cropped(canvas, bitmap, cell, bitmapPaint)
        }
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
        PaneDraw.centered(canvas, label, placeholderPaint, cell)
    }

    /** 在缩略图下方居中绘制名称，超出宽度时截断。 */
    private fun drawLabel(
        canvas: Canvas,
        cell: Rect,
        item: FileItem,
        thumbnailWidth: Int,
    ) {
        val text = PaneDraw.gridLabel(item, smallPaint, thumbnailWidth)
        PaneDraw.centered(canvas, text, smallPaint, cell)
    }
}

internal fun FileGridRect.toAndroidRect(): Rect = Rect(left, top, right, bottom)
