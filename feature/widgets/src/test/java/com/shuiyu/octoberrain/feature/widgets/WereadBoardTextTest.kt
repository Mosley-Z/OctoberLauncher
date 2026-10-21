package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 笔记折行截断的单元测试。
 *
 * 覆盖超长笔记截断、短笔记原样返回与空文本，以及版式对笔记行数的硬限制。
 */
class WereadBoardTextTest {
    /**
     * 超长笔记只保留两行，末行带省略号。
     */
    @Test
    fun clipsNotesToTwoLines() {
        val lines = limitNoteLines(listOf("第一行", "第二行", "第三行"), 2) { "$it…" }
        assertEquals(listOf("第一行", "第二行…"), lines)
    }

    /**
     * 未超限的笔记原样返回。
     */
    @Test
    fun keepsShortNotesUntouched() {
        val wrapped = listOf("第一行", "第二行")
        assertSame(wrapped, limitNoteLines(wrapped, 2) { "$it…" })
    }

    /**
     * 空文本返回空行。
     */
    @Test
    fun keepsEmptyNotes() {
        assertTrue(limitNoteLines(emptyList(), 2) { it }.isEmpty())
    }

    /**
     * 版式把笔记行数限制在两行以内，包含显式传入更大行数的情况。
     */
    @Test
    fun frameLimitsNoteLinesToTwo() {
        val auto = WereadBoardFrame.of(1200f, 480f, 24f)
        val forced = WereadBoardFrame.of(1200f, 480f, 24f, noteLineCount = 5)
        assertTrue(auto.noteMaxLines in 0..2)
        assertTrue(forced.noteMaxLines in 0..2)
    }
}
