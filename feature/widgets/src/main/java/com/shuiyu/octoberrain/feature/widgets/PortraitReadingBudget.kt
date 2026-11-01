package com.shuiyu.octoberrain.feature.widgets

/** 竖屏阅读组的纯数值高度分配，单位为像素。 */
internal data class PortraitReadingBudget(
    val coverHeight: Float,
    val leading: Float,
    val statsGap: Float,
    val statsHeight: Float,
    val textScale: Float,
)

/** 先消耗留白与封面，再缩组间距；文字缩放仅作为最后兜底。 */
internal fun portraitReadingBudget(
    height: Float,
    preferredCover: Float,
    readingTextHeight: Float,
    statsHeight: Float,
    fixedSpacing: Float,
    preferredStatsGap: Float,
    minimumStatsGap: Float,
): PortraitReadingBudget {
    val available = height.coerceAtLeast(0f)
    val textHeight = readingTextHeight + statsHeight
    val gap = (available - fixedSpacing - textHeight).coerceIn(minimumStatsGap, preferredStatsGap)
    val scale = ((available - fixedSpacing - gap).coerceAtLeast(0f) / textHeight.coerceAtLeast(1f)).coerceAtMost(1f)
    val fixed = textHeight * scale + fixedSpacing + gap
    val cover = minOf(preferredCover, (available - fixed).coerceAtLeast(0f))
    return PortraitReadingBudget(
        cover,
        ((available - fixed - cover) / 2).coerceAtLeast(0f),
        gap,
        statsHeight * scale,
        scale,
    )
}
