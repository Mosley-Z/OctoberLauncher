package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersivePortraitBalanceTest {
    @Test fun compactAndLandscapeRemainUnchanged() {
        for ((w, h) in listOf(552f to 768f, 600f to 800f, 1648f to 824f)) {
            assertEquals(0f, immersivePortraitBalance(w, h), 0f)
        }
    }

    @Test fun proportionalWindowsHaveIdenticalWeights() {
        assertEquals(1f, immersivePortraitBalance(824f, 1648f), .001f)
        assertEquals(immersivePortraitBalance(400f, 720f), immersivePortraitBalance(800f, 1440f), .001f)
    }

    @Test fun allIntermediateRatiosAreBoundedAndContinuous() {
        var previous = 0f
        for (height in 500..1400) {
            val value = immersivePortraitBalance(500f, height.toFloat())
            assertTrue(value in previous..1f)
            assertTrue(value - previous < .01f)
            previous = value
        }
    }
}
