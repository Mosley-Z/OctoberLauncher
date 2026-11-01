package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveStatsTypographyTest {
    @Test
    fun keepsCaptionAtOrBelow90Dp() {
        assertFalse(immersiveStatsTypographyFits(89.9f, 200f, 180f, 100f, 80f))
        assertFalse(immersiveStatsTypographyFits(90f, 200f, 180f, 100f, 80f))
        assertTrue(immersiveStatsTypographyFits(90.1f, 200f, 180.2f, 100f, 80f))
    }

    @Test
    fun acceptsTargetTypographyIncludingExactFit() {
        assertTrue(immersiveStatsTypographyFits(120f, 240f, 240f, 160f, 100f))
        assertTrue(immersiveStatsTypographyFits(120f, 240f, 240f, 240f, 240f))
    }

    @Test
    fun rejectsEitherTextExceedingCellWidth() {
        assertFalse(immersiveStatsTypographyFits(120f, 240f, 240f, maxOf(241f, 100f), 100f))
        assertFalse(immersiveStatsTypographyFits(120f, 240f, 240f, maxOf(100f, 241f), 100f))
    }

    @Test
    fun rejectsCombinedFontHeightsAndGapExceedingCellHeight() {
        assertFalse(immersiveStatsTypographyFits(120f, 240f, 240f, 160f, 120f + 117f + 4f))
    }
}
