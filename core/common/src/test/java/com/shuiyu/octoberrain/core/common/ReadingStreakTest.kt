package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReadingStreakTest {
    @Test fun `today joins only at sixty seconds and yesterday remains visible before that`() {
        val today = LocalDate.of(2026, 10, 25)
        val days = mapOf(today to 59L, today.minusDays(1) to 61L, today.minusDays(2) to 60L, today.minusDays(3) to 59L)
        assertEquals(2, continuousReadingDays(days, today))
        assertEquals(3, continuousReadingDays(days + (today to 60L), today))
        assertEquals(0, continuousReadingDays(days + (today.minusDays(1) to 0L), today))
    }

    @Test fun `missing history is unknown instead of a false break and long streaks cross years`() {
        val today = LocalDate.of(2026, 1, 10)
        val days = (0L..70L).associate { today.minusDays(it) to 60L }
        assertNull(continuousReadingDays(days, today))
        assertEquals(71, continuousReadingDays(days + (today.minusDays(71) to 0L), today))
    }
}
