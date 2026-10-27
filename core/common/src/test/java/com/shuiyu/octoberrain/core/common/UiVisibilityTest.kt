package com.shuiyu.octoberrain.core.common

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiVisibilityTest {
    @After fun reset() {
        UiVisibility.setForeground(false)
        UiVisibility.setInteractive(false)
    }

    @Test fun `screen wake cannot activate a background application`() {
        UiVisibility.setForeground(false)
        UiVisibility.setInteractive(true)
        assertTrue(UiVisibility.interactive.value)
        assertFalse(UiVisibility.active.value)
        UiVisibility.setForeground(true)
        assertTrue(UiVisibility.active.value)
        UiVisibility.setInteractive(false)
        assertFalse(UiVisibility.active.value)
        UiVisibility.setInteractive(true)
        assertTrue(UiVisibility.active.value)
        UiVisibility.setForeground(false)
        assertFalse(UiVisibility.active.value)
    }

    @Test fun `resume before screen on stays paused until screen on`() {
        UiVisibility.setInteractive(false)
        UiVisibility.setForeground(true)
        assertFalse(UiVisibility.active.value)
        UiVisibility.setInteractive(true)
        assertTrue(UiVisibility.active.value)
    }
}
