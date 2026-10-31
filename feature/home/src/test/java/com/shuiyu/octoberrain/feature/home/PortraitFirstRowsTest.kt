package com.shuiyu.octoberrain.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PortraitFirstRowsTest {
    @Test
    fun preservesWidgetBudgetAndSmallWindows() {
        assertEquals(2, portraitFirstRows(600, 100, 8, 24, 150, 2))
        assertEquals(3, portraitFirstRows(700, 100, 8, 24, 150, 2))
        assertEquals(2, portraitFirstRows(700, 100, 8, 24, 400, 2))
        assertEquals(1, portraitFirstRows(250, 100, 8, 24, 150, 1))
        assertEquals(2, portraitFirstRows(700, 140, 8, 24, 150, 2))
    }

    @Test
    fun boundaryAndDensityDoNotChangeTheBudget() {
        for (density in listOf(1, 2, 3)) {
            assertEquals(
                2,
                portraitFirstRows(
                    679 * density,
                    100 * density,
                    8 * density,
                    24 * density,
                    150 * density,
                    2,
                ),
            )
            assertEquals(
                3,
                portraitFirstRows(
                    680 * density,
                    100 * density,
                    8 * density,
                    24 * density,
                    150 * density,
                    2,
                ),
            )
        }
    }
}
