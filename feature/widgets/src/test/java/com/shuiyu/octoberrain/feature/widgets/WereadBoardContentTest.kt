package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 杂志版式文本生成的单元测试。
 *
 * 覆盖时长、进度、底部信息、划线整理、时间与日期文案的边界取值。
 */
class WereadBoardContentTest {
    /** 零秒与负数不显示时长。 */
    @Test
    fun durationHidesUnknownValue() {
        assertEquals("", readingDurationText(0))
        assertEquals("", readingDurationText(-30))
    }

    /** 不足一分钟按一分钟显示。 */
    @Test
    fun durationRoundsUpShortReading() {
        assertEquals("1m", readingDurationText(1))
        assertEquals("1m", readingDurationText(59))
        assertEquals("1m", readingDurationText(60))
    }

    /** 小时与分钟同时出现时使用「Xh Ym」格式。 */
    @Test
    fun durationUsesHourAndMinute() {
        assertEquals("9h 3m", readingDurationText(9 * 3600 + 3 * 60))
        assertEquals("9h 0m", readingDurationText(9 * 3600))
    }

    /** 进度百分数截断到 0 到 100。 */
    @Test
    fun progressClampsRange() {
        assertEquals("进度未知", readingProgressText(-1))
        assertEquals("已读 0%", readingProgressText(0))
        assertEquals("已读 100%", readingProgressText(101))
        assertEquals("已读 1%", readingProgressText(1))
    }

    /** 时长缺失时底部信息只保留进度。 */
    @Test
    fun infoOmitsMissingDuration() {
        assertEquals("已读 1%", readingInfoText(1, 0))
        assertEquals("已读 1%  ·  9h 3m", readingInfoText(1, 9 * 3600 + 3 * 60))
    }

    /** 划线正文压缩空白并去除首尾空白。 */
    @Test
    fun markTextCollapsesWhitespace() {
        assertEquals("", markTextOf(null))
        assertEquals("第一行 第二行", markTextOf("  第一行\n\n第二行  "))
    }

    /** 时间固定为两位小时与两位分钟。 */
    @Test
    fun clockPadsBothParts() {
        assertEquals("09:05", clockTextOf(9, 5))
        assertEquals("22:24", clockTextOf(22, 24))
        assertEquals("00:00", clockTextOf(0, 0))
    }

    /** 日期使用中文星期与月日。 */
    @Test
    fun dateUsesChineseWeekday() {
        assertEquals("星期三，10月7日", dateTextOf(10, 7, 4))
        assertEquals("星期日，1月1日", dateTextOf(1, 1, 1))
        assertEquals("星期日，1月1日", dateTextOf(1, 1, 0))
    }
}
