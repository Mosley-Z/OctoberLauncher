package com.shuiyu.octoberrain.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRefreshTest {
    @Test
    fun onlyTurnsCountAndGlobalCompletionRestartsBudget() {
        val refresh = ReaderRefresh()
        repeat(5) { assertFalse(refresh.needsFull(6, 0, true)) }
        repeat(20) { assertFalse(refresh.needsFull(6, 0, false)) }
        assertTrue(refresh.needsFull(6, 0, true))
        repeat(4) { assertFalse(refresh.needsFull(6, 0, true)) }
        repeat(5) { assertFalse(refresh.needsFull(6, 1, true)) }
        assertTrue(refresh.needsFull(6, 1, true))
    }

    @Test
    fun disabledAndChangedSettingsStartFresh() {
        val refresh = ReaderRefresh()
        repeat(200) { assertFalse(refresh.needsFull(0, 0, true)) }
        repeat(5) { assertFalse(refresh.needsFull(6, 0, true)) }
        refresh.reset()
        repeat(11) { assertFalse(refresh.needsFull(12, 0, true)) }
        assertTrue(refresh.needsFull(12, 0, true))
    }
}
