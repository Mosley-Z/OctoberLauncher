package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.widget.noteChapterLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMetadataTest {
    @Test fun `a repeated book title is not presented as another chapter label`() {
        assertEquals("", noteChapterLabel("Book", " Book "))
        assertEquals("", noteChapterLabel("Book", null))
        assertEquals("Chapter 2", noteChapterLabel("Book", "Chapter 2"))
    }
}
