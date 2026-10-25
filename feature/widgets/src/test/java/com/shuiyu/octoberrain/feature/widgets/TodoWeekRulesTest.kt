package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.common.WereadTodo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 待办周历日期窗口规则的单元测试。
 *
 * 覆盖含今天的窗口起点、起始星期收敛、跨月跨年标题、跨夏令时的日期换算与星期单字。
 */
class TodoWeekCalendarTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 起始星期决定窗口起点，今天落在窗口内。 */
    @Test
    fun startIncludesToday() {
        val monday = LocalDate.of(2026, 10, 19)
        assertEquals(monday, TodoWeekCalendar.startFor(monday.plusDays(3), 1))
        assertEquals(monday, TodoWeekCalendar.startFor(monday.plusDays(6), 1))
        assertEquals(monday.plusDays(2), TodoWeekCalendar.startFor(monday.plusDays(3), 3))
    }

    /** 越界的起始星期收敛为周一。 */
    @Test
    fun startDayIsNormalized() {
        val today = LocalDate.of(2026, 10, 21)
        assertEquals(1, TodoWeekCalendar.normalizeStartDay(0))
        assertEquals(1, TodoWeekCalendar.normalizeStartDay(8))
        assertEquals(7, TodoWeekCalendar.normalizeStartDay(7))
        assertEquals(TodoWeekCalendar.startFor(today, 1), TodoWeekCalendar.startFor(today, 8))
    }

    /** 七天窗口跨月时保持连续。 */
    @Test
    fun daysSpanMonthBoundary() {
        val days = TodoWeekCalendar.daysOf(LocalDate.of(2026, 10, 28))
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 10, 28), days.first())
        assertEquals(LocalDate.of(2026, 11, 3), days.last())
        assertEquals(listOf("三", "四", "五", "六", "日", "一", "二"), days.map(TodoWeekCalendar::weekdayLabel))
    }

    /** 第二行右移一天后星期排列整体前移一位。 */
    @Test
    fun shiftOneDayMovesWeekdayOrder() {
        val start = LocalDate.of(2026, 10, 19)
        val shifted = start.minusDays(1)
        assertEquals("一", TodoWeekCalendar.weekdayLabel(start))
        assertEquals("日", TodoWeekCalendar.weekdayLabel(shifted))
        assertEquals(
            listOf("日", "一", "二", "三", "四", "五", "六"),
            TodoWeekCalendar.daysOf(shifted).map(TodoWeekCalendar::weekdayLabel),
        )
    }

    /** 月份与年份范围按窗口首尾生成。 */
    @Test
    fun titleCoversMonthAndYear() {
        assertEquals("2026年10月", TodoWeekCalendar.titleOf(LocalDate.of(2026, 10, 19)))
        assertEquals("2026年10–11月", TodoWeekCalendar.titleOf(LocalDate.of(2026, 10, 28)))
        assertEquals("2025年12月–2026年1月", TodoWeekCalendar.titleOf(LocalDate.of(2025, 12, 29)))
    }

    /** 日期换算在夏令时切换当天仍回到同一日期。 */
    @Test
    fun dateConversionSurvivesDaylightSaving() {
        val daylightZone = ZoneId.of("America/New_York")
        val springForward = LocalDate.of(2026, 3, 8)
        val fallBack = LocalDate.of(2026, 11, 1)
        assertEquals(
            springForward,
            TodoWeekCalendar.dateOf(TodoWeekCalendar.startOfDay(springForward, daylightZone), daylightZone),
        )
        assertEquals(
            fallBack,
            TodoWeekCalendar.dateOf(TodoWeekCalendar.startOfDay(fallBack, daylightZone), daylightZone),
        )
    }

    /** 零点秒数按本地时区换算而不是按 UTC。 */
    @Test
    fun startOfDayUsesLocalMidnight() {
        val date = LocalDate.of(2026, 1, 1)
        val seconds = TodoWeekCalendar.startOfDay(date, zone)
        assertEquals(date, TodoWeekCalendar.dateOf(seconds, zone))
        assertTrue(seconds > 0L)
    }
}

/**
 * 待办周历投影与排序规则的单元测试。
 *
 * 覆盖按日过滤、完成状态优先、时间升序、未设时间置后、零点时间有效与同时间稳定排序。
 */
class TodoWeekItemsTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val day: LocalDate = LocalDate.of(2026, 10, 21)

    /** 未完成的晚时刻排在已完成早时刻之前。 */
    @Test
    fun pendingComesBeforeDone() {
        val done = todo(id = 1, minute = 8 * 60, done = true, order = 0)
        val pending = todo(id = 2, minute = 21 * 60, order = 1)
        val sorted = TodoWeekItems.sort(listOf(done, pending))
        assertEquals(listOf(2L, 1L), sorted.map(WereadTodo::id))
    }

    /** 同一完成状态下按时间升序，未设时间排在该组末尾。 */
    @Test
    fun timeSortsBeforeUntimed() {
        val late = todo(id = 1, minute = 20 * 60, order = 0)
        val early = todo(id = 2, minute = 9 * 60, order = 1)
        val untimed = todo(id = 3, minute = WereadTodo.NO_TIME, order = 2)
        val sorted = TodoWeekItems.sort(listOf(late, untimed, early))
        assertEquals(listOf(2L, 1L, 3L), sorted.map(WereadTodo::id))
    }

    /** 零点是有效时间，按时间排序时排在最前。 */
    @Test
    fun midnightCountsAsTime() {
        val midnight = todo(id = 1, minute = 0, order = 0)
        val untimed = todo(id = 2, minute = WereadTodo.NO_TIME, order = 1)
        val sorted = TodoWeekItems.sort(listOf(untimed, midnight))
        assertTrue(sorted.first().hasTime)
        assertEquals(listOf(1L, 2L), sorted.map(WereadTodo::id))
    }

    /** 时间相同时保持原有手工顺序。 */
    @Test
    fun equalTimeKeepsManualOrder() {
        val second = todo(id = 1, minute = 10 * 60, order = 5)
        val first = todo(id = 2, minute = 10 * 60, order = 2)
        assertEquals(listOf(2L, 1L), TodoWeekItems.sort(listOf(second, first)).map(WereadTodo::id))
    }

    /** 只投影落在该日且设置了日期的待办。 */
    @Test
    fun projectsMatchingDayOnly() {
        val onDay = todo(id = 1, date = day)
        val otherDay = todo(id = 2, date = day.plusDays(1))
        val noDate = todo(id = 3, date = null)
        val projected = TodoWeekItems.onDay(listOf(onDay, otherDay, noDate), day, zone)
        assertEquals(listOf(1L), projected.map(WereadTodo::id))
    }

    /** 七天分组与传入日期一一对应。 */
    @Test
    fun weekGroupsByDay() {
        val days = TodoWeekCalendar.daysOf(day)
        val todos = listOf(todo(id = 1, date = days[0]), todo(id = 2, date = days[3]), todo(id = 3, date = days[3]))
        val grouped = TodoWeekItems.week(todos, days, zone)
        assertEquals(7, grouped.size)
        assertEquals(listOf(1L, 2L, 3L), grouped.flatMap { items -> items.map(WereadTodo::id) })
        assertEquals(2, grouped[3].size)
    }

    /** 时间输入区分零点、未设置与非法格式。 */
    @Test
    fun parsesTimeInput() {
        assertEquals(0, TodoWeekItems.parseTimeInput("00:00"))
        assertEquals(WereadTodo.NO_TIME, TodoWeekItems.parseTimeInput("  "))
        assertEquals(450, TodoWeekItems.parseTimeInput("7:30"))
        assertEquals(23 * 60 + 59, TodoWeekItems.parseTimeInput("23:59"))
        assertNull(TodoWeekItems.parseTimeInput("24:00"))
        assertNull(TodoWeekItems.parseTimeInput("7:30:00"))
        assertNull(TodoWeekItems.parseTimeInput("abc"))
    }

    /**
     * 构造一条测试用待办。
     *
     * @param id 标识
     * @param minute 一天内分钟数，缺省表示未设时间
     * @param done 是否已完成
     * @param order 原手工顺序
     * @param date 日期，null 表示未设置
     * @return 测试待办
     */
    private fun todo(
        id: Long,
        minute: Int = WereadTodo.NO_TIME,
        done: Boolean = false,
        order: Long = 0L,
        date: LocalDate? = day,
    ): WereadTodo =
        WereadTodo(
            id = id,
            content = "待办$id",
            dateSec = date?.let { TodoWeekCalendar.startOfDay(it, zone) } ?: 0L,
            minuteOfDay = minute,
            done = done,
            order = order,
        )
}
