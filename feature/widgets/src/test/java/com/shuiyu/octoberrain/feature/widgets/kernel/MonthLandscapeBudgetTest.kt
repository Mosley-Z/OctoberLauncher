package com.shuiyu.octoberrain.feature.widgets.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthLandscapeBudgetTest {
    @Test
    fun `all month rows stay above measured time and wrapping footer across slot sizes`() {
        for (height in listOf(300f, 340f, 420f)) {
            for (width in listOf(230f, 360f, 600f)) {
                for ((rows, footer) in (4..6).flatMap { row -> listOf(18f, 40f, 62f).map { row to it } }) {
                    val budget = MonthLandscapeBudget.compute(60f, height - 20f, width, rows, 24f, 34f, footer, 1f)
                    assertTrue(budget.cell > 0f)
                    val gridBottom = budget.chartTop + 24f + rows * budget.cell + (rows - 1) * budget.gap
                    assertTrue(gridBottom <= budget.totalTop - 12f + .001f)
                    assertTrue(budget.totalTop + 34f <= budget.summaryTop - 8f + .001f)
                    assertEquals(height - 20f, budget.summaryTop + footer, .001f)
                    assertTrue(7 * budget.cell + 6 * budget.gap <= width + .001f)
                }
            }
        }
    }

    @Test
    fun `heat tiers keep exact portrait coverage and black border for odd and even cells`() {
        for (size in listOf(19, 20, 27, 28)) {
            for (level in 0..4) {
                val pixels = MonthHeatPattern.pixels(size, level)
                val inner = size - 2
                val black =
                    (1 until size - 1).sumOf { y ->
                        (1 until size - 1).count { x -> pixels[y * size + x] == -16777216 }
                    }
                assertEquals(Math.round(inner * inner * level / 4f), black)
                assertTrue(pixels.all { it == -1 || it == -16777216 })
                assertTrue((0 until size).all { pixels[it] == -16777216 && pixels[it * size] == -16777216 })
            }
        }
    }
}
