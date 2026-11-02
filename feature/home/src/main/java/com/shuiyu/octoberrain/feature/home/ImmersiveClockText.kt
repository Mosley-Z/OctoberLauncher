package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.TimeUtils
import java.util.Calendar
import java.util.Date

/** 测量与绘制共享数字文本；时区使用当前本地时区，时段仅属于顶部状态栏。 */
internal fun immersiveClockText(
    now: Date,
    twelveHour: Boolean,
): String {
    val calendar = Calendar.getInstance().apply { time = now }
    return TimeUtils.formatTime(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), twelveHour)
}
