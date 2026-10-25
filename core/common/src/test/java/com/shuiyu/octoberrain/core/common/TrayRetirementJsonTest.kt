package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrayRetirementJsonTest {
    @Test
    fun `legacy panel fields do not prevent shared settings restore`() {
        val legacy = """{
          "version":15,
          "tray":{"enabled":true,"notificationLimit":4,"brightnessCold":2,"brightnessWarm":6,
            "mediaEnabled":true,"showWifi":true,"showBluetooth":false,
            "overlayEnabled":true,"showBatteryPercent":false,"autoRefreshMinutes":30,
            "volumeKeyFlip":true,"refreshOnAppExit":true,"refreshOnAppExitDelayMs":225},
          "notifications":{"showAppLabel":false,"badgeEnabled":true,"badgeMax":9,
            "foldByApp":true,"mergeConversation":false,"stripSummary":true}
        }"""
        val restored = AppJson.format.decodeFromString(LauncherConfig.serializer(), legacy)
        assertTrue(restored.tray.overlayEnabled)
        assertFalse(restored.tray.showBatteryPercent)
        assertEquals(30, restored.tray.autoRefreshMinutes)
        assertTrue(restored.tray.volumeKeyFlip)
        assertTrue(restored.tray.refreshOnAppExit)
        assertEquals(225, restored.tray.refreshOnAppExitDelayMs)
        assertTrue(restored.notifications.badgeEnabled)
        assertEquals(9, restored.notifications.badgeMax)
        assertTrue(restored.notifications.foldByApp)
        assertFalse(restored.notifications.mergeConversation)
        assertTrue(restored.notifications.stripSummary)
        val saved = AppJson.format.encodeToString(LauncherConfig.serializer(), restored)
        assertFalse(saved.contains("notificationLimit"))
        assertFalse(saved.contains("showAppLabel"))
    }
}
