package com.shuiyu.octoberrain.feature.home

/** 自动首页仅在保留半屏组件空间且三行应用完整放下时增加一行。 */
internal fun portraitFirstRows(
    available: Int,
    rowSize: Int,
    gap: Int,
    widgetGap: Int,
    minimumWidget: Int,
    baselineRows: Int,
): Int {
    val baseline = baselineRows.coerceAtLeast(1)
    val appHeight = 3 * rowSize + 2 * gap
    val widgetBudget = maxOf((available + 1) / 2, minimumWidget)
    return if (baseline < 3 && available - appHeight - widgetGap >= widgetBudget) 3 else baseline
}
