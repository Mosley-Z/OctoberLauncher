package com.shuiyu.octoberrain.feature.widgets.kernel

/** Vertical allocation only; header, width, fonts and card unit stay unchanged. */
internal data class LandscapePeriodLayout(
    val ruleY: Float,
    val totalTop: Float,
    val totalLineHeight: Float,
    val totalVisible: Boolean,
    val chartTop: Float,
    val chartBottom: Float,
    val chartVisible: Boolean,
    val weekHeight: Float,
    val calendarRows: Int,
    val visibleCalendarRows: Int,
    val cellHeight: Float,
    val cellGap: Float,
    val summaryTop: Float,
    val summaryLineStep: Float,
    val summaryBottom: Float,
    val minimumContentHeight: Float,
    val minimumCompleteHeight: Float,
)

internal object LandscapePeriodGeometry {
    fun compute(
        g: LandscapeCardGeometry,
        monthly: Boolean,
        calendarRows: Int,
        summaryRows: Int,
    ): LandscapePeriodLayout {
        val u = g.unit
        val rows = calendarRows.coerceIn(1, 6)
        val lines = summaryRows.coerceIn(1, 3)
        val ruleY = g.body.top - 2f * u
        val footerBottom = g.height - 12f * u
        val totalHeight = (if (monthly) 27f else 42f) * 1.2f * u
        val normalHeight = (20f + (lines - 1) * 24f) * u
        val compact = ruleY + 15f * u + totalHeight > footerBottom - normalHeight
        val lineStep = (if (compact) 18f else 24f) * u
        val summaryHeight = (if (compact) 18f else 20f) * u + (lines - 1) * lineStep
        val summaryTop = footerBottom - summaryHeight
        val totalTop = ruleY + minOf(15f * u, (summaryTop - ruleY - totalHeight).coerceAtLeast(0f))
        val totalVisible = totalTop + totalHeight <= summaryTop
        val chartTop = totalTop + (if (monthly) 40f else 60f) * u
        val chartBottom = summaryTop - 10f * u
        val gap = 4f * u
        val dateBudget = chartBottom - chartTop - 22f * u
        val fittingRows = ((dateBudget + gap) / (16f * u + gap)).toInt().coerceIn(0, rows)
        val visibleRows = if (monthly && totalVisible) fittingRows else 0
        val cellHeight = if (visibleRows > 0) (dateBudget - (visibleRows - 1) * gap) / visibleRows else 0f
        val weekHeight = (chartBottom - chartTop - 24f * u).coerceAtLeast(0f)
        val chartVisible = totalVisible && if (monthly) visibleRows > 0 else weekHeight >= 2f * u
        val completeChart = if (monthly) 22f + rows * 16f + (rows - 1) * 4f else 24f + 16f
        return LandscapePeriodLayout(
            ruleY,
            totalTop,
            totalHeight,
            totalVisible,
            chartTop,
            chartBottom,
            chartVisible,
            weekHeight,
            rows,
            visibleRows,
            cellHeight,
            gap,
            summaryTop,
            lineStep,
            footerBottom,
            ruleY + totalHeight + (18f * lines + 12f) * u,
            ruleY + (15f + (if (monthly) 40f else 60f) + completeChart + 10f) * u + normalHeight + 12f * u,
        )
    }
}
