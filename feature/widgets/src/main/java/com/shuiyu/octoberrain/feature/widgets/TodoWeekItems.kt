package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.common.WereadTodo
import java.time.LocalDate
import java.time.ZoneId

/**
 * 待办周历的取数与排序规则。
 *
 * 只投影设置了日期且落在窗口内的待办；每个日历日内部按「未完成优先、有时间的按时间升序、
 * 未设时间的排在同组末尾、其余保持原有手工顺序」排列。排序结果只用于绘制，不回写存储。
 */
internal object TodoWeekItems {
    /** 同一天内的绘制顺序：完成状态、是否设时间、时间、原排序序号、标识。 */
    private val DAY_ORDER: Comparator<WereadTodo> =
        compareBy<WereadTodo>(
            { it.done },
            { if (it.hasTime) 0 else 1 },
            { if (it.hasTime) it.minuteOfDay else 0 },
            { it.order },
            { it.id },
        )

    /**
     * 判断待办是否属于指定日期。
     *
     * @param todo 待办
     * @param date 本地日期
     * @param zone 换算时区
     * @return 设置了日期且落在该日期时为 true
     */
    fun belongsTo(
        todo: WereadTodo,
        date: LocalDate,
        zone: ZoneId,
    ): Boolean = todo.hasDate && TodoWeekCalendar.dateOf(todo.dateSec, zone) == date

    /**
     * 取某个日期的待办并按绘制顺序排列。
     *
     * @param todos 全部待办
     * @param date 目标日期
     * @param zone 换算时区
     * @return 该日期的待办，已按绘制顺序排列
     */
    fun onDay(
        todos: List<WereadTodo>,
        date: LocalDate,
        zone: ZoneId,
    ): List<WereadTodo> = sort(todos.filter { belongsTo(it, date, zone) })

    /**
     * 按窗口的七天分组，结果与传入日期一一对应。
     *
     * @param todos 全部待办
     * @param days 窗口内的七个日期
     * @param zone 换算时区
     * @return 每一天对应一组已排序的待办
     */
    fun week(
        todos: List<WereadTodo>,
        days: List<LocalDate>,
        zone: ZoneId,
    ): List<List<WereadTodo>> = days.map { onDay(todos, it, zone) }

    /**
     * 按绘制顺序排列待办。
     *
     * 时间值零点是有效时间，因此判断是否设时间只看 [WereadTodo.hasTime]，
     * 不把分钟数当作是否存在时间的依据。
     *
     * @param todos 待排序的待办
     * @return 排序后的新清单
     */
    fun sort(todos: List<WereadTodo>): List<WereadTodo> = todos.sortedWith(DAY_ORDER)

    /**
     * 解析「HH:mm」格式的时间输入。
     *
     * 空白表示清除时间，返回 [WereadTodo.NO_TIME]；格式或范围不合法时返回 null，
     * 由调用方提示重新输入。零点属于合法时间，返回 0。
     *
     * @param text 用户输入
     * @return 一天内分钟数、[WereadTodo.NO_TIME] 或 null
     */
    fun parseTimeInput(text: String): Int? {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) WereadTodo.NO_TIME else parseClock(trimmed)
    }

    /**
     * 解析非空的时间文本，越界与格式错误统一视为不合法。
     *
     * @param text 已去除首尾空白的输入
     * @return 一天内分钟数，格式或范围不合法时为 null
     */
    private fun parseClock(text: String): Int? {
        val match = TIME_PATTERN.matchEntire(text) ?: return null
        val hour = match.groupValues[1].toIntOrNull()
        val minute = match.groupValues[2].toIntOrNull()
        return if (hour == null || minute == null) null else toMinutes(hour, minute)
    }

    /**
     * 把合法数字换算成一天内分钟数，越界返回 null。
     *
     * @param hour 小时
     * @param minute 分钟
     * @return 一天内分钟数，越界时为 null
     */
    private fun toMinutes(
        hour: Int,
        minute: Int,
    ): Int? = if (hour > MAX_HOUR || minute > MAX_MINUTE) null else hour * WereadTodo.MINUTES_PER_HOUR + minute

    /** 时间输入的小时上限。 */
    private const val MAX_HOUR = 23

    /** 时间输入的分钟上限。 */
    private const val MAX_MINUTE = 59

    /** 时间输入格式。 */
    private val TIME_PATTERN = Regex("^(\\d{1,2}):(\\d{2})$")
}
