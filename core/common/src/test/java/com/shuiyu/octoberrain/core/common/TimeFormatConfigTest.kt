package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatConfigTest {
    @Test
    fun absentFieldKeepsTwentyFourHoursWithoutMigration() {
        val restored = AppJson.format.decodeFromString<LauncherConfig>("""{"appearance":{"themeId":"paper"}}""")
        assertFalse(restored.appearance.twelveHour)
        assertEquals(LauncherConfig.CURRENT_VERSION, restored.version)
    }

    @Test
    fun existingTwelveHourSelectionSurvivesConfigAndAppearanceRoundTrips() {
        val restored = AppJson.format.decodeFromString<LauncherConfig>("""{"appearance":{"twelveHour":true}}""")
        assertTrue(restored.appearance.twelveHour)
        assertEquals(
            restored,
            AppJson.format.decodeFromString<LauncherConfig>(AppJson.format.encodeToString(restored)),
        )
        assertEquals(
            restored.appearance,
            AppJson.format.decodeFromString<AppearanceConfig>(AppJson.format.encodeToString(restored.appearance)),
        )
    }
}
