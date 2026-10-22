package com.shuiyu.octoberrain.feature.reader

import android.content.Context

/** 仅已提交的有效翻页计数；任何来源完成全刷后重新开始计数。 */
internal class ReaderRefresh {
    private var turns = 0
    private var completed = -1L

    fun reset() {
        turns = 0
    }

    fun needsFull(
        interval: Int,
        fullVersion: Long,
        turned: Boolean,
    ): Boolean {
        if (completed != fullVersion) {
            completed = fullVersion
            reset()
        }
        if (interval == 0 || !turned) return false
        turns++
        val full = turns >= interval
        if (full) reset()
        return full
    }
}

/** 阅读器独立偏好，不改桌面/书架的刷新设置。 */
internal class ReaderPreferences(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("reader_preferences", Context.MODE_PRIVATE)

    var refreshPages: Int
        get() = prefs.getInt("refresh_pages", 6).takeIf { it in listOf(0, 6, 12) } ?: 6
        set(value) {
            require(value in listOf(0, 6, 12))
            prefs.edit().putInt("refresh_pages", value).apply()
        }
}
