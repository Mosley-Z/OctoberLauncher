package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.DesktopFolder
import com.shuiyu.octoberrain.core.common.EntryType
import com.shuiyu.octoberrain.core.common.FolderRules
import com.shuiyu.octoberrain.core.common.InstalledApp
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.common.SortMode
import com.shuiyu.octoberrain.core.common.SpecialEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 旧配置取消隐藏或保留排序键，也不能恢复第二个书架入口。 */
class DesktopEntriesTest {
    @Test
    fun folderMembersDoNotAppearAtTopLevel() {
        val folder = DesktopFolder("f1", "阅读", listOf("app:a", "app:hidden", "app:uninstalled"))
        val desktop = DesktopConfig(hiddenPackages = setOf("hidden"), folders = listOf(folder))
        val entries = DesktopEntries.build(listOf(app("a"), app("b"), app("hidden")), desktop)
        assertFalse(entries.any { it.key == "app:a" || it.key == "app:hidden" })
        assertTrue(entries.any { it.key == "app:b" })
        val entry = entries.single { it.key == "folder:f1" }
        assertTrue(entry.isFolder)
        assertEquals(EntryType.FOLDER, entry.type)
        assertEquals("阅读", entry.label)
        assertEquals(folder, entry.folder)
        val emptyCatalogFolders = DesktopEntries.build(emptyList(), desktop).filter { it.isFolder }.map { it.key }
        assertEquals(listOf("folder:f1"), emptyCatalogFolders)
        val restored = DesktopEntries.build(listOf(app("a")), desktop.copy(hiddenPackages = emptySet()))
        assertFalse(restored.any { it.key == "app:a" })
    }

    @Test
    fun emptyFoldersUseFallbackLabelAndPrecedeSpecialsWithoutOrder() {
        val desktop = DesktopConfig(folders = listOf(DesktopFolder("f1", " ")))
        val entries = DesktopEntries.build(listOf(app("a")), desktop)
        assertEquals(listOf("app:a", "folder:f1", "special:files", "special:settings"), entries.map { it.key })
        assertEquals("文件夹", entries.single { it.isFolder }.label)
    }

    @Test
    fun orderRanksFoldersAndAppsTogetherStably() {
        val desktop =
            DesktopConfig(
                folders = listOf(DesktopFolder("f1", "组", listOf("app:a"))),
                order = listOf("folder:f1", "app:c"),
            )
        val entries = DesktopEntries.build(listOf(app("a"), app("b"), app("c")), desktop)
        assertEquals(listOf("folder:f1", "app:c", "app:b", "special:files", "special:settings"), entries.map { it.key })
    }

    @Test
    fun damagedOrderUsesNormalizedRanksWithoutDuplicateEntries() {
        val desktop =
            DesktopConfig(
                folders = listOf(DesktopFolder("f1", "Books")),
                order = listOf("app:b", "folder:missing", "folder:f1", "app:a", "app:b"),
            )
        val normalized = FolderRules.normalize(desktop)
        assertEquals(listOf("app:b", "folder:f1", "app:a"), normalized.order)
        val keys = DesktopEntries.build(listOf(app("a"), app("b")), desktop).map { it.key }
        assertEquals(normalized.order + listOf("special:files", "special:settings"), keys)
        assertEquals(keys.distinct(), keys)
    }

    @Test
    fun damagedFolderReferencesFallBackToAppEntries() {
        val desktop =
            DesktopConfig(
                folders = listOf(DesktopFolder("", "损坏", listOf("app:a"))),
                order = listOf("folder:missing", "app:a"),
            )
        assertTrue(DesktopEntries.build(listOf(app("a")), desktop).any { it.key == "app:a" })
        val missing = DesktopConfig(order = listOf("folder:missing", "app:a"))
        assertTrue(DesktopEntries.build(listOf(app("a")), missing).any { it.key == "app:a" })
    }

    @Test
    fun noFoldersMatchLegacyRulesForEverySortAndOrder() {
        val apps = listOf(app("z", "甲", 3L), app("a", "乙", 1L), app("b", "甲", 1L), app("hidden"))
        for (mode in SortMode.entries) {
            for (order in listOf(emptyList(), listOf("special:settings", "app:z", "app:z", "app:a"))) {
                val desktop =
                    DesktopConfig(
                        sortMode = mode,
                        hiddenPackages = setOf("hidden"),
                        hiddenSpecials = setOf("special:weread"),
                        order = order,
                    )
                assertEquals(legacyBuild(apps, desktop), DesktopEntries.build(apps, desktop))
            }
        }
    }

    /** 按改造前的独立规则构造期望，不调用新的组装入口。 */
    private fun legacyBuild(
        apps: List<InstalledApp>,
        desktop: DesktopConfig,
    ): List<LauncherEntry> {
        val byPackage = apps.associateBy { it.packageName }
        val visible =
            apps.filter { it.packageName !in desktop.hiddenPackages }.map {
                LauncherEntry("app:${it.packageName}", EntryType.APP, it.label, packageName = it.packageName)
            }
        val sorted =
            when (desktop.sortMode) {
                SortMode.INSTALL_TIME ->
                    visible.sortedWith(
                        compareBy<LauncherEntry> { byPackage[it.packageName]?.firstInstallTime ?: 0L }
                            .thenBy { it.label }
                            .thenBy { it.packageName ?: "" },
                    )
                SortMode.LABEL ->
                    visible.sortedWith(compareBy<LauncherEntry> { it.label }.thenBy { it.packageName ?: "" })
                SortMode.PACKAGE -> visible.sortedBy { it.packageName ?: "" }
                SortMode.MANUAL -> visible
            }
        val specials =
            listOf(
                LauncherEntry("special:weread", EntryType.SPECIAL, "微信读书", special = SpecialEntry.WEREAD),
                LauncherEntry("special:files", EntryType.SPECIAL, "文件", special = SpecialEntry.FILES),
                LauncherEntry("special:settings", EntryType.SPECIAL, "十雨设置", special = SpecialEntry.SETTINGS),
            ).filter { it.key !in desktop.hiddenSpecials }
        val base = sorted + specials
        if (desktop.order.isEmpty()) return base
        val rank = desktop.order.withIndex().associate { (index, key) -> key to index }
        return base.sortedWith(compareBy { rank[it.key] ?: Int.MAX_VALUE })
    }

    private fun app(
        packageName: String,
        label: String = packageName,
        time: Long = 0L,
    ): InstalledApp = InstalledApp(packageName, label, null, time, false)

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

    @Test
    fun componentAppsWithSamePackageProduceDistinctKeys() {
        val apps =
            listOf(
                InstalledApp("com.example.multi", "多入口", "com.example.multi.First", 1L, false),
                InstalledApp("com.example.multi", "多入口", "com.example.multi.Second", 2L, false),
            )
        val entries = DesktopEntries.build(apps, DesktopConfig())

        assertEquals(
            listOf(
                "app:com.example.multi/com.example.multi.First",
                "app:com.example.multi/com.example.multi.Second",
            ),
            entries.filter { it.isApp }.map { it.key },
        )
        assertTrue(entries.filter { it.isApp }.all { it.activityName != null })
    }

    @Test
    fun folderMembersFilterOnlyMatchingComponent() {
        val folder =
            DesktopFolder(
                "f1",
                "组",
                listOf("app:com.example.multi/com.example.multi.First"),
            )
        val apps =
            listOf(
                InstalledApp("com.example.multi", "多入口", "com.example.multi.First", 0L, false),
                InstalledApp("com.example.multi", "多入口", "com.example.multi.Second", 0L, false),
            )
        val keys = DesktopEntries.build(apps, DesktopConfig(folders = listOf(folder))).map { it.key }

        assertFalse("app:com.example.multi/com.example.multi.First" in keys)
        assertTrue("app:com.example.multi/com.example.multi.Second" in keys)
    }

    @Test
    fun legacyPackageKeyInFolderNoLongerHidesComponentEntries() {
        // 迁移期旧包级成员键与组件键不相等，入口保持可见直到解析完成。
        val folder = DesktopFolder("f1", "组", listOf("app:com.example.multi"))
        val apps = listOf(InstalledApp("com.example.multi", "多入口", "com.example.multi.First", 0L, false))
        val entries = DesktopEntries.build(apps, DesktopConfig(folders = listOf(folder)))

        assertTrue(entries.any { it.key == "app:com.example.multi/com.example.multi.First" })
    }

    @Test
    fun hiddenPackageHidesEveryComponentOfThatPackage() {
        val apps =
            listOf(
                InstalledApp("com.example.multi", "多入口", "com.example.multi.First", 0L, false),
                InstalledApp("com.example.multi", "多入口", "com.example.multi.Second", 1L, false),
                InstalledApp("com.example.other", "其他", "com.example.other.Main", 2L, false),
            )
        val entries = DesktopEntries.build(apps, DesktopConfig(hiddenPackages = setOf("com.example.multi")))

        assertFalse(entries.any { it.packageName == "com.example.multi" })
        assertTrue(entries.any { it.packageName == "com.example.other" })
    }

    @Test
    fun manualOrderMatchesComponentKeysOnly() {
        val apps =
            listOf(
                InstalledApp("com.example.multi", "多入口", "com.example.multi.First", 0L, false),
                InstalledApp("com.example.multi", "多入口", "com.example.multi.Second", 0L, false),
            )
        val desktop =
            DesktopConfig(
                order = listOf("app:com.example.multi/com.example.multi.Second", "app:com.example.multi"),
            )
        val keys = DesktopEntries.build(apps, desktop).filter { it.isApp }.map { it.key }

        assertEquals(
            listOf(
                "app:com.example.multi/com.example.multi.Second",
                "app:com.example.multi/com.example.multi.First",
            ),
            keys,
        )
    }
}
