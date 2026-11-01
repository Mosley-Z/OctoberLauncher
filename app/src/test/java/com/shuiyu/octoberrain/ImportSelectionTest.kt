package com.shuiyu.octoberrain

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSelectionTest {
    @Test fun clipsComeFirstAndDataIsAppendedOnlyOnce() {
        assertEquals(listOf("b", "a", "c"), orderedImportSelection(listOf("b", "a", "b"), "c"))
        assertEquals(listOf("b", "a"), orderedImportSelection(listOf("b", "a"), "b"))
        assertEquals(listOf("single"), orderedImportSelection(emptyList(), "single"))
        assertEquals(emptyList<String>(), orderedImportSelection<String>(emptyList(), null))
    }
}
