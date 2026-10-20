package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.UsageEntry
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.render.InkCanvas

/**
 * 使用时长组件：列出今日使用最多的三个应用与时长。
 *
 * 每行右侧显示分钟数，行底绘制按最长时长归一的比例条；
 * 组件只做展示，点击不跳转，避免误触进入其他页面。
 */
class UsageWidget(
    context: Context,
) : InkWidgetView(context) {
    private var entries: List<UsageEntry> = emptyList()

    override suspend fun loadData(force: Boolean): Boolean {
        entries = if (AppServices.hasContent()) AppServices.content.usageToday() else emptyList()
        return true
    }

    override fun drawWidget(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        val area = RectF(padX, pad, width - padX, height - pad)
        if (area.width() <= 0f || area.height() <= 0f) return

        val data = entries.take(MAX_ROWS)
        if (data.isEmpty()) {
            drawEmptyState(canvas, area, "暂无使用记录")
            return
        }

        val headerPaint = textPaint(minOf(tokens.textBody, height * 0.14f), InkColors.INK)
        val captionPaint = textPaint(minOf(tokens.textCaption, height * 0.1f), InkColors.INK_2)
        var y = InkDraw.text(canvas, "今日使用", area.left, area.top, headerPaint)
        y += pad * 0.4f

        val rowHeight = (area.bottom - y) / data.size
        if (rowHeight <= 0f) return
        val maxMinutes = maxOf(1, data.first().minutes)
        val labelPaint = textPaint(minOf(tokens.textBody, rowHeight * 0.42f), InkColors.INK)

        data.forEachIndexed { index, entry ->
            val rowTop = y + rowHeight * index
            val minutesText = "${entry.minutes} 分钟"
            val valueWidth = captionPaint.measureText(minutesText)
            val label = InkDraw.ellipsize(entry.label, labelPaint, area.width() - valueWidth - pad)
            InkDraw.text(canvas, label, area.left, rowTop, labelPaint)
            InkDraw.textRight(
                canvas,
                minutesText,
                area.right,
                rowTop + (InkDraw.lineHeight(labelPaint) - InkDraw.lineHeight(captionPaint)) / 2f,
                captionPaint,
            )

            val barHeight = maxOf(1f, tokens.dpF(1.5f))
            val barTop = rowTop + rowHeight - pad * 0.4f - barHeight
            if (barTop > rowTop + InkDraw.lineHeight(labelPaint) + pad * 0.2f) {
                val barBottom = barTop + barHeight
                InkCanvas.drawRect(
                    canvas,
                    RectF(area.left, barTop, area.right, barBottom),
                    fillPaint(InkColors.INK_4),
                    filled = true,
                )
                val ratio = entry.minutes.toFloat() / maxMinutes
                if (ratio > 0f) {
                    InkCanvas.drawRect(
                        canvas,
                        RectF(area.left, barTop, area.left + area.width() * ratio, barBottom),
                        fillPaint(InkColors.INK),
                        filled = true,
                    )
                }
            }
        }
    }

    private companion object {
        /** 最多展示的应用行数。 */
        const val MAX_ROWS = 3
    }
}
