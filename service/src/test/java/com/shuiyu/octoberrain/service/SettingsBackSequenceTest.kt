package com.shuiyu.octoberrain.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackSequenceTest {
    @Test fun `captured down repeat and up remain owned across focus changes`() {
        val keys = SettingsBackSequence()
        assertTrue(keys.down(100, 2, 0, true))
        assertTrue(keys.down(100, 2, 1, false))
        assertTrue(keys.up(100, 2))
        assertFalse(keys.up(100, 2))
    }

    @Test fun `foreign keys and an unowned repeat never consume a matching release`() {
        val keys = SettingsBackSequence()
        assertFalse(keys.down(100, 2, 1, true))
        assertFalse(keys.up(100, 2))
        assertTrue(keys.down(200, 2, 0, true))
        assertFalse(keys.up(200, 3))
        assertTrue(keys.up(200, 2))
        assertFalse(keys.down(300, 2, 0, false))
        assertFalse(keys.up(300, 2))
    }

    @Test fun `disconnect clears ownership`() {
        val keys = SettingsBackSequence()
        assertTrue(keys.down(100, 2, 0, true))
        keys.clear()
        assertFalse(keys.up(100, 2))
    }

    @Test fun `only default S4 home session at vendor settings root qualifies`() {
        assertTrue(settingsBackEligible(true, true, true, VENDOR, true, true))
        assertFalse(settingsBackEligible(false, true, true, VENDOR, true, true))
        assertFalse(settingsBackEligible(true, false, true, VENDOR, true, true))
        assertFalse(settingsBackEligible(true, true, false, VENDOR, true, true))
        assertFalse(settingsBackEligible(true, true, true, "other.app", true, true))
        assertFalse(settingsBackEligible(true, true, true, VENDOR, false, true))
        assertFalse(settingsBackEligible(true, true, true, VENDOR, true, false))
    }

    private companion object {
        const val VENDOR = "com.wetao.elauncher"
    }
}
