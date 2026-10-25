package com.shuiyu.octoberrain.core.common

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPanelAccessTest {
    @After
    fun tearDown() {
        NotificationPanelAccess.disconnect()
    }

    @Test
    fun disconnectedServiceCannotOpenPanel() {
        NotificationPanelAccess.disconnect()
        assertFalse(NotificationPanelAccess.available.value)
        assertFalse(NotificationPanelAccess.open())
    }

    @Test
    fun connectedServiceOpensUntilUnbound() {
        NotificationPanelAccess.connect { true }
        assertTrue(NotificationPanelAccess.available.value)
        assertTrue(NotificationPanelAccess.open())
        NotificationPanelAccess.disconnect()
        assertFalse(NotificationPanelAccess.open())
    }

    @Test
    fun rejectedActionRestoresFallbackUntilServiceReconnects() {
        NotificationPanelAccess.connect { false }
        assertFalse(NotificationPanelAccess.open())
        assertFalse(NotificationPanelAccess.available.value)
        NotificationPanelAccess.connect { true }
        assertTrue(NotificationPanelAccess.open())
    }

    @Test
    fun serviceExceptionRestoresFallback() {
        NotificationPanelAccess.connect { error("service disconnected") }
        assertFalse(NotificationPanelAccess.open())
        assertFalse(NotificationPanelAccess.available.value)
    }
}
