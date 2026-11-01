package com.shuiyu.octoberrain.feature.widgets

internal fun immersiveStatsTypographyFits(
    cellHeightDp: Float,
    cellWidth: Float,
    cellHeight: Float,
    textWidth: Float,
    textHeight: Float,
): Boolean = cellHeightDp > 90f && textWidth <= cellWidth && textHeight <= cellHeight
