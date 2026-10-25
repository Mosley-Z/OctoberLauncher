package com.shuiyu.octoberrain.core.device

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplaySafeAreaTest {
    @Test fun `only the identified P4 receives physical cover compensation`() {
        assertEquals(12, DisplaySafeArea.physicalSideInset("Eink", "P4"))
        assertEquals(0, DisplaySafeArea.physicalSideInset("XTEINK", "S4"))
        assertEquals(0, DisplaySafeArea.physicalSideInset("Other", "P4"))
    }

    @Test fun `physical sides become top and bottom in either landscape rotation`() {
        assertEquals(DisplaySafeArea.Insets(12, 0, 12, 0), DisplaySafeArea.insets(12, 552, 768))
        assertEquals(DisplaySafeArea.Insets(0, 12, 0, 12), DisplaySafeArea.insets(12, 768, 552))
        assertEquals(DisplaySafeArea.Insets(0, 0, 0, 0), DisplaySafeArea.insets(0, 800, 480))
    }
}
