package com.shuiyu.octoberrain.feature.reader.office.sheets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.shuiyu.octoberrain.feature.reader.ReaderTable
import com.shuiyu.octoberrain.feature.reader.TableMerge

/** Native scrollable cell window; merged ranges retain absolute coordinates and never create workbook DOM. */
internal class SheetTableView(
    context: Context,
    table: ReaderTable,
) : HorizontalScrollView(context) {
    init {
        setBackgroundColor(Color.WHITE)
        isFillViewport = true
        val vertical = ScrollView(context)
        vertical.addView(SheetCanvas(context, table))
        addView(vertical, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
}

private class SheetCanvas(
    context: Context,
    private val table: ReaderTable,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val cellWidth = 144 * density
    private val rowHeight = 64 * density
    private val headerWidth = 56 * density
    private val headerHeight = 36 * density
    private val textPaint =
        android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        }
    private val linePaint =
        Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = density
        }
    private val highlightPaint = Paint().apply { color = Color.LTGRAY }
    private val visibleColumns = minOf(10, (table.totalColumns - table.column).coerceAtLeast(1))

    init {
        contentDescription =
            buildString {
                append(table.sheets.getOrElse(table.sheet) { "工作表" })
                table.cells.forEachIndexed { row, cells ->
                    cells.forEachIndexed { column, value ->
                        append("\n${table.row + row + 1}行 ${table.column + column + 1}列 $value")
                    }
                }
            }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(
            (headerWidth + visibleColumns * cellWidth).toInt(),
            (headerHeight + maxOf(1, table.cells.size) * rowHeight).toInt(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (column in 0 until visibleColumns) {
            val x = headerWidth + column * cellWidth
            cell(canvas, x, 0f, cellWidth, headerHeight, columnName(table.column + column))
        }
        table.cells.forEachIndexed { row, values ->
            val y = headerHeight + row * rowHeight
            cell(canvas, 0f, y, headerWidth, rowHeight, (table.row + row + 1).toString())
            for (column in 0 until visibleColumns) drawValue(canvas, row, column, values.getOrElse(column) { "" })
        }
    }

    private fun drawValue(
        canvas: Canvas,
        row: Int,
        column: Int,
        value: String,
    ) {
        val absoluteRow = table.row + row
        val absoluteColumn = table.column + column
        val merge =
            table.merges.firstOrNull {
                absoluteRow >= it.row &&
                    absoluteRow < it.row + it.rows &&
                    absoluteColumn >= it.column &&
                    absoluteColumn < it.column + it.columns
            }
        if (merge == null) {
            cell(
                canvas,
                headerWidth + column * cellWidth,
                headerHeight + row * rowHeight,
                cellWidth,
                rowHeight,
                value,
                table.highlight == (absoluteRow to absoluteColumn),
            )
        } else {
            drawMerge(canvas, row, column, merge, value)
        }
    }

    private fun drawMerge(
        canvas: Canvas,
        row: Int,
        column: Int,
        merge: TableMerge,
        value: String,
    ) {
        val firstRow = maxOf(merge.row, table.row) - table.row
        val firstColumn = maxOf(merge.column, table.column) - table.column
        if (row == firstRow && column == firstColumn) {
            val rows = minOf(merge.row + merge.rows - table.row, table.cells.size) - firstRow
            val columns = minOf(merge.column + merge.columns - table.column, visibleColumns) - firstColumn
            cell(
                canvas,
                headerWidth + column * cellWidth,
                headerHeight + row * rowHeight,
                cellWidth * columns,
                rowHeight * rows,
                value,
                mergedHighlight(merge),
            )
        }
    }

    private fun mergedHighlight(merge: TableMerge): Boolean {
        val hit = table.highlight
        return hit != null &&
            hit.first in merge.row until merge.row + merge.rows &&
            hit.second in merge.column until merge.column + merge.columns
    }

    private fun cell(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        value: String,
        highlighted: Boolean = false,
    ) {
        if (highlighted) canvas.drawRect(x, y, x + width, y + height, highlightPaint)
        linePaint.strokeWidth = if (highlighted) 3 * density else density
        canvas.drawRect(x, y, x + width, y + height, linePaint)
        val save = canvas.save()
        canvas.clipRect(x + 4 * density, y + density, x + width - 4 * density, y + height - density)
        val lines = value.replace('\r', ' ').split('\n', limit = 3)
        lines.take(2).forEachIndexed { index, line ->
            val text = TextUtils.ellipsize(line, textPaint, width - 12 * density, TextUtils.TruncateAt.END)
            canvas.drawText(text.toString(), x + 6 * density, y + (24 + index * 22) * density, textPaint)
        }
        canvas.restoreToCount(save)
    }

    private fun columnName(index: Int): String {
        var number = index + 1
        var name = ""
        while (number > 0) {
            number--
            name = ('A' + number % 26) + name
            number /= 26
        }
        return name
    }
}
