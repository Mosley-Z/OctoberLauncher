package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.TrayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyTrayGateTest {
    @Test
    fun `legacy master off remains visually off on real section load`() {
        val raw = """{"tray":{"enabled":false,"overlayEnabled":true,"showBatteryPercent":false,
          "autoRefreshMinutes":30,"volumeKeyFlip":true,"refreshOnAppExit":true,"refreshOnAppExitDelayMs":225},
          "notifications":{"badgeEnabled":true,"badgeMax":9}}"""
        val memory = Memory(raw)
        val store = JsonConfigStore(memory)
        val restored = store.state.value
        assertFalse(restored.tray.overlayEnabled)
        assertFalse(restored.tray.showBatteryPercent)
        assertEquals(30, restored.tray.autoRefreshMinutes)
        assertTrue(restored.tray.volumeKeyFlip)
        assertTrue(restored.tray.refreshOnAppExit)
        assertEquals(225, restored.tray.refreshOnAppExitDelayMs)
        assertTrue(restored.notifications.badgeEnabled)
        assertEquals(9, restored.notifications.badgeMax)
        store.edit { it.copy(appearance = it.appearance.copy(showLabels = false)) }
        val reloaded = JsonConfigStore(memory)
        assertFalse(reloaded.state.value.tray.overlayEnabled)
    }

    @Test
    fun `backup full decode folds exactly the same old master gate`() {
        val raw = AppJson.format.parseToJsonElement("""{"tray":{"enabled":false,"overlayEnabled":true}}""")
        val decoded = AppJson.format.decodeFromJsonElement(LauncherConfig.serializer(), raw)
        val imported = decoded.copy(tray = preserveLegacyTrayGate(raw, decoded.tray))
        assertFalse(imported.tray.overlayEnabled)
    }

    @Test
    fun `enabled or absent master does not change existing overlay preference`() {
        val on = TrayConfig(overlayEnabled = true)
        val off = TrayConfig(overlayEnabled = false)
        listOf("""{"enabled":true}""", "{}", """{"enabled":"false"}""").forEach { section ->
            val root = AppJson.format.parseToJsonElement("""{"tray":$section}""")
            assertSame(on, preserveLegacyTrayGate(root, on))
            assertSame(off, preserveLegacyTrayGate(root, off))
        }
        val disabled = AppJson.format.parseToJsonElement("""{"tray":{"enabled":false}}""")
        assertSame(off, preserveLegacyTrayGate(disabled, off))
    }

    private class Memory(
        private var raw: String,
    ) : ConfigPersistence {
        override fun read(): String = raw

        override fun write(value: String): Boolean {
            raw = value
            return true
        }
    }
}
