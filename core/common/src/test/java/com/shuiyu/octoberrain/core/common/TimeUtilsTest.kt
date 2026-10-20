package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * TimeUtils 的格式化与相对时间单元测试。
 *
 * 所有断言都显式传入固定时间戳或数值，不依赖运行时的当前时间，避免测试结果随执行时刻变化。
 */
class TimeUtilsTest {
    /** 固定时间基准，2023-11-15 06:13:20（UTC+8），仅作为相对时间的计算基准。 */
    private val now = 1_700_000_000_000L

    /** 构造本地时区下的时间戳，用于与 Calendar 解析结果对照。 */
    private fun localMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long =
        Calendar
            .getInstance()
            .apply {
                clear()
                set(year, month - 1, day, hour, minute, 0)
            }.timeInMillis

    /** 二十四小时制补零输出。 */
    @Test
    fun `formatTime 二十四小时制补零`() {
        assertEquals("00:00", TimeUtils.formatTime(0, 0, false))
        assertEquals("09:05", TimeUtils.formatTime(9, 5, false))
        assertEquals("13:30", TimeUtils.formatTime(13, 30, false))
        assertEquals("23:59", TimeUtils.formatTime(23, 59, false))
    }

    /** 十二小时制包含上午与下午标记，零点与正午边界分别显示十二点。 */
    @Test
    fun `formatTime 十二小时制边界`() {
        assertEquals("上午 12:00", TimeUtils.formatTime(0, 0, true))
        assertEquals("上午 9:05", TimeUtils.formatTime(9, 5, true))
        assertEquals("上午 11:59", TimeUtils.formatTime(11, 59, true))
        assertEquals("下午 12:00", TimeUtils.formatTime(12, 0, true))
        assertEquals("下午 1:04", TimeUtils.formatTime(13, 4, true))
        assertEquals("下午 11:59", TimeUtils.formatTime(23, 59, true))
    }

    /** 日期格式化输出月日与星期标签。 */
    @Test
    fun `formatDate 输出月日与星期`() {
        val millis = localMillis(2023, 1, 1, 12, 0)
        assertEquals("1月1日 星期日", TimeUtils.formatDate(millis))
    }

    /** 完整时间与日期部分分别按固定模式输出。 */
    @Test
    fun `formatDateTime 与 formatDay 输出固定模式`() {
        val millis = localMillis(2023, 1, 1, 12, 0)
        assertEquals("2023-01-01 12:00", TimeUtils.formatDateTime(millis))
        assertEquals("2023-01-01", TimeUtils.formatDay(millis))
    }

    /** 星期标签映射覆盖全部取值，未知取值回退为星期日。 */
    @Test
    fun `weekLabel 与 weekShort 映射完整`() {
        assertEquals("星期一", TimeUtils.weekLabel(Calendar.MONDAY))
        assertEquals("星期六", TimeUtils.weekLabel(Calendar.SATURDAY))
        assertEquals("星期日", TimeUtils.weekLabel(Calendar.SUNDAY))
        assertEquals("星期日", TimeUtils.weekLabel(0))

        assertEquals("一", TimeUtils.weekShort(Calendar.MONDAY))
        assertEquals("六", TimeUtils.weekShort(Calendar.SATURDAY))
        assertEquals("日", TimeUtils.weekShort(Calendar.SUNDAY))
    }

    /** 时长格式化区分分钟、整小时与小时加分钟三种形态，负数按零处理。 */
    @Test
    fun `formatDuration 三种形态与负数`() {
        assertEquals("0 分钟", TimeUtils.formatDuration(-5))
        assertEquals("0 分钟", TimeUtils.formatDuration(0))
        assertEquals("45 分钟", TimeUtils.formatDuration(45))
        assertEquals("1 小时", TimeUtils.formatDuration(60))
        assertEquals("1 小时 1 分", TimeUtils.formatDuration(61))
        assertEquals("1 小时 30 分", TimeUtils.formatDuration(90))
        assertEquals("24 小时", TimeUtils.formatDuration(1440))
    }

    /** 相对时间在一分钟以内显示刚刚，未来时间同样按刚刚处理。 */
    @Test
    fun `relative 一分钟以内与未来时间`() {
        assertEquals("刚刚", TimeUtils.relative(now, now))
        assertEquals("刚刚", TimeUtils.relative(now - 59_999L, now))
        assertEquals("刚刚", TimeUtils.relative(now + 60_000L, now))
    }

    /** 相对时间按分钟、小时、天依次进位，边界值取相邻区间的上界。 */
    @Test
    fun `relative 分钟小时天分段`() {
        assertEquals("1 分钟前", TimeUtils.relative(now - 60_000L, now))
        assertEquals("59 分钟前", TimeUtils.relative(now - 59 * 60_000L, now))
        assertEquals("1 小时前", TimeUtils.relative(now - 60 * 60_000L, now))
        assertEquals("23 小时前", TimeUtils.relative(now - 23 * 60 * 60_000L - 59 * 60_000L, now))
        assertEquals("1 天前", TimeUtils.relative(now - 24 * 60 * 60_000L, now))
        assertEquals("6 天前", TimeUtils.relative(now - 6 * 24 * 60 * 60_000L, now))
    }

    /** 超过七天改输出日期；当前实现没有单独的昨天分支，一天前显示为一天前。 */
    @Test
    fun `relative 超过七天输出日期`() {
        val sevenDays = now - 7 * 24 * 60 * 60_000L
        assertEquals(TimeUtils.formatDay(sevenDays), TimeUtils.relative(sevenDays, now))
    }
}
