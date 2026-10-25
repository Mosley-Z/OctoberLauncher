package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.core.device.DeviceProfile
import com.shuiyu.octoberrain.core.device.DisplayClass
import com.shuiyu.octoberrain.core.device.HeightClass
import com.shuiyu.octoberrain.core.device.SizeClass
import com.shuiyu.octoberrain.feature.files.ui.FileMetrics
import com.shuiyu.octoberrain.feature.files.ui.FileTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2MetricsTest {
    @Test fun previewsDominateInBothOrientationsAndCellsKeepTheirLabelsClickable() {
        listOf(480 to 753, 800 to 432, 552 to 720, 768 to 505, 824 to 1600).forEach { (w, h) ->
            val m = metricsOf(w, h, 219)
            assertEquals(if (w > h) 5 else 3, m.gridColumns)
            assertEquals(m.gridColumns * m.gridRows, m.pageSize)
            val cells = (0 until m.pageSize).map { m.gridCell(it) }
            cells.forEachIndexed { index, c ->
                assertEquals(c.thumbnail.width, c.thumbnail.height)
                assertTrue(c.hit.bottom <= m.contentBottom)
                assertTrue(c.hit.contains(c.label.right - 1, c.label.bottom - 1))
                assertTrue(c.hit.contains(c.hit.left, c.thumbnail.top))
                assertFalse(c.hit.contains(c.hit.right, c.hit.top))
                assertEquals(listOf(index), cells.indices.filter { cells[it].hit.contains(c.label.left, c.label.top) })
            }
        }
    }

    @Test fun s4LandscapeHasTwoLargeRowsRatherThanSixteenTinyPreviews() {
        val m = metricsOf(800, 432, 219)
        assertEquals(5, m.gridColumns)
        assertEquals(2, m.gridRows)
        assertEquals(10, m.pageSize)
        assertTrue(m.gridCellDrawH > 100)
    }

    private fun metricsOf(
        width: Int,
        height: Int,
        dpi: Int,
    ): FileMetrics = FileMetrics(width, height, tokensOf(width, height, dpi))

    private fun tokensOf(
        width: Int,
        height: Int,
        dpi: Int,
    ): FileTokens = FileTokens(profileOf(width, height, dpi))

    private fun profileOf(
        width: Int,
        height: Int,
        dpi: Int,
    ): DeviceProfile {
        val density = dpi / 160f
        val widthDp = (width / density).toInt()
        val heightDp = (height / density).toInt()
        return DeviceProfile(
            widthPx = width,
            heightPx = height,
            widthDp = widthDp,
            heightDp = heightDp,
            density = density,
            sizeClass = SizeClass.fromWidth(widthDp),
            heightClass = HeightClass.fromHeight(heightDp),
            display = DisplayClass.EinkFast,
            hasFrontLight = true,
            statusBarInsetPx = 0,
            navigationInsetPx = 0,
            longEdgePx = maxOf(width, height),
            shortEdgePx = minOf(width, height),
        )
    }
}
