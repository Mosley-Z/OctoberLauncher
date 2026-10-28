package com.shuiyu.octoberrain.feature.widgets.kernel

import android.graphics.Color
import android.graphics.Typeface
import com.inkread.weekread.kernel.CardState
import com.inkread.weekread.kernel.PeriodCalendar
import com.inkread.weekread.kernel.PeriodSnapshot
import com.inkread.weekread.kernel.render.CardLayout
import com.inkread.weekread.kernel.render.HostPeriodChart

/** Index C: date, rule, italic total, full-width chart, then summary. */
internal object LandscapePeriodRenderer {
    private val italic = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    private val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")

    fun draw(
        originalInk: LandscapeInk,
        original: LandscapeCardGeometry,
        state: CardState,
        monthly: Boolean,
    ) {
        val stats = state.stats() ?: return
        val count = stats.dayCount.takeIf { it in 1..31 } ?: 31
        val rows = (PeriodCalendar.firstWeekdayIndex(stats.baseTime) + count + 6) / 7
        val comparison = stats.compare != null && !stats.compare.isNaN()
        val achievement = !state.achievementText().isNullOrBlank() && stats.isCurrentPeriod()
        val lines = 1 + (if (comparison) 1 else 0) + (if (achievement) 1 else 0)
        val layout = LandscapePeriodGeometry.compute(original, monthly, rows, lines)
        val ink = originalInk
        val g = original
        val saved = ink.canvas.save()
        // Unsupported tiny slots must not paint over the header or outside the card.
        ink.canvas.clipRect(g.body.left, layout.ruleY, g.body.right, layout.summaryBottom)
        ink.rule(g.body.left, layout.ruleY, g.body.right, layout.ruleY, Color.BLACK, 2f)
        if (layout.totalVisible) {
            total(ink, stats.totalSec, g.body.left, layout.totalTop, if (monthly) 27f else 42f, g.body.width, monthly)
        }
        if (layout.chartVisible) {
            if (monthly) calendar(ink, g, state, stats, layout) else week(ink, g, stats, layout)
        }
        summary(ink, g, state, stats, layout.summaryTop, layout.summaryLineStep)
        ink.canvas.restoreToCount(saved)
        state.setMonthScrollMax(0f)
    }

    fun meta(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        state: CardState,
        monthly: Boolean,
    ) {
        val stats = state.stats() ?: return
        val date = if (monthly) PeriodCalendar.monthLabel(stats.baseTime) else PeriodCalendar.weekLabel(stats.baseTime)
        val days = "${stats.readDays}天"
        val daysWidth = if (monthly) 50f * g.unit else 0f
        val width = g.meta.width - daysWidth
        ink.paint.typeface = Typeface.DEFAULT
        ink.paint.textSize = 15f * g.unit
        val measured = ink.paint.measureText(date)
        // Full date takes priority; the same day count remains in the footer if the row is unusually narrow.
        val showDays = monthly && measured <= width
        val dateWidth = if (showDays) width else g.meta.width
        val font = minOf(15f, 15f * dateWidth / measured.coerceAtLeast(1f))
        ink.text(date, g.meta.left, g.meta.top + 10f * g.unit, font, dateWidth, color = Color.DKGRAY)
        if (showDays) right(ink, days, g.meta.right, g.meta.top + 10f * g.unit, 15f)
    }

    private fun total(
        ink: LandscapeInk,
        seconds: Int,
        left: Float,
        top: Float,
        size: Float,
        width: Float,
        monthly: Boolean,
    ) {
        var x = left
        val label =
            if (monthly) {
                String.format(java.util.Locale.ROOT, "%.1f小时", seconds / 3600.0)
            } else {
                CardLayout.fmtTotal(seconds)
            }
        Regex("\\d+(?:\\.\\d+)?|\\D+").findAll(label).forEach { match ->
            val numeric = match.value.firstOrNull()?.isDigit() == true
            val fontSize = if (numeric) size else 15f
            ink.text(
                match.value,
                x,
                top + (size - fontSize) * ink.unit,
                fontSize,
                (left + width - x).coerceAtLeast(0f),
                if (numeric) italic else Typeface.DEFAULT,
            )
            x += ink.paint.measureText(match.value)
        }
    }

    private fun week(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        stats: PeriodSnapshot,
        layout: LandscapePeriodLayout,
    ) {
        val u = g.unit
        val today = if (stats.isCurrentPeriod()) stats.todayIndex() else -1
        val days = stats.daySec ?: IntArray(0)
        val maxSec = maxOf(3600, (0..6).filter { today < 0 || it <= today }.maxOf { days.getOrElse(it) { 0 } })
        val chartHeight = layout.weekHeight
        val bottom = layout.chartTop + chartHeight
        val gap = 14f * u
        val barWidth = ((g.body.width - 6f * gap) / 7f).coerceAtLeast(1f)
        for (day in 0..6) {
            val x = g.body.left + day * (barWidth + gap)
            val future = today >= 0 && day > today
            val seconds = days.getOrElse(day) { 0 }.coerceAtLeast(0)
            if (!future && seconds > 0) {
                val height = minOf(chartHeight, maxOf(2f * u, chartHeight * seconds / maxSec))
                ink.box(
                    LandscapeRect(x, bottom - height, x + barWidth, bottom),
                    if (day == today) Color.BLACK else Color.LTGRAY,
                )
            }
            ink.centered(
                weekdays[day],
                LandscapeRect(x, bottom + 4f * u, x + barWidth, bottom + 24f * u),
                13f,
                Color.BLACK,
            )
        }
        ink.rule(g.body.left, bottom, g.body.right, bottom, Color.GRAY)
    }

    private fun calendar(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        state: CardState,
        stats: PeriodSnapshot,
        layout: LandscapePeriodLayout,
    ) {
        val u = g.unit
        val count = stats.dayCount.takeIf { it in 1..31 } ?: 31
        val offset = PeriodCalendar.firstWeekdayIndex(stats.baseTime)
        val gap = layout.cellGap
        val cellWidth = (g.body.width - 6f * gap) / 7f
        val cellHeight = layout.cellHeight
        val top = layout.chartTop
        weekdays.forEachIndexed { index, name ->
            val left = g.body.left + index * (cellWidth + gap)
            ink.centered(name, LandscapeRect(left, top, left + cellWidth, top + 18f * u), 12f)
        }
        for (day in 0 until count) {
            val slot = offset + day
            if (slot / 7 >= layout.visibleCalendarRows) continue
            val left = g.body.left + (slot % 7) * (cellWidth + gap)
            val y = top + 22f * u + (slot / 7) * (cellHeight + gap)
            val rect = LandscapeRect(left, y, left + cellWidth, y + cellHeight)
            dayCell(ink, state, stats, day, rect)
        }
    }

    private fun dayCell(
        ink: LandscapeInk,
        state: CardState,
        stats: PeriodSnapshot,
        day: Int,
        rect: LandscapeRect,
    ) {
        val seconds = stats.daySec?.getOrElse(day) { 0 } ?: 0
        val future = stats.isCurrentPeriod() && day > stats.todayIndex()
        val level = HostPeriodChart.heatLevel(seconds)
        val fill = dayFill(future, state.monthHeatmap(), level)
        ink.box(rect, fill, Color.LTGRAY)
        if (!state.monthHeatmap() && seconds > 0 && !future) {
            // Retain the user's checkbox representation of days with reading.
            ink.centered("✓", rect, 15f)
        } else {
            val textColor = if (state.monthHeatmap() && level >= 2 && !future) Color.WHITE else Color.DKGRAY
            ink.centered((day + 1).toString(), rect, 12f, textColor)
        }
        if (stats.isCurrentPeriod() && day == stats.todayIndex()) ink.box(rect, color = Color.BLACK)
    }

    private fun dayFill(
        future: Boolean,
        heatmap: Boolean,
        level: Int,
    ): Int? =
        when {
            future -> null
            !heatmap -> Color.WHITE
            level <= 0 -> Color.rgb(238, 238, 238)
            level == 1 -> Color.LTGRAY
            level == 2 -> Color.GRAY
            else -> Color.DKGRAY
        }

    private fun right(
        ink: LandscapeInk,
        value: String,
        edge: Float,
        top: Float,
        size: Float,
    ) {
        ink.paint.typeface = Typeface.DEFAULT
        ink.paint.textSize = maxOf(18f, size * ink.unit)
        ink.text(value, edge - ink.paint.measureText(value), top, size)
    }

    private fun summary(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        state: CardState,
        stats: PeriodSnapshot,
        top: Float,
        lineStep: Float,
    ) {
        val half = g.body.width / 2f
        ink.text("阅读 ${stats.readDays} 天", g.body.left, top, 14f, half)
        val avg = "日均 ${CardLayout.fmtShort(stats.avgSec)}"
        right(ink, avg, g.body.right, top, 14f)
        val compare = stats.compare
        var next = top + lineStep
        if (compare != null && !compare.isNaN()) {
            val label = if (state.isMonthly()) "较上月" else "较上周"
            val arrow = if (compare >= 0) "↑" else "↓"
            val percent = Math.round(kotlin.math.abs(compare) * 100)
            ink.text("$label $arrow$percent%", g.body.left, next, 13f, g.body.width, color = Color.DKGRAY)
            next += lineStep
        }
        val achievement = state.achievementText()
        if (!achievement.isNullOrBlank() && stats.isCurrentPeriod()) {
            ink.text(achievement, g.body.left, next, 13f, g.body.width, color = Color.DKGRAY)
        }
    }
}
