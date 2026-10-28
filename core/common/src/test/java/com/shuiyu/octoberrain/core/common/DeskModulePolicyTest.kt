package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeskModulePolicyTest {
    @Test fun assetsAndFingerprintDefaultOnWithoutResettingExplicitOff() {
        assertTrue(DeskModulePolicy.isEnabled(LauncherConfig(), "rank"))
        assertTrue(DeskModulePolicy.isEnabled(LauncherConfig(), "fp"))
        val base = LauncherConfig()
        val explicitOff =
            base.copy(
                integrations = base.integrations.copy(wereadDesk = base.integrations.wereadDesk.copy(onMask = 1)),
            )
        assertFalse(DeskModulePolicy.isEnabled(explicitOff, "rank"))
        assertFalse(DeskModulePolicy.isEnabled(explicitOff, "fp"))
        org.junit.Assert.assertTrue("rank" in DeskModulePolicy.settingsOrder(LauncherConfig()))
    }

    @Test
    fun configuredOrderIsPreservedAndDeduplicated() {
        val config = config(listOf("rank", "bookshelf", "rank", "unknown", "bill", "fp"), 3)
        assertEquals(listOf("rank", "bookshelf", "bill"), DeskModulePolicy.visibleOrder(config))
    }

    @Test
    fun allOffRemainsEmptyButSettingsStillOffersAllModules() {
        val config = config(emptyList(), 0)
        assertTrue(DeskModulePolicy.visibleOrder(config).isEmpty())
        assertEquals(5, DeskModulePolicy.settingsOrder(config).size)
        assertEquals(5, DeskModulePolicy.settingsOrder(config).distinct().size)
    }

    @Test
    fun kernelSwitchControlsActualVisibilityAndCanEnableMissingFingerprint() {
        val config = config(listOf("bill", "rank"), 3)
        val disabled = DeskModulePolicy.toggle(config, "bill")
        assertEquals(listOf("rank"), DeskModulePolicy.visibleOrder(disabled))
        val enabled = DeskModulePolicy.toggle(disabled, "fp")
        assertEquals(listOf("rank", "fp"), DeskModulePolicy.visibleOrder(enabled))
        assertTrue(enabled.integrations.wereadDesk.isVisible("fp"))
        assertFalse(enabled.integrations.wereadDesk.isVisible("bill"))
    }

    @Test
    fun hostModuleCanBeDisabledAndReenabledAtOriginalPosition() {
        val config = config(listOf("rank", "bookshelf", "negative_files", "bill"), 3)
        val disabled = DeskModulePolicy.toggle(config, "bookshelf")
        assertFalse(DeskModulePolicy.isEnabled(disabled, "bookshelf"))
        assertTrue("bookshelf" in DeskModulePolicy.settingsOrder(disabled))
        val restored = DeskModulePolicy.toggle(disabled, "bookshelf")
        assertEquals(DeskModulePolicy.visibleOrder(config), DeskModulePolicy.visibleOrder(restored))
    }

    @Test
    fun movingModulesChangesDisplayAndKernelOrderTogether() {
        val config = config(listOf("rank", "bookshelf", "bill"), 3)
        val moved = DeskModulePolicy.move(config, "bill", -1)
        assertEquals(listOf("rank", "bill", "bookshelf"), DeskModulePolicy.visibleOrder(moved))
        assertEquals(listOf("rank", "bill"), moved.integrations.wereadDesk.order)
        assertEquals(moved, DeskModulePolicy.move(moved, "rank", -1))
    }

    @Test
    fun disabledModuleCanBeMovedThenReenabledAtNewPosition() {
        val config = config(listOf("bill", "rank"), 3)
        val moved = DeskModulePolicy.move(config, "bookshelf", -1)
        val enabled = DeskModulePolicy.toggle(moved, "bookshelf")
        assertEquals(listOf("bill", "bookshelf", "rank"), DeskModulePolicy.visibleOrder(enabled))
    }

    private fun config(
        modules: List<String>,
        mask: Int,
    ): LauncherConfig {
        val config = LauncherConfig()
        return config.copy(
            desktop = config.desktop.copy(negativePage = config.desktop.negativePage.copy(modules = modules)),
            integrations = config.integrations.copy(wereadDesk = config.integrations.wereadDesk.copy(onMask = mask)),
        )
    }
}
