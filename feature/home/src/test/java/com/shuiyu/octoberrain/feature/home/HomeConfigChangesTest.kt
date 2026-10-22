package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.LauncherConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeConfigChangesTest {
    @Test fun lockAndTrayChangesDoNotReloadDesktop() {
        val config = LauncherConfig()
        assertFalse(homeConfigChanged(config, config.copy(lock = config.lock.copy(failedAttempts = 1))))
        assertFalse(homeConfigChanged(config, config.copy(tray = config.tray.copy(autoRefreshMinutes = 8))))
    }

    @Test fun desktopAppearanceAndContentChangesRemainVisible() {
        val config = LauncherConfig()
        assertTrue(homeConfigChanged(null, config))
        assertTrue(homeConfigChanged(config, config.copy(desktop = config.desktop.copy(wereadBoardPoolMask = 3))))
        assertTrue(homeConfigChanged(config, config.copy(appearance = config.appearance.copy(themeId = "paper"))))
        assertTrue(
            homeConfigChanged(config, config.copy(integrations = config.integrations.copy(wereadEnabled = false))),
        )
    }
}
