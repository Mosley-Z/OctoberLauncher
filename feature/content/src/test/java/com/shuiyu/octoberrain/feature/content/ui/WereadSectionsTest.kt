package com.shuiyu.octoberrain.feature.content.ui

import com.shuiyu.octoberrain.core.common.WereadNoteSnapshot
import com.shuiyu.octoberrain.core.common.WereadSection
import com.shuiyu.octoberrain.core.common.WereadStatsPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 微信读书页面纯逻辑的单元测试。
 *
 * 覆盖分段与周期名称、分段可见性、空态文案、笔记分组、热力等级、月历排布、
 * 环比文案与进度文案；不依赖 Android 上下文。
 */
class WereadSectionsTest {
    /** 五个分段名称按页签顺序返回。 */
    @Test
    fun `分段名称与顺序`() {
        assertEquals(
            listOf("总览", "统计", "笔记", "账单", "排行"),
            WereadSectionLogic.SECTIONS.map { WereadSectionLogic.label(it) },
        )
        assertEquals(
            listOf(
                WereadSection.OVERVIEW,
                WereadSection.STATS,
                WereadSection.NOTES,
                WereadSection.BILL,
                WereadSection.RANK,
            ),
            WereadSectionLogic.SECTIONS,
        )
    }

    /** 密钥与来源同时就绪时显示全部页签，否则隐藏。 */
    @Test
    fun `未配置或关闭来源时隐藏分段`() {
        assertEquals(5, WereadSectionLogic.visibleSections(configured = true, enabled = true).size)
        assertTrue(WereadSectionLogic.visibleSections(configured = false, enabled = true).isEmpty())
        assertTrue(WereadSectionLogic.visibleSections(configured = true, enabled = false).isEmpty())
        assertTrue(WereadSectionLogic.visibleSections(configured = false, enabled = false).isEmpty())
    }

    /** 周期名称与选项顺序一致。 */
    @Test
    fun `周期名称`() {
        assertEquals("本周", WereadSectionLogic.periodLabel(WereadStatsPeriod.WEEKLY))
        assertEquals("本月", WereadSectionLogic.periodLabel(WereadStatsPeriod.MONTHLY))
        assertEquals("本年", WereadSectionLogic.periodLabel(WereadStatsPeriod.ANNUALLY))
        assertEquals("累计", WereadSectionLogic.periodLabel(WereadStatsPeriod.OVERALL))
        assertEquals(4, WereadSectionLogic.PERIODS.size)
    }

    /** 每个分段都有非空提示文案，统计分段包含周期名。 */
    @Test
    fun `空态文案`() {
        WereadSectionLogic.SECTIONS.forEach { section ->
            assertTrue(WereadSectionLogic.emptyMessage(section).isNotBlank())
        }
        assertTrue(
            WereadSectionLogic
                .emptyMessage(WereadSection.STATS, WereadStatsPeriod.ANNUALLY)
                .contains("本年"),
        )
        assertEquals(
            "本周统计暂无数据。",
            WereadSectionLogic.emptyMessage(WereadSection.STATS, WereadStatsPeriod.WEEKLY),
        )
    }

    /** 同名笔记聚合到同一组，书名缺失归入未知书籍。 */
    @Test
    fun `笔记按书分组并处理缺失书名`() {
        val notes =
            listOf(
                note("甲书", "第一段"),
                note("乙书", "第二段"),
                note("", "第三段"),
                note("甲书", "第四段"),
            )
        val groups = WereadSectionLogic.groupNotes(notes)
        assertEquals(listOf("甲书", "乙书", "未知书籍"), groups.map { it.bookTitle })
        val first = groups.first()
        assertEquals(2, first.notes.size)
        assertEquals("第一段", first.notes.first().text)
    }

    /** 分组顺序按书名首次出现的位置保持稳定。 */
    @Test
    fun `笔记分组保持出现顺序`() {
        val groups =
            WereadSectionLogic.groupNotes(
                listOf(note("丙", "a"), note("甲", "b"), note("丙", "c")),
            )
        assertEquals(listOf("丙", "甲"), groups.map { it.bookTitle })
        assertEquals(listOf("a", "c"), groups.first().notes.map { it.text })
    }

    /** 无阅读与峰值等分四档的边界。 */
    @Test
    fun `热力等级按峰值分档`() {
        assertEquals(0, WereadSectionLogic.heatLevel(0, 100))
        assertEquals(0, WereadSectionLogic.heatLevel(-5, 100))
        assertEquals(1, WereadSectionLogic.heatLevel(1, 100))
        assertEquals(1, WereadSectionLogic.heatLevel(24, 100))
        assertEquals(1, WereadSectionLogic.heatLevel(25, 100))
        assertEquals(2, WereadSectionLogic.heatLevel(26, 100))
        assertEquals(3, WereadSectionLogic.heatLevel(51, 100))
        assertEquals(4, WereadSectionLogic.heatLevel(76, 100))
        assertEquals(4, WereadSectionLogic.heatLevel(100, 100))
    }

    /** 峰值缺失或小于 1 时按 1 处理，结果不越界。 */
    @Test
    fun `热力等级峰值下限`() {
        assertEquals(4, WereadSectionLogic.heatLevel(3, 0))
        assertEquals(4, WereadSectionLogic.heatLevel(60, 30))
        assertEquals(1, WereadSectionLogic.heatLevel(1, 30))
    }

    /** 2026 年 10 月 1 日为星期四，月初需要三个空位。 */
    @Test
    fun `月历月初对齐星期一`() {
        val cells = WereadSectionLogic.monthCells(2026, 10, List(31) { 10 })
        assertEquals(3, cells.take(3).count { it.day == 0 })
        assertEquals(1, cells[3].day)
        assertEquals(34, cells.size)
        assertEquals(31, cells.count { it.day > 0 })
    }

    /** 月历日期与分钟数一一对应，缺失日期按 0 处理。 */
    @Test
    fun `月历映射每日分钟数`() {
        val minutes = listOf(0, 30, 0, 60)
        val cells = WereadSectionLogic.monthCells(2026, 2, minutes)
        val firstDay = cells.first { it.day == 1 }
        assertEquals(0, firstDay.minutes)
        assertEquals(60, cells.first { it.day == 4 }.minutes)
        assertEquals(4, cells.first { it.day == 4 }.level)
        assertEquals(0, cells.first { it.day == 28 }.minutes)
    }

    /** 单日统计文案包含年月日与时长。 */
    @Test
    fun `单日统计文案`() {
        val text = WereadSectionLogic.daySummary(2026, 10, 20, 95)
        assertTrue(text.contains("2026 年 10 月 20 日"))
        assertTrue(text.contains("1 小时 35 分钟"))
        assertEquals("2026 年 10 月 20 日阅读 0 分钟", WereadSectionLogic.daySummary(2026, 10, 20, 0))
    }

    /** 环比文案区分增加、减少与持平。 */
    @Test
    fun `环比文案`() {
        assertTrue(WereadSectionLogic.deltaText(120, 60).startsWith("比上月多"))
        assertTrue(WereadSectionLogic.deltaText(30, 90).startsWith("比上月少"))
        assertEquals("与上月持平", WereadSectionLogic.deltaText(60, 60))
    }

    /** 进度超出 0 到 1 时夹取后显示百分比。 */
    @Test
    fun `进度文案夹取`() {
        assertEquals("已读 0%", WereadSectionLogic.progressText(-0.2f))
        assertEquals("已读 45%", WereadSectionLogic.progressText(0.456f))
        assertEquals("已读 100%", WereadSectionLogic.progressText(1.6f))
    }

    /** 星期标签自周一开始，共七个。 */
    @Test
    fun `星期标签顺序`() {
        assertEquals(listOf("一", "二", "三", "四", "五", "六", "日"), WereadSectionLogic.WEEKDAYS)
    }

    private fun note(
        bookTitle: String,
        text: String,
    ): WereadNoteSnapshot = WereadNoteSnapshot(bookTitle = bookTitle, text = text)
}
