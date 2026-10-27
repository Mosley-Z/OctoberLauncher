package com.shuiyu.octoberrain.core.wereadsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticSyncBudgetTest {
    @Test fun `request limit stops further network attempts`() {
        val budget = AutomaticSyncBudget({ true }, { 0L })
        repeat(12) { assertEquals(5000, budget.nextTimeoutMillis()) }
        assertNull(budget.nextTimeoutMillis())
        assertTrue(budget.stopped)
    }

    @Test fun `remaining time limits socket timeout then stops`() {
        var now = 0L
        val budget = AutomaticSyncBudget({ true }, { now })
        now = 18_000L
        assertEquals(1000, budget.nextTimeoutMillis())
        now = 20_000L
        assertNull(budget.nextTimeoutMillis())
    }

    @Test fun `background pauses batch permanently even after returning`() {
        var active = true
        val budget = AutomaticSyncBudget({ active }, { 0L })
        assertEquals(5000, budget.nextTimeoutMillis())
        active = false
        assertNull(budget.nextTimeoutMillis())
        active = true
        assertNull(budget.nextTimeoutMillis())
    }
}
