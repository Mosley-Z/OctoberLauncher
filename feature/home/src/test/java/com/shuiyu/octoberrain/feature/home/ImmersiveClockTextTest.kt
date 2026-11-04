package com.shuiyu.octoberrain.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ImmersiveClockTextTest {
    @Test
    fun portraitLandscapeAndMeasurementTextUseTheSelectedFormat() {
        val calendar = Calendar.getInstance().apply { clear() }
        listOf(0 to "00:05", 9 to "09:05", 12 to "12:05", 13 to "01:05", 23 to "11:05").forEach { (hour, text) ->
            calendar.set(2026, Calendar.NOVEMBER, 2, hour, 5)
            assertEquals(text, immersiveClockText(calendar.time, true))
            assertEquals("${hour.toString().padStart(2, '0')}:05", immersiveClockText(calendar.time, false))
        }
    }
}
