package com.shuiyu.octoberrain.feature.files.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.feature.files.data.FileItem

/**
 * 选择页网格排布。
 *
 * 排布参数沿用文件页：四列四行、每页 16 项，单元格宽度按左右留白、列数与间距均分，
 * 缩略图高度优先取宽度的四分之三，内容区不足四行时改用 [FileMetrics.gridCellDrawH]。
 * 本类只负责几何换算与绘制，缩略图由调用方经 lambda 提供，未就绪时绘制通用图片图标。
 * 目录绘制文件夹图标，图片绘制居中裁剪的缩略图，其余文件绘制大写扩展名。
 *
 * 参数 tokens 为文件页尺寸令牌。
 */
internal class PickGridPane(
    private val tokens: FileTokens,
) {
    private val namePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.smallText
            color = InkColors.INK
        }
    private val placeholderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.smallText * 0.72f
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
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val iconPath = Path()

    /**
     * 绘制当前页网格。
     *
     * 参数 canvas 为画布；metrics 为本次布局几何；items 为目录全部条目；
     * start 与 end 为页首下标与页末下标（不含）；
     * bitmapOf 按条目取已就绪的缩略图，返回 null 表示尚未就绪。
     */
    fun draw(
        canvas: Canvas,
        metrics: FileMetrics,
        items: List<FileItem>,
        start: Int,
        end: Int,
        bitmapOf: (FileItem) -> Bitmap?,
    ) {
        for (index in start until end) {
            val item = items.getOrNull(index) ?: continue
            val cell = cellRect(metrics, index - start)
            drawCell(canvas, cell, item, if (item.isImage) bitmapOf(item) else null)
        }
    }

    /**
     * 反查坐标命中的条目下标。
     *
     * 参数 x 与 y 为视图坐标；metrics 为本次布局几何；start 为页首下标。
     * 坐标落在内容区之外或单元格间隔时返回 -1。
     */
    fun indexAt(
        x: Int,
        y: Int,
        metrics: FileMetrics,
        start: Int,
    ): Int {
        val slot =
            if (y < metrics.contentTop || y >= metrics.contentTop + metrics.gridDrawH) {
                null
            } else {
                (0 until FileMetrics.GRID_PAGE).firstOrNull { cellRect(metrics, it).contains(x, y) }
            }
        return slot?.let { start + it } ?: -1
    }

    /** 绘制单元格：图标或缩略图、扩展名占位，名称在缩略图下方居中显示。 */
    private fun drawCell(
        canvas: Canvas,
        cell: Rect,
        item: FileItem,
        bitmap: Bitmap?,
    ) {
        when {
            item.dir -> drawFolder(canvas, cell)
            bitmap != null -> PaneDraw.cropped(canvas, bitmap, cell, bitmapPaint)
            item.isImage -> {
                canvas.drawRect(cell, framePaint)
                drawPictureGlyph(canvas, cell)
            }
            else -> {
                canvas.drawRect(cell, framePaint)
                PaneDraw.centered(canvas, extensionLabel(item), placeholderPaint, cell)
            }
        }
        val labelRect = Rect(cell.left, cell.bottom, cell.right, cell.bottom + tokens.cellLabelH)
        PaneDraw.centered(canvas, PaneDraw.fit(item.name, namePaint, cell.width()), namePaint, labelRect)
    }

    /** 绘制文件夹图标，图标取单元格短边的四成。 */
    private fun drawFolder(
        canvas: Canvas,
        cell: Rect,
    ) {
        InkGlyphs.draw(
            canvas = canvas,
            kind = InkGlyphs.Kind.FOLDER,
            cx = cell.centerX().toFloat(),
            cy = cell.centerY().toFloat(),
            size = minOf(cell.width(), cell.height()) * 0.4f,
            paint = glyphPaint,
        )
    }

    /** 绘制通用图片图标，由描边方框与框内山形折线组成。 */
    private fun drawPictureGlyph(
        canvas: Canvas,
        cell: Rect,
    ) {
        val inset = minOf(cell.width(), cell.height()) * 0.26f
        val frame =
            Rect(
                cell.left + inset.toInt(),
                cell.top + inset.toInt(),
                cell.right - inset.toInt(),
                cell.bottom - inset.toInt(),
            )
        if (frame.width() <= 0 || frame.height() <= 0) return
        canvas.drawRect(frame, glyphPaint)
        val left = frame.left.toFloat()
        val width = frame.width().toFloat()
        val bottom = frame.bottom.toFloat()
        val height = frame.height().toFloat()
        iconPath.reset()
        iconPath.moveTo(left + width * 0.16f, bottom - height * 0.18f)
        iconPath.lineTo(left + width * 0.4f, bottom - height * 0.52f)
        iconPath.lineTo(left + width * 0.58f, bottom - height * 0.3f)
        iconPath.lineTo(left + width * 0.74f, bottom - height * 0.5f)
        iconPath.lineTo(left + width * 0.88f, bottom - height * 0.18f)
        canvas.drawPath(iconPath, glyphPaint)
    }

    /** 取占位文案，无扩展名时显示打开提示。 */
    private fun extensionLabel(item: FileItem): String {
        val extension = item.extension().removePrefix(".")
        return if (extension.isEmpty()) BrowseStrings.OPEN else extension.uppercase()
    }

    /** 计算单元格缩略图区域，标签另占一行；内容区左边界为 0。 */
    private fun cellRect(
        metrics: FileMetrics,
        slot: Int,
    ): Rect {
        val row = slot / FileMetrics.GRID_COLS
        val column = slot % FileMetrics.GRID_COLS
        val left = tokens.padH + column * (metrics.cellW + tokens.gap)
        val top = metrics.contentTop + row * (metrics.gridCellDrawH + tokens.cellLabelH + tokens.gap)
        return Rect(left, top, left + metrics.cellW, top + metrics.gridCellDrawH)
    }
}
