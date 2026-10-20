package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.common.WereadRankEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 微信读书组件纯逻辑测试：覆盖三态解析、文本格式、指纹分档与排行筛选。
 *
 * 全部用例不依赖 Android 运行时，仅验证 [WereadWidgetLogic] 与
 * [WereadWidgetData] 的纯函数行为。
 */
class WereadWidgetLogicTest {
    /** 密钥为空时优先判定为未配置。 */
    @Test
    fun blankKeyIsUnconfigured() {
        val state = WereadWidgetLogic.stateOf("", enabled = true, shelfEnabled = true, hasData = true)
        assertEquals(WereadWidgetState.UNCONFIGURED, state)
    }

    /** 集成开关关闭时判定为未配置。 */
    @Test
    fun disabledIntegrationIsUnconfigured() {
        val state = WereadWidgetLogic.stateOf("key", enabled = false, shelfEnabled = true, hasData = true)
        assertEquals(WereadWidgetState.UNCONFIGURED, state)
    }

    /** 书架开关关闭时判定为未配置，先于数据判定。 */
    @Test
    fun shelfDisabledIsUnconfigured() {
        val state = WereadWidgetLogic.stateOf("key", enabled = true, shelfEnabled = false, hasData = true)
        assertEquals(WereadWidgetState.UNCONFIGURED, state)
    }

    /** 配置完整但无数据时判定为空态。 */
    @Test
    fun configuredWithoutDataIsEmpty() {
        val state = WereadWidgetLogic.stateOf("key", enabled = true, shelfEnabled = true, hasData = false)
        assertEquals(WereadWidgetState.EMPTY, state)
    }

    /** 配置完整且有数据时判定为可展示。 */
    @Test
    fun configuredWithDataIsReady() {
        val state = WereadWidgetLogic.stateOf("key", enabled = true, shelfEnabled = true, hasData = true)
        assertEquals(WereadWidgetState.READY, state)
    }

    /** 空态与未配置态返回固定中文文案，可展示态不产生文案。 */
    @Test
    fun messagesMatchStates() {
        assertEquals("微信读书未配置", WereadWidgetLogic.messageOf(WereadWidgetState.UNCONFIGURED))
        assertEquals("暂无阅读数据", WereadWidgetLogic.messageOf(WereadWidgetState.EMPTY))
        assertEquals("", WereadWidgetLogic.messageOf(WereadWidgetState.READY))
    }

    /** 分钟数格式覆盖零、分钟、整小时与小时加分钟。 */
    @Test
    fun minutesFormatting() {
        assertEquals("0 分钟", WereadWidgetLogic.formatMinutes(0))
        assertEquals("45 分钟", WereadWidgetLogic.formatMinutes(45))
        assertEquals("1 小时", WereadWidgetLogic.formatMinutes(60))
        assertEquals("2 小时 5 分钟", WereadWidgetLogic.formatMinutes(125))
    }

    /** 增幅计算在上月为零时返回 null，其余四舍五入。 */
    @Test
    fun deltaPercentRounding() {
        assertNull(WereadWidgetLogic.deltaPercent(120, 0))
        assertEquals(50, WereadWidgetLogic.deltaPercent(180, 120))
        assertEquals(-25, WereadWidgetLogic.deltaPercent(90, 120))
    }

    /** 增幅文案覆盖无上月数据、持平与正负三种情况。 */
    @Test
    fun deltaFormatting() {
        assertEquals(WereadWidgetLogic.DELTA_NONE, WereadWidgetLogic.formatDelta(120, 0))
        assertEquals(WereadWidgetLogic.DELTA_FLAT, WereadWidgetLogic.formatDelta(60, 60))
        assertEquals("+50%", WereadWidgetLogic.formatDelta(180, 120))
        assertEquals("-25%", WereadWidgetLogic.formatDelta(90, 120))
    }

    /** 活跃天数只统计大于零的日期。 */
    @Test
    fun activeDaysCountsPositiveValues() {
        assertEquals(3, WereadWidgetLogic.activeDays(listOf(0, 10, 0, 5, 7)))
    }

    /** 最长连续天数在中断处重新计数。 */
    @Test
    fun longestStreakResetsAfterGap() {
        assertEquals(3, WereadWidgetLogic.longestStreak(listOf(1, 2, 3, 0, 4, 5)))
        assertEquals(0, WereadWidgetLogic.longestStreak(listOf(0, 0)))
    }

    /** 峰值为零与空列表的表现一致。 */
    @Test
    fun peakMinutesHandlesEmpty() {
        assertEquals(0, WereadWidgetLogic.peakMinutes(emptyList()))
        assertEquals(0, WereadWidgetLogic.peakMinutes(listOf(0, 0)))
        assertEquals(42, WereadWidgetLogic.peakMinutes(listOf(10, 42, 3)))
    }

    /** 峰值为零时全部比例为零，避免除零。 */
    @Test
    fun ratiosHandleZeroPeak() {
        assertTrue(WereadWidgetData.ratios(listOf(0, 0)).all { it == 0f })
        assertEquals(listOf(0.5f, 1f, 0f), WereadWidgetData.ratios(listOf(10, 20, 0)))
    }

    /** 灰度分档在边界值处按闭区间落入对应档位。 */
    @Test
    fun levelThresholds() {
        assertEquals(0, WereadWidgetData.levelOf(0f))
        assertEquals(1, WereadWidgetData.levelOf(0.34f))
        assertEquals(2, WereadWidgetData.levelOf(0.5f))
        assertEquals(2, WereadWidgetData.levelOf(0.67f))
        assertEquals(3, WereadWidgetData.levelOf(0.9f))
    }

    /** 末尾取值不足时左侧补零，超出时截取末尾。 */
    @Test
    fun lastDaysPadsAndTruncates() {
        assertEquals(listOf(0, 0, 1, 2), WereadWidgetData.lastDays(listOf(1, 2), 4))
        assertEquals(listOf(3, 4), WereadWidgetData.lastDays(listOf(1, 2, 3, 4), 2))
        assertEquals(emptyList<Int>(), WereadWidgetData.lastDays(listOf(1), 0))
    }

    /** 指纹固定为七列四行共 28 格。 */
    @Test
    fun fingerprintHasFixedCells() {
        val cells = WereadWidgetData.fingerprintCells(listOf(1, 2, 3))
        assertEquals(28, cells.size)
        assertTrue(cells.all { it in 0..3 })
    }

    /** 进度值越界时被夹取到 0 与 1。 */
    @Test
    fun progressClamping() {
        assertEquals(0f, WereadWidgetData.clampProgress(-0.2f))
        assertEquals(1f, WereadWidgetData.clampProgress(1.4f))
        assertEquals(73, WereadWidgetData.progressPercent(0.725f))
    }

    /** 排行按进度降序并限制条数。 */
    @Test
    fun rankEntriesSortsAndLimits() {
        val entries =
            listOf(
                WereadRankEntry(title = "A", author = "", progress = 0.2f),
                WereadRankEntry(title = "B", author = "", progress = 0.9f),
                WereadRankEntry(title = "C", author = "", progress = 0.5f),
            )
        assertEquals(listOf("B", "C"), WereadWidgetData.rankEntries(entries, 2).map { it.title })
        assertEquals(0, WereadWidgetData.rankEntries(entries, 0).size)
    }

    /** 笔记摘要折叠空白并在超长时截断。 */
    @Test
    fun noteSummaryCompactsAndTruncates() {
        assertEquals("第一行 第二行", WereadWidgetData.noteSummary("  第一行\n\n第二行  "))
        val long = "字".repeat(80)
        val summary = WereadWidgetData.noteSummary(long, maxLength = 10)
        assertEquals(10, summary.length)
        assertTrue(summary.endsWith("…"))
    }

    /** 指纹标签在无连续天数与有连续天数时都能生成说明。 */
    @Test
    fun fingerprintLabelIncludesStreakAndAverage() {
        val zero = WereadWidgetData.fingerprintLabel(0, 0)
        assertTrue(zero.isNotBlank())
        val text = WereadWidgetData.fingerprintLabel(7, 45)
        assertTrue(text.contains("7"))
        assertTrue(text.contains("45"))
    }

    /**
     * 整体卡片的五种形态按顺序循环切换。
     *
     * 原月度热力组件的双视图切换随组件删除迁到整体卡片，形态数量与首尾衔接在此校验。
     */
    @Test
    fun boardFormsCycleThroughAllForms() {
        assertEquals(5, WereadBoardForm.entries.size)
        assertEquals(WereadBoardForm.MONTH, WereadBoardForm.WEEK.next())
        assertEquals(WereadBoardForm.WEEK, WereadBoardForm.TODO.next())
        assertEquals(WereadBoardForm.entries.toList(), WereadBoardForm.entries.map { it })
    }
}
