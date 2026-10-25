package com.shuiyu.octoberrain.feature.widgets

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 待办周历的日期窗口规则。
 *
 * 全部运算基于本地日历日：窗口起点、平移后的七天与今日判定都不依赖固定的秒数偏移，
 * 跨月、跨年以及夏令时切换由 `java.time` 处理。
 */
internal object TodoWeekCalendar {
    /** 一周固定七天。 */
    const val DAY_COUNT: Int = 7

    /** 星期单字，按 ISO 次序从周一到周日。 */
    private val WEEKDAY_LABELS: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

    /**
     * 把配置值收敛到 ISO 星期范围。
     *
     * @param value 配置中的起始星期
     * @return 1 到 7 之间的起始星期，越界时取周一
     */
    fun normalizeStartDay(value: Int): Int = value.takeIf { it in 1..DAY_COUNT } ?: 1

    /**
     * 计算含指定日期的七天窗口起点。
     *
     * 起点为该起始星期最近一次出现的日期，因此今天始终落在窗口内。
     *
     * @param today 用于定位窗口的日期
     * @param startDay 起始星期，ISO 取值 1 到 7
     * @return 窗口第一天
     */
    fun startFor(
        today: LocalDate,
        startDay: Int,
    ): LocalDate {
        val offset = (today.dayOfWeek.value - normalizeStartDay(startDay) + DAY_COUNT) % DAY_COUNT
        return today.minusDays(offset.toLong())
    }

    /**
     * 展开窗口的七天。
     *
     * @param start 窗口第一天
     * @return 从起点开始的七个连续日期
     */
    fun daysOf(start: LocalDate): List<LocalDate> = (0 until DAY_COUNT).map { start.plusDays(it.toLong()) }

    /**
     * 取日期对应的 ISO 星期。
     *
     * @param date 目标日期
     * @return 1 为周一，7 为周日
     */
    fun startDayOf(date: LocalDate): Int = date.dayOfWeek.value

    /**
     * 取日期的星期单字。
     *
     * @param date 目标日期
     * @return 单个汉字的星期
     */
    fun weekdayLabel(date: LocalDate): String = WEEKDAY_LABELS[date.dayOfWeek.value - 1]

    /**
     * 生成窗口标题，跨月写两个月份，跨年写完整年月范围。
     *
     * @param start 窗口第一天
     * @return 例如「2026年5月」「2026年5–6月」「2025年12月–2026年1月」
     */
    fun titleOf(start: LocalDate): String {
        val end = start.plusDays((DAY_COUNT - 1).toLong())
        return when {
            start.year != end.year -> "${start.year}年${start.monthValue}月–${end.year}年${end.monthValue}月"
            start.monthValue != end.monthValue -> "${start.year}年${start.monthValue}–${end.monthValue}月"
            else -> "${start.year}年${start.monthValue}月"
        }
    }

    /**
     * 把待办日期换算为本地日期。
     *
     * @param dateSec 当天零点的秒数
     * @param zone 换算使用的时区，默认取系统时区
     * @return 该秒数对应的本地日期
     */
    fun dateOf(
        dateSec: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = Instant.ofEpochSecond(dateSec).atZone(zone).toLocalDate()

    /**
     * 取某个本地日期的零点秒数，作为待办日期写入。
     *
     * @param date 目标日期
     * @param zone 换算使用的时区，默认取系统时区
     * @return 当天零点的秒数
     */
    fun startOfDay(
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = date.atStartOfDay(zone).toEpochSecond()
}
