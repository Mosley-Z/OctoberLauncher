package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 入口键的构造与解析规则。 */
class EntryKeysTest {
    @Test
    fun componentKeyJoinsPackageAndActivityWithSlash() {
        val key = EntryKeys.appKey("com.example.app", "com.example.app.MainActivity")
        assertEquals("app:com.example.app/com.example.app.MainActivity", key)
        assertTrue(EntryKeys.isComponentKey(key))
    }

    @Test
    fun legacyPackageKeyIsNotComponentKey() {
        assertFalse(EntryKeys.isComponentKey("app:com.example.app"))
        assertEquals("com.example.app", EntryKeys.legacyPackageOf("app:com.example.app"))
    }

    @Test
    fun componentKeyHasNoLegacyPackage() {
        val key = EntryKeys.appKey("com.example.app", "com.example.app.MainActivity")
        assertNull(EntryKeys.legacyPackageOf(key))
        assertEquals("com.example.app", EntryKeys.packageOf(key))
        assertEquals("com.example.app.MainActivity", EntryKeys.activityOf(key))
    }

    @Test
    fun specialAndFolderKeysAreNotAppKeys() {
        assertFalse(EntryKeys.isAppKey("special:files"))
        assertFalse(EntryKeys.isAppKey("folder:f1"))
        assertNull(EntryKeys.packageOf("special:files"))
        assertNull(EntryKeys.packageOf("folder:f1"))
        assertNull(EntryKeys.activityOf("special:files"))
    }

    @Test
    fun appEntryKeyPrefersActivityAndFallsBackToPackage() {
        val component = InstalledApp("com.example.app", "示例", "com.example.app.MainActivity", 0L, false)
        assertEquals("app:com.example.app/com.example.app.MainActivity", EntryKeys.appEntryKey(component))
        val legacy = InstalledApp("com.example.app", "示例", null, 0L, false)
        assertEquals("app:com.example.app", EntryKeys.appEntryKey(legacy))
    }

    @Test
    fun packageKeyBuildsLegacyKeyForMigrationLookup() {
        val key = EntryKeys.packageKey("com.example.app")
        assertEquals("app:com.example.app", key)
        assertEquals("com.example.app", EntryKeys.legacyPackageOf(key))
    }

    @Test
    fun blankActivityFallsBackToPackage() {
        val app = InstalledApp("com.example.app", "示例", "  ", 0L, false)
        assertEquals("app:com.example.app", EntryKeys.appEntryKey(app))
    }

    @Test
    fun nestedActivityNamesKeepFullClassName() {
        val key = EntryKeys.appKey("com.example.app", "com.example.app.ui.SecondActivity")
        assertEquals("com.example.app.ui.SecondActivity", EntryKeys.activityOf(key))
        assertEquals("com.example.app", EntryKeys.packageOf(key))
    }
}
