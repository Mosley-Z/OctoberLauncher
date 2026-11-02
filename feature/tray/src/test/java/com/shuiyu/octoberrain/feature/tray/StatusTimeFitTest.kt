package com.shuiyu.octoberrain.feature.tray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTimeFitTest {
    @Test
    fun preservesPreferredSlotAndConsumesGapBeforeScaling() {
        assertEquals(StatusTimeFit(100, 1f), statusTimeFit(150f, 100, 240))
        assertEquals(StatusTimeFit(80, 1f), statusTimeFit(70f, 100, 80))
    }

    @Test
    fun completeTimeFitsAfterReservingBatteryChargingAndRefreshControls() {
        val rowWidth = 280
        val reserved = 32 + 40 + 4 + 60 + 18 + 2 + 3 + 24
        val available = rowWidth - reserved
        val naturalTimeWidth = 160f // Includes the trailing pm.
        val fit = statusTimeFit(naturalTimeWidth, 100, available)
        assertEquals(available, fit.minimumWidth)
        assertTrue(fit.scaleX < 1f)
        assertEquals(available.toFloat(), naturalTimeWidth * fit.scaleX, .001f)
        assertTrue(reserved + naturalTimeWidth * fit.scaleX <= rowWidth)
    }

    @Test
    fun wideningRestoresFullWidthAndZeroSpaceNeverProducesNegativeValues() {
        assertEquals(StatusTimeFit(0, 0f), statusTimeFit(160f, 100, -4))
        assertEquals(StatusTimeFit(100, 1f), statusTimeFit(160f, 100, 300))
        assertEquals(StatusTimeFit(0, 1f), statusTimeFit(0f, 100, 0))
    }
}
