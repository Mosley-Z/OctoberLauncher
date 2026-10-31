package com.shuiyu.octoberrain.feature.content.ui

import com.shuiyu.octoberrain.core.common.WereadNoteSnapshot
import com.shuiyu.octoberrain.core.common.WereadSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 微信读书页面纯逻辑的单元测试。
 *
 * 覆盖分段名称、分段可见性、空态文案、笔记分组、环比文案与进度文案；
 * 不依赖 Android 上下文。
 */
class WereadSectionsTest {
    /** 四个分段名称按页签顺序返回。 */
    @Test
    fun `分段名称与顺序`() {
        assertEquals(
            listOf("总览", "笔记", "账单", "排行"),
            WereadSectionLogic.SECTIONS.map { WereadSectionLogic.label(it) },
        )
        assertEquals(
            listOf(
                WereadSection.OVERVIEW,
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
        assertEquals(4, WereadSectionLogic.visibleSections(configured = true, enabled = true).size)
        assertTrue(WereadSectionLogic.visibleSections(configured = false, enabled = true).isEmpty())
        assertTrue(WereadSectionLogic.visibleSections(configured = true, enabled = false).isEmpty())
        assertTrue(WereadSectionLogic.visibleSections(configured = false, enabled = false).isEmpty())
    }

    /** 每个分段都有非空提示文案。 */
    @Test
    fun `空态文案`() {
        WereadSectionLogic.SECTIONS.forEach { section ->
            assertTrue(WereadSectionLogic.emptyMessage(section).isNotBlank())
        }
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
        assertEquals("进度未知", WereadSectionLogic.progressText(-0.2f))
        assertEquals("已读 45%", WereadSectionLogic.progressText(0.456f))
        assertEquals("已读 100%", WereadSectionLogic.progressText(1.6f))
    }

    private fun note(
        bookTitle: String,
        text: String,
    ): WereadNoteSnapshot = WereadNoteSnapshot(bookTitle = bookTitle, text = text)
}
