package com.shuiyu.octoberrain.feature.widgets.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design and interaction invariants, independent of Android Canvas and cached data. */
class LandscapeCardGeometryTest {
    @Test
    fun controlsKeepSeparateUsableTargetsAcrossLandscapeSlots() {
        listOf(368 to 220, 368 to 340, 464 to 420, 640 to 360).forEach { (width, height) ->
            listOf(false, true).forEach { period ->
                val g = LandscapeCardGeometry.compute(width, height, period, minimumTouch = 44f)
                val controls = listOf(g.title, g.refresh) + g.footer
                controls.forEach { rect ->
                    assertTrue(rect.width >= 44f)
                    assertTrue(rect.height >= 44f)
                    assertTrue(rect.left >= 0f && rect.top >= 0f)
                    assertTrue(rect.right <= width && rect.bottom <= height)
                    assertEquals(1, controls.count { it.contains(rect.centerX, rect.centerY) })
                }
                controls.forEachIndexed { index, first ->
                    controls.drop(index + 1).forEach { second -> assertFalse(overlaps(first, second)) }
                }
                assertTrue(g.body.left >= g.title.right)
                assertTrue(g.body.bottom < g.footer.first().top)
                assertTrue(g.bookmark.left < g.title.right)
                assertTrue(g.bookmark.bottom < height)
            }
        }
    }

    @Test
    fun approvedNoteKeepsTwoEqualButtonsPinnedWhileContentHeightChanges() {
        val short = LandscapeCardGeometry.compute(464, 260)
        val tall = LandscapeCardGeometry.compute(464, 460)
        listOf(short, tall).forEach { g ->
            assertEquals(2, g.footer.size)
            assertEquals(g.footer[0].width, g.footer[1].width, .001f)
            assertEquals(g.height - 8f * g.unit, g.footer.last().bottom, .001f)
            assertTrue(g.bookmark.height <= 80.01f * g.unit)
            assertFalse(g.title.contains(g.title.right, g.title.centerY))
            assertFalse(g.refresh.contains(g.refresh.centerX, g.refresh.bottom))
        }
        assertEquals(200f, tall.body.height - short.body.height, .001f)
    }

    @Test
    fun undatedTasksUseLessSpaceAndScrolledRowsCannotHitTheHeader() {
        val plain = LandscapeTodoGeometry.rowHeight(1, false, 1f)
        val dated = LandscapeTodoGeometry.rowHeight(1, true, 1f)
        val multiline = LandscapeTodoGeometry.rowHeight(3, false, 1f)
        assertEquals(24f, dated - plain, .001f)
        assertTrue(multiline > plain)
        val viewport = LandscapeRect(62f, 60f, 450f, 300f)
        val first = checkNotNull(LandscapeTodoGeometry.visibleRow(viewport, 30f, plain))
        val next = checkNotNull(LandscapeTodoGeometry.visibleRow(viewport, 30f + plain, dated))
        assertEquals(viewport.top, first.top, .001f)
        assertFalse(first.contains(first.centerX, 59f))
        assertFalse(overlaps(first, next))
        assertNull(LandscapeTodoGeometry.visibleRow(viewport, 301f, dated))
        assertNull(LandscapeTodoGeometry.visibleRow(viewport, -100f, plain))
    }

    private fun overlaps(
        first: LandscapeRect,
        second: LandscapeRect,
    ): Boolean =
        first.left < second.right &&
            second.left < first.right &&
            first.top < second.bottom &&
            second.top < first.bottom
}
