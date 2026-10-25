package com.shuiyu.octoberrain.feature.widgets.kernel

/** A dated row adds exactly its date band; undated rows have no hidden line reservation. */
internal object LandscapeTodoGeometry {
    fun rowHeight(
        lineCount: Int,
        hasDate: Boolean,
        unit: Float,
    ): Float =
        32f * unit + maxOf(24f * unit, lineCount.coerceAtLeast(1) * 28.5f * unit) +
            if (hasDate) 24f * unit else 0f

    fun visibleRow(
        viewport: LandscapeRect,
        top: Float,
        height: Float,
    ): LandscapeRect? {
        val visibleTop = maxOf(viewport.top, top)
        val visibleBottom = minOf(viewport.bottom, top + height)
        return if (visibleBottom > visibleTop) {
            LandscapeRect(viewport.left, visibleTop, viewport.right, visibleBottom)
        } else {
            null
        }
    }
}
