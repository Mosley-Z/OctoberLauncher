package com.shuiyu.octoberrain.feature.widgets.kernel

/** Centers only the ordinary portrait month content without changing its scale or height. */
internal class MonthContentFrame(
    availableWidth: Int,
    portraitMonth: Boolean = false,
) {
    val width: Int = if (portraitMonth) minOf(availableWidth, 412) else availableWidth
    val offsetX: Float = (availableWidth - width) / 2f

    fun localX(x: Float): Float? {
        if (offsetX == 0f) return x
        return (x - offsetX).takeIf { it >= 0f && it < width }
    }
}
