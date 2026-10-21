package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

/** 桌面卡片的循环与列表共享启用池，旧配置序号可恢复。 */
class WereadBoardFormTest {
    @Test
    fun poolFiltersInOriginalOrder() {
        assertEquals(
            listOf(WereadBoardForm.MONTH, WereadBoardForm.NOTE),
            WereadBoardForm.pool(0b01010),
        )
    }

    @Test
    fun emptyOrUnknownPoolFallsBackToAllForms() {
        assertEquals(WereadBoardForm.entries.toList(), WereadBoardForm.pool(0))
        assertEquals(WereadBoardForm.entries.toList(), WereadBoardForm.pool(0b100000))
    }

    @Test
    fun cycleSkipsDisabledFormsAndWraps() {
        val pool = WereadBoardForm.pool(0b01010)
        assertEquals(WereadBoardForm.NOTE, WereadBoardForm.MONTH.nextIn(pool))
        assertEquals(WereadBoardForm.MONTH, WereadBoardForm.NOTE.nextIn(pool))
        assertEquals(WereadBoardForm.MONTH, WereadBoardForm.BOOK.nextIn(pool))
    }

    @Test
    fun singleEnabledFormRemainsSelected() {
        val pool = WereadBoardForm.pool(0b00100)
        assertEquals(WereadBoardForm.BOOK, WereadBoardForm.BOOK.nextIn(pool))
    }

    @Test
    fun persistedOrdinalRestoresEachSelectedForm() {
        WereadBoardForm.entries.forEach { assertEquals(it, WereadBoardForm.of(it.ordinal)) }
        assertEquals(WereadBoardForm.WEEK, WereadBoardForm.of(null))
        assertEquals(WereadBoardForm.WEEK, WereadBoardForm.of(-1))
    }
}
