package com.shuiyu.octoberrain.feature.widgets.kernel

/** Reserves measured footer text before allocating every calendar row, including six-row months. */
internal data class MonthLandscapeBudget(
    val top: Float,
    val bottom: Float,
    val chartTop: Float,
    val cell: Float,
    val gap: Float,
    val totalTop: Float,
    val summaryTop: Float,
) {
    companion object {
        fun compute(
            top: Float,
            bottom: Float,
            width: Float,
            rows: Int,
            headerHeight: Float,
            totalHeight: Float,
            summaryHeight: Float,
            unit: Float,
        ): MonthLandscapeBudget {
            val gap = 4f * unit
            val summaryTop = bottom - summaryHeight
            val totalTop = summaryTop - 8f * unit - totalHeight
            val chartTop = top + 12f * unit
            val count = rows.coerceIn(4, 6)
            val budget = totalTop - 12f * unit - chartTop - headerHeight
            val cell = minOf(28f * unit, (width - 6 * gap) / 7f, (budget - (count - 1) * gap) / count).coerceAtLeast(0f)
            return MonthLandscapeBudget(top, bottom, chartTop, cell, gap, totalTop, summaryTop)
        }
    }
}
