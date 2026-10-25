package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ClockTimeTest {
    @Test
    fun twelveHourMidnightNoonAndLateEvening() {
        assertEquals("12:00", ClockTime(0, 0, 0, true).timeText)
        assertEquals("12:00", ClockTime(12, 0, 0, true).timeText)
        assertEquals("11:59", ClockTime(23, 59, 0, true).timeText)
        assertEquals("00:00", ClockTime(0, 0, 0).timeText)
        assertEquals("23:59", ClockTime(23, 59, 0).timeText)
    }

    @Test
    fun chineseClockUsesExactlyTwoHourCharactersAndTwoMinuteDigits() {
        val digits = "零一二三四五六七八九"
        for (hour in 0..24) {
            val expected = if (hour < 10) "${digits[hour]}时" else "${digits[hour / 10]}${digits[hour % 10]}"
            listOf(false, true).forEach { twelveHour ->
                val lines = ClockTime(hour, 41, 0, twelveHour).chineseLines
                assertEquals(expected, lines[0])
                assertEquals(2, lines[0].length)
                assertEquals("四一", lines[1])
            }
        }
        assertEquals(listOf("零时", "零零"), ClockTime(0, 0, 0, true).chineseLines)
        assertEquals(listOf("一零", "一零"), ClockTime(10, 10, 0).chineseLines)
        assertEquals(listOf("二三", "五九"), ClockTime(23, 59, 0).chineseLines)
        assertEquals(listOf("二四", "零五"), ClockTime(24, 5, 0, true).chineseLines)
    }

    @Test
    fun designNumberWidthDoesNotOverrideChosenFonts() {
        assertEquals(1.18f, clockNumberScale(ClockFace.BOOK_PAGE, "default"), .001f)
        assertEquals(1.18f, clockNumberScale(ClockFace.DIVIDED, "default"), .001f)
        assertEquals(1.18f, clockNumberScale(ClockFace.TIME_DATE, "default"), .001f)
        assertEquals(1f, clockNumberScale(ClockFace.TIME_DATE, "mono"), .001f)
        assertEquals(1f, clockNumberScale(ClockFace.STUDY_DIAL, "default"), .001f)
    }

    @Test
    fun dateAndTimeComeFromTheSameCalendarAcrossYearBoundary() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        calendar.set(2026, Calendar.DECEMBER, 31, 23, 59, 0)
        assertEquals("2026年12月31日　星期四", ClockTime.from(calendar, false).date.fullText)
        calendar.add(Calendar.MINUTE, 1)
        val next = ClockTime.from(calendar, false)
        assertEquals("00:00", next.timeText)
        assertEquals(listOf("2027年", "1月1日", "星期五"), next.date.lines)
    }

    @Test
    fun publishedIdentifiersAndUnknownFallbackArePreserved() {
        val legacy = listOf("default", "stacked", "flip", "boxed", "analog")
        assertEquals(legacy, ClockFace.entries.take(5).map { it.id })
        assertEquals(
            11,
            ClockFace.entries
                .map { it.id }
                .toSet()
                .size,
        )
        legacy.forEach { assertEquals(it, ClockFace.fromId(it).id) }
        assertEquals(ClockFace.DEFAULT, ClockFace.fromId("future_clock"))
        assertEquals(ClockFace.DEFAULT, ClockFace.fromId(null))
    }
}
