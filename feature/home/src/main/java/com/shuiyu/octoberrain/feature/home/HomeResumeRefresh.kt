package com.shuiyu.octoberrain.feature.home

import java.time.LocalDate

/** Background content changes survive a layout-only resume without reloading unchanged content. */
internal class HomeResumeRefresh(
    private var day: LocalDate = LocalDate.now(),
) {
    private var dirty = false

    fun invalidate() {
        dirty = true
    }

    fun resume(
        reuseUnchanged: Boolean,
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        val refresh = !reuseUnchanged || dirty || today != day
        dirty = false
        day = today
        return refresh
    }
}
