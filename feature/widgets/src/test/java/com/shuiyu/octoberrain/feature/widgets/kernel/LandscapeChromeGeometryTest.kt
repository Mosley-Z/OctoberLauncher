package com.shuiyu.octoberrain.feature.widgets.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeChromeGeometryTest {
    @Test fun refreshCoversIconAndAllUpdatedRowsExceptForLocalTodos() {
        listOf(368 to 180, 464 to 420, 640 to 360).forEach { (w, h) ->
            listOf("week", "month", "note").forEach { form ->
                val g = LandscapeCardGeometry.compute(w, h, form != "note")
                val zone = checkNotNull(landscapeRefreshZone(form, g))
                assertTrue(zone.contains(g.refreshInk.centerX, g.refreshInk.centerY))
                assertTrue(zone.contains(g.chrome.updatedInk.centerX, g.chrome.updatedInk.bottom - 1f))
                assertTrue(zone.top >= g.title.bottom)
                assertNull(landscapeRefreshZone("todo", g))
            }
        }
    }

    @Test fun leftControlsAndEveryTimestampCharacterFitWithoutMovingTheBody() {
        listOf(368 to 180, 368 to 220, 464 to 420, 640 to 360).forEach { (w, h) ->
            listOf(false, true).forEach { period ->
                val g = LandscapeCardGeometry.compute(w, h, period, minimumTouch = 44f)
                val c = g.chrome
                assertTrue(c.switchTouch.bottom <= c.refreshTouch.top)
                assertTrue(c.switchTouch.height >= 44f && c.refreshTouch.height >= 44f)
                assertTrue(c.updatedInk.top >= c.refreshTouch.bottom)
                assertTrue(c.updatedInk.bottom <= h)
                assertTrue(c.updatedInk.height > 0f)
                val originalTop = (if (period) 18f else 6f) * g.unit
                val originalBody =
                    maxOf(originalTop + 38f * g.unit, originalTop + maxOf(44f * g.unit, 44f)) + 9f * g.unit
                assertEquals(originalBody, g.body.top, .001f)
                assertEquals(g.body.right, g.meta.right, .001f)
            }
        }
    }

    @Test fun timeUsesRealUpdatedLabelAndNeverDegradesToRefreshOnly() {
        assertEquals("更新于12:41", landscapeUpdatedLabel("更新于 12:41"))
        assertEquals("更新于02:03", landscapeUpdatedLabel("同步 2:03"))
        assertEquals("更新于--:--", landscapeUpdatedLabel(null))
        assertEquals("更新于--:--", landscapeUpdatedLabel("刷新"))
        assertEquals("更新于--:--", landscapeUpdatedLabel("25:99"))
    }

    @Test fun hourAndMinuteStayTogetherWithASeparateColonRow() {
        assertEquals(listOf("更", "新", "于", "02", ":", "03"), landscapeUpdatedRows("更新于 2:03"))
        assertEquals(listOf("更", "新", "于", "--", ":", "--"), landscapeUpdatedRows(null))
    }
}
