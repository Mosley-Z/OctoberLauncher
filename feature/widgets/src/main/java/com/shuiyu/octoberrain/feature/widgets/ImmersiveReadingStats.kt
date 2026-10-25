package com.shuiyu.octoberrain.feature.widgets

/** Cached reading totals. Null means absent; zero is an explicitly reported value. */
data class ImmersiveReadingStats(
    val todayMinutes: Int? = null,
    val finishedBooks: Int? = null,
    val streakDays: Int? = null,
    val dateKey: String? = null,
)
