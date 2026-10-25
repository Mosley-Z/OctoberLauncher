package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.widget.WidgetNoteText
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetNoteTextTest {
    @Test
    fun emojiAtNarrowLineMovesAsAWholeCharacter() {
        val text = "\uD83D\uDCD6下一句"
        assertEquals(0, WidgetNoteText.fitLength(text, 1))
        assertEquals(2, WidgetNoteText.fitLength(text, 0, wrap = true))
        assertEquals(2, WidgetNoteText.fitLength(text, 1, wrap = true))
    }

    @Test
    fun combiningAccentStaysWithItsLetterAtTheInlineTail() {
        val text = "e\u0301后续"
        assertEquals(0, WidgetNoteText.fitLength(text, 1))
        assertEquals(2, WidgetNoteText.fitLength(text, 1, wrap = true))
        assertEquals("前文", WidgetNoteText.dropLast("前文e\u0301"))
    }

    @Test
    fun emptyAndFullPassagesRetainTheirContent() {
        assertEquals(0, WidgetNoteText.fitLength("", 0, wrap = true))
        assertEquals("", WidgetNoteText.dropLast(""))
        assertEquals(3, WidgetNoteText.fitLength("完整句", 3))
        assertEquals("前文", WidgetNoteText.dropLast("前文\uD83D\uDCD6"))
    }
}
