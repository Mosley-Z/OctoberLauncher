package com.shuiyu.octoberrain.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeResumeRefreshTest {
    private val day = LocalDate.of(2026, 10, 29)

    @Test fun `returning from settings with unchanged data does not force reload`() {
        val refresh = HomeResumeRefresh(day)
        assertFalse(refresh.resume(true, day))
        assertFalse(refresh.resume(true, day))
    }

    @Test fun `background data changes merge into exactly one forced refresh`() {
        val refresh = HomeResumeRefresh(day)
        refresh.invalidate()
        refresh.invalidate()
        assertTrue(refresh.resume(true, day))
        assertFalse(refresh.resume(true, day))
    }

    @Test fun `crossing midnight refreshes once even without a content event`() {
        val refresh = HomeResumeRefresh(day)
        assertTrue(refresh.resume(true, day.plusDays(1)))
        assertFalse(refresh.resume(true, day.plusDays(1)))
    }

    @Test fun `a later background update is never lost after a completed resume`() {
        val refresh = HomeResumeRefresh(day)
        assertFalse(refresh.resume(true, day))
        refresh.invalidate()
        assertTrue(refresh.resume(true, day))
        refresh.invalidate()
        assertTrue(refresh.resume(true, day.plusDays(1)))
        assertFalse(refresh.resume(true, day.plusDays(1)))
    }

    @Test fun `other page returns retain reload for local changes without version notifications`() {
        val refresh = HomeResumeRefresh(day)
        assertTrue(refresh.resume(false, day))
        assertTrue(refresh.resume(false, day))
        assertFalse(refresh.resume(true, day))
    }
}
