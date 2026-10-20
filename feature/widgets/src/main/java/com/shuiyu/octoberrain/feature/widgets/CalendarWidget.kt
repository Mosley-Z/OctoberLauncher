package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.render.InkCanvas
import java.time.LocalDate

/**
 * 日历组件：绘制当月月历并高亮当天。
 *
 * 固定六行七列网格，跨月部分留空；网格线统一使用设计令牌的描边宽度，
 * 当天以实心圆角块反白显示。
 */
class CalendarWidget(
    context: Context,
) : InkWidgetView(context) {
    override fun drawWidget(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        val today = LocalDate.now()
        val left = padX
        val right = width - padX
        val top = pad
        val bottom = height - pad
        if (right <= left || bottom <= top) return

        val headerPaint = textPaint(minOf(tokens.textHeading, height * 0.16f), InkColors.INK)
        val captionPaint = textPaint(minOf(tokens.textCaption, height * 0.1f), InkColors.INK_2)
        val bodyPaint = textPaint(minOf(tokens.textBody, height * 0.14f), InkColors.INK)

        var y = InkDraw.text(canvas, "${today.year} 年 ${today.monthValue} 月", left, top, headerPaint)
        InkDraw.textRight(
            canvas,
            "${today.monthValue} 月 ${today.dayOfMonth} 日",
            right,
            top + (InkDraw.lineHeight(headerPaint) - InkDraw.lineHeight(captionPaint)) / 2f,
            captionPaint,
        )
        y += pad * 0.4f

        val columns = WEEK_LABELS.size
        val cellWidth = (right - left) / columns
        WEEK_LABELS.forEachIndexed { index, label ->
            InkDraw.textCenter(canvas, label, left + cellWidth * index + cellWidth / 2f, y, captionPaint)
        }
        y += InkDraw.lineHeight(captionPaint) + pad * 0.3f

        val gridHeight = bottom - y
        if (gridHeight <= 0f) return
        val rows = MAX_ROWS
        val rowHeight = gridHeight / rows
        val dayPaint = textPaint(minOf(bodyPaint.textSize, rowHeight * 0.62f), InkColors.INK)
        val invertedPaint = textPaint(dayPaint.textSize, InkColors.PAPER)

        // 网格线宽与其余组件统一取设计令牌的描边宽度
        val gridPaint = strokePaint(InkColors.INK_4)
        val gridBottom = y + rowHeight * rows
        for (column in 1 until columns) {
            val x = left + cellWidth * column
            canvas.drawLine(x, y, x, gridBottom, gridPaint)
        }
        for (row in 1 until rows) {
            val lineY = y + rowHeight * row
            canvas.drawLine(left, lineY, right, lineY, gridPaint)
        }

        // 当月一日在周一阵列中的偏移，周一为第 0 列
        val offset = today.withDayOfMonth(1).dayOfWeek.value - 1
        val days = today.lengthOfMonth()
        for (index in 0 until rows * columns) {
            val day = index - offset + 1
            if (day < 1 || day > days) continue
            val centerX = left + cellWidth * (index % columns) + cellWidth / 2f
            val rowTop = y + rowHeight * (index / columns)
            if (day == today.dayOfMonth) {
                val radius = minOf(cellWidth, rowHeight) * 0.42f
                val centerY = rowTop + rowHeight / 2f
                val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
                InkCanvas.drawRoundRect(
                    canvas,
                    rect,
                    tokens.radiusSmall.toFloat(),
                    fillPaint(InkColors.INK),
                    filled = true,
                )
                InkDraw.textCenterBaseline(canvas, day.toString(), centerX, centerY, invertedPaint)
            } else {
                InkDraw.textCenter(
                    canvas,
                    day.toString(),
                    centerX,
                    rowTop + (rowHeight - InkDraw.lineHeight(dayPaint)) / 2f,
                    dayPaint,
                )
            }
        }
    }

    private companion object {
        /** 星期表头，周一开头。 */
        val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

        /** 月历固定行数，保证跨月时网格高度一致。 */
        const val MAX_ROWS = 6
    }
}
