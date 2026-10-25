package com.shuiyu.octoberrain.feature.widgets.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapePeriodGeometryTest {
    @Test
    fun `week chart absorbs height while header font unit and footer offset stay fixed`() {
        val short = LandscapeCardGeometry.compute(464, 340, period = true)
        val tall = LandscapeCardGeometry.compute(464, 500, period = true)
        val a = LandscapePeriodGeometry.compute(short, false, 1, 3)
        val b = LandscapePeriodGeometry.compute(tall, false, 1, 3)
        assertTrue(a.chartVisible && b.chartVisible)
        assertEquals(short.unit, tall.unit, .001f)
        assertEquals(short.meta, tall.meta)
        assertEquals(a.totalTop, b.totalTop, .001f)
        assertEquals(a.chartTop, b.chartTop, .001f)
        assertEquals(160f, b.weekHeight - a.weekHeight, .001f)
        assertEquals(12f * short.unit, short.height - a.summaryBottom, .001f)
        assertEquals(12f * tall.unit, tall.height - b.summaryBottom, .001f)
        assertEquals(a.chartBottom - 24f * short.unit, a.chartTop + a.weekHeight, .001f)
    }

    @Test
    fun `four five and six calendar rows fill budget without changing date font unit`() {
        val g = LandscapeCardGeometry.compute(464, 420, period = true)
        val layouts = (4..6).map { LandscapePeriodGeometry.compute(g, true, it, 3) }
        layouts.forEach { p ->
            assertTrue(p.totalVisible && p.chartVisible)
            assertEquals(p.calendarRows, p.visibleCalendarRows)
            assertTrue(p.cellHeight >= 16f * g.unit)
            val lastBottom =
                p.chartTop + 22f * g.unit + p.calendarRows * p.cellHeight +
                    (p.calendarRows - 1) * p.cellGap
            assertEquals(p.chartBottom, lastBottom, .001f)
            assertEquals(10f * g.unit, p.summaryTop - lastBottom, .001f)
        }
        assertTrue(layouts[0].cellHeight > layouts[1].cellHeight)
        assertTrue(layouts[1].cellHeight > layouts[2].cellHeight)
    }

    @Test
    fun `footer reserves only real comparison and goal rows`() {
        val g = LandscapeCardGeometry.compute(464, 420, period = true)
        val one = LandscapePeriodGeometry.compute(g, true, 6, 1)
        val two = LandscapePeriodGeometry.compute(g, true, 6, 2)
        val three = LandscapePeriodGeometry.compute(g, true, 6, 3)
        assertEquals(24f * g.unit, one.summaryTop - two.summaryTop, .001f)
        assertEquals(24f * g.unit, two.summaryTop - three.summaryTop, .001f)
        assertEquals(one.summaryBottom, three.summaryBottom, .001f)
        assertTrue(one.cellHeight > two.cellHeight && two.cellHeight > three.cellHeight)
        assertTrue(three.chartBottom < three.summaryTop)
    }

    @Test
    fun `short slot omits only rows that cannot fit and never scales the card unit`() {
        val g = LandscapeCardGeometry.compute(368, 180, period = true)
        val p = LandscapePeriodGeometry.compute(g, true, 6, 3)
        assertTrue(p.totalVisible)
        assertTrue(p.visibleCalendarRows < 6)
        assertEquals(0, p.visibleCalendarRows)
        assertFalse(p.chartVisible)
        assertTrue(p.totalTop + p.totalLineHeight <= p.summaryTop)
        assertTrue(p.summaryTop >= p.ruleY)
        assertEquals(g.height - 12f * g.unit, p.summaryBottom, .001f)
        assertTrue(p.minimumCompleteHeight > g.height)
    }

    @Test
    fun `partial calendar has complete cells and leaves footer untouched`() {
        val g = LandscapeCardGeometry.compute(464, 280, period = true)
        val p = LandscapePeriodGeometry.compute(g, true, 6, 3)
        assertTrue(p.chartVisible)
        assertTrue(p.visibleCalendarRows in 1..5)
        assertTrue(p.cellHeight >= 16f * g.unit)
        val bottom =
            p.chartTop + 22f * g.unit + p.visibleCalendarRows * p.cellHeight +
                (p.visibleCalendarRows - 1) * p.cellGap
        assertTrue(bottom <= p.summaryTop - 10f * g.unit + .001f)
    }

    @Test
    fun `below minimum content height keeps header and summary priority without drawing over them`() {
        val g = LandscapeCardGeometry.compute(640, 240, period = true)
        val p = LandscapePeriodGeometry.compute(g, false, 1, 3)
        assertTrue(p.minimumContentHeight > g.height)
        assertFalse(p.totalVisible)
        assertFalse(p.chartVisible)
        assertTrue(p.summaryTop >= p.ruleY)
        assertEquals(g.height - 12f * g.unit, p.summaryBottom, .001f)
    }
}
