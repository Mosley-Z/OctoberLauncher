package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopPageConfigTest {
    @Test fun `device defaults and per direction overrides preserve legacy manual values`() {
        val automatic = DesktopConfig()
        assertEquals(1, automatic.firstGridRows(false, "P4"))
        assertEquals(2, automatic.firstGridRows(false, "S4"))
        assertEquals(3, automatic.firstGridRows(true, "P4"))
        assertEquals(3, automatic.firstGridRows(true, "S4"))
        assertEquals(3, automatic.firstGridRows(true))
        assertEquals(2, automatic.firstGridColumns(true))
        val legacy = automatic.copy(firstPageRows = 4, firstPageColumns = 2)
        val edited = legacy.withFirstGrid(false, 0, 0)
        assertEquals(1, edited.firstGridRows(false, "P4"))
        assertEquals(4, edited.firstGridRows(true, "P4"))
        assertEquals(3, edited.firstGridColumns(false))
        assertEquals(2, edited.firstGridColumns(true))
        assertNull(edited.landscapeFirstPageRows)
    }

    @Test fun `page image changes survive round trip and never replace home or sibling options`() {
        val home = DesktopConfig(widgetId = "image", widgetImagePath = "home.png", imageSwipeDirection = 3)
        val added = home.editWidgetPage(1) { it.copy(widgetId = "image") }
        val edited =
            added
                .editWidgetPage(1) { it.copy(widgetImagePath = "page.png") }
                .editWidgetPage(2) { it.copy(widgetId = "clock_e") }
        val restored = Json.decodeFromString<DesktopConfig>(Json.encodeToString(edited))
        assertEquals("home.png", restored.widgetImagePath)
        assertEquals("page.png", restored.forWidgetPage(1).widgetImagePath)
        assertEquals(3, restored.forWidgetPage(1).imageSwipeDirection)
        assertEquals("clock_e", restored.forWidgetPage(2).widgetId)
        val closed = restored.editWidgetPage(1) { it.copy(widgetId = "") }
        assertEquals("", closed.forWidgetPage(1).widgetId)
        assertEquals("image", closed.widgetId)
        assertEquals("clock_e", closed.forWidgetPage(2).widgetId)
    }
}
