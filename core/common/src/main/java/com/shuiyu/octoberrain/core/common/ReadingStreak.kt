package com.shuiyu.octoberrain.core.common

import java.time.LocalDate

/** Missing days remain unknown. Today joins the streak only after one minute. */
@Suppress("ReturnCount") // Missing today, a missing past day, and a known break have distinct meanings.
fun continuousReadingDays(
    days: Map<LocalDate, Long>,
    today: LocalDate,
): Int? {
    val current = days[today] ?: return null
    var cursor = if (current >= 60L) today else today.minusDays(1)
    var count = 0
    while (true) {
        val seconds = days[cursor] ?: return null
        if (seconds < 60L) return count
        count++
        cursor = cursor.minusDays(1)
    }
}
