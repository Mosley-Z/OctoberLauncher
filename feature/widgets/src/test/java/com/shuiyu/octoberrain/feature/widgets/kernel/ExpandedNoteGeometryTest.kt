package com.shuiyu.octoberrain.feature.widgets.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedNoteGeometryTest {
    @Test fun `expanding a card widens paper without scaling typography or header anchors`() {
        val normal = LandscapeCardGeometry.compute(440, 440)
        val expanded = LandscapeCardGeometry.compute(720, 440, referenceWidth = 440)
        assertEquals(normal.unit, expanded.unit, .001f)
        assertEquals(normal.body.left, expanded.body.left, .001f)
        assertEquals(normal.body.top, expanded.body.top, .001f)
        assertEquals(normal.footer.first().top, expanded.footer.first().top, .001f)
        assertTrue(expanded.body.width > normal.body.width)
    }
}
