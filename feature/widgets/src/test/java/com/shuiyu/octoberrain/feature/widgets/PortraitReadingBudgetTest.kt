package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitReadingBudgetTest {
    private fun budget(height: Float) = portraitReadingBudget(height, 300f, 160f, 100f, 20f, 24f, 4f)

    private fun bottom(v: PortraitReadingBudget) =
        v.leading + v.coverHeight + 160f * v.textScale + v.statsHeight + 20f + v.statsGap

    @Test fun longScreenAddsOuterWhitespace() {
        val normal = budget(700f)
        val tall = budget(1000f)
        assertEquals(24f, tall.statsGap, .001f)
        assertEquals(normal.coverHeight, tall.coverHeight, .001f)
        assertEquals(normal.leading + 150f, tall.leading, .001f)
        assertEquals(1000f - tall.leading, bottom(tall), .001f)
    }

    @Test fun shortScreenShrinksCoverBeforeText() {
        val v = budget(400f)
        assertEquals(1f, v.textScale, .001f)
        assertEquals(96f, v.coverHeight, .001f)
        assertEquals(400f, bottom(v), .001f)
    }

    @Test fun gapShrinksAfterCoverDisappears() {
        val v = budget(290f)
        assertEquals(0f, v.coverHeight, .001f)
        assertEquals(1f, v.textScale, .001f)
        assertEquals(10f, v.statsGap, .001f)
    }

    @Test fun smallViewportScalesTextAsLastResort() {
        for (height in listOf(80f, 160f, 240f, 290f, 400f, 700f, 1000f)) {
            val v = budget(height)
            assertTrue(v.coverHeight in 0f..300f)
            assertTrue(v.leading >= 0f)
            assertTrue(v.statsGap in 4f..24f)
            assertTrue(bottom(v) <= height + .001f)
        }
        assertTrue(budget(240f).textScale < 1f)
    }
}
