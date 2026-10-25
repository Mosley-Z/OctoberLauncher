package com.shuiyu.octoberrain.feature.widgets

import java.util.Calendar

/** 与时间一同取得的不可变日期快照，预览与桌面使用同一时区和日界。 */
data class ClockDate(
    val year: Int = 1970,
    val month: Int = 1,
    val day: Int = 1,
    val weekday: Int = Calendar.THURSDAY,
) {
    val weekdayText: String get() = "星期${WEEKDAYS[weekday - 1]}"
    val fullText: String get() = "${year}年${month}月${day}日　$weekdayText"
    val lines: List<String> get() = listOf("${year}年", "${month}月${day}日", weekdayText)

    companion object {
        private const val WEEKDAYS = "日一二三四五六"

        fun from(calendar: Calendar): ClockDate =
            ClockDate(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.DAY_OF_WEEK),
            )
    }
}

/** E固定使用24小时值且始终两字：零时至九时、一零至二四；分钟保持两位逐字读。 */
internal val ClockTime.chineseLines: List<String>
    get() {
        val digits = "零一二三四五六七八九"
        val hour = if (hour24 < 10) "${digits[hour24]}时" else "${digits[hour24 / 10]}${digits[hour24 % 10]}"
        val minutes = "${digits[minute / 10]}${digits[minute % 10]}"
        return listOf(hour, minutes)
    }
