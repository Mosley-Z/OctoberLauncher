package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 旧配置取消隐藏或保留排序键，也不能恢复第二个书架入口。 */
class DesktopEntriesTest {
    @Test
    fun legacyVisibleShelfKeyDoesNotCreateShelfEntry() {
        val desktop =
            DesktopConfig(hiddenSpecials = emptySet(), order = listOf("special:bookshelf", "special:settings"))
        val keys = DesktopEntries.build(emptyList(), desktop).map { it.key }
        assertFalse("special:bookshelf" in keys)
        assertTrue("special:settings" in keys)
        assertTrue("special:files" in keys)
    }

    @Test
    fun nativeAndLauncherSettingsKeepIndependentNames() {
        val system = InstalledApp("com.android.settings", "设置", "Settings", 0L, true)
        val entries = DesktopEntries.build(listOf(system), DesktopConfig())
        assertTrue(entries.any { it.packageName == "com.android.settings" && it.label == "设置" })
        assertEquals("十雨设置", entries.single { it.key == "special:settings" }.label)
    }

    @Test
    fun hidingLauncherSettingsDoesNotRemoveLastNativeSettingsEntry() {
        val system = InstalledApp("com.android.settings", "系统设置", "Settings", 0L, true)
        val entries = DesktopEntries.build(listOf(system), DesktopConfig(hiddenSpecials = setOf("special:settings")))
        assertTrue(entries.any { it.packageName == "com.android.settings" })
        assertFalse(entries.any { it.key == "special:settings" })
    }

    @Test
    fun vendorSettingsWithDifferentPackageKeepsOriginalLabel() {
        val vendor = InstalledApp("vendor.device.control", "设备控制中心", "VendorSettings", 0L, true)
        val entries = DesktopEntries.build(listOf(vendor), DesktopConfig())
        assertEquals("设备控制中心", entries.single { it.packageName == vendor.packageName }.label)
        assertTrue(entries.any { it.key == "special:settings" })
    }
}
