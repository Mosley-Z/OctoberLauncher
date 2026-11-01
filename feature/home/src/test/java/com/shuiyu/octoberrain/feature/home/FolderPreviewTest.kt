package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.DesktopFolder
import com.shuiyu.octoberrain.core.common.EntryKeys
import com.shuiyu.octoberrain.core.common.EntryType
import com.shuiyu.octoberrain.core.common.InstalledApp
import com.shuiyu.octoberrain.core.common.LauncherEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderPreviewTest {
    @Test
    fun previewTakesFirstFourVisibleMembersInFolderOrder() {
        val folder =
            DesktopFolder(
                "f1",
                "阅读",
                listOf(
                    "app:hidden",
                    "app:missing",
                    "special:files",
                    "folder:f2",
                    "app:e",
                    "app:b",
                    "app:d",
                    "app:a",
                    "app:c",
                ),
            )
        val members = previewFolderMembers(folder, catalog("a", "b", "c", "d", "e", "hidden"), setOf("hidden"))
        assertEquals(listOf("app:e", "app:b", "app:d", "app:a"), members.take(4).map { it.key })
        assertEquals(5, members.size)
        assertTrue(members.all { it.type == EntryType.APP && it.key == "app:${it.packageName}" })
    }

    @Test
    fun missingAndHiddenMembersReturnWhenCatalogOrVisibilityRestores() {
        val folder = DesktopFolder("f1", "阅读", listOf("app:a", "app:b"))
        assertTrue(previewFolderMembers(folder, catalog("a"), setOf("a")).isEmpty())
        assertEquals(listOf("app:a"), previewFolderMembers(folder, catalog("a"), emptySet()).map { it.key })
        val restored = previewFolderMembers(folder, catalog("b", "a"), emptySet())
        assertEquals(listOf("app:a", "app:b"), restored.map { it.key })
        assertEquals(listOf("名称a", "名称b"), restored.map { it.label })
        assertEquals(listOf("app:a", "app:b"), folder.memberKeys)
    }

    @Test
    fun zeroThroughFourMembersKeepTheirActualCount() {
        val apps = catalog("a", "b", "c", "d")
        for (count in 0..4) {
            val keys = listOf("app:a", "app:b", "app:c", "app:d").take(count)
            assertEquals(keys, previewFolderMembers(DesktopFolder("f1", "组", keys), apps, emptySet()).map { it.key })
        }
    }

    @Test
    fun editableMembersKeepUninstalledPlaceholderAndSkipHidden() {
        val folder = DesktopFolder("f1", "阅读", listOf("app:a", "app:hidden", "app:gone.example", "app:b"))
        val members = editableFolderMembers(folder, catalog("a", "hidden", "b"), setOf("hidden"))

        assertEquals(3, members.size)
        assertEquals(listOf("a", "gone.example", "b"), members.map { it.packageName })
        assertEquals("example（已卸载）", members[1].label)
        assertTrue(members.none { it.packageName == "hidden" })
        assertTrue(members.all { it.type == EntryType.APP && it.key == "app:${it.packageName}" })
    }

    @Test
    fun previewAndEditableDifferOnlyForUninstalled() {
        val folder = DesktopFolder("f1", "阅读", listOf("app:a", "app:hidden", "app:gone.example", "app:b"))
        val apps = catalog("a", "hidden", "b")
        val hiddenPackages = setOf("hidden")
        val preview = previewFolderMembers(folder, apps, hiddenPackages)
        val editable = editableFolderMembers(folder, apps, hiddenPackages)

        assertEquals(listOf("app:a", "app:b"), preview.map { it.key })
        assertEquals(listOf("app:a", "app:gone.example", "app:b"), editable.map { it.key })
        assertEquals(preview, editable.filter { it.packageName in apps })
    }

    @Test
    fun missingComponentDoesNotFallBackToSiblingActivity() {
        // 包内 A 已卸载、同包 A2 仍在；旧记录指向 A 时不得展示或启动 A2。
        val apps = catalogOf(app("pkg.a", "A2"))
        val folder = DesktopFolder("f1", "组", listOf("app:pkg.a/A"))

        assertTrue(previewFolderMembers(folder, apps, emptySet()).isEmpty())
        val editable = editableFolderMembers(folder, apps, emptySet())
        assertEquals(listOf("app:pkg.a/A"), editable.map { it.key })
        assertNull(editable.single().activityName)
        assertNull(resolveEntryApp(editable.single(), apps.values))
    }

    @Test
    fun aliasEntriesResolveToTheirOwnActivity() {
        val apps = catalogOf(app("pkg.a", "A1", "别名一"), app("pkg.a", "A2", "别名二"))
        val folder = DesktopFolder("f1", "组", listOf("app:pkg.a/A2"))

        val preview = previewFolderMembers(folder, apps, emptySet())
        assertEquals(listOf("别名二"), preview.map { it.label })
        assertEquals("A2", resolveEntryApp(preview.single(), apps.values)?.activityName)
    }

    @Test
    fun legacyKeyUsesOnlyCandidateWhenUnique() {
        val single = catalogOf(app("pkg.a", "Main"))
        assertEquals("Main", resolveLegacyApp("pkg.a", single.values)?.activityName)
        assertEquals("Main", resolveEntryApp(legacyEntry("pkg.a"), single.values)?.activityName)

        val multiple = catalogOf(app("pkg.a", "A1"), app("pkg.a", "A2"))
        assertNull(resolveLegacyApp("pkg.a", multiple.values))
        assertNull(resolveEntryApp(legacyEntry("pkg.a"), multiple.values))
        assertTrue(previewFolderMembers(DesktopFolder("f1", "组", listOf("app:pkg.a")), multiple, emptySet()).isEmpty())
    }

    private fun legacyEntry(packageName: String): LauncherEntry =
        LauncherEntry(
            key = EntryKeys.packageKey(packageName),
            type = EntryType.APP,
            label = packageName,
            packageName = packageName,
        )

    private fun app(
        packageName: String,
        activityName: String,
        label: String = "名称$activityName",
    ): InstalledApp = InstalledApp(packageName, label, activityName, 0L, false)

    /** 组件键索引，与 HomeView 的 appsByKey 一致。 */
    private fun catalogOf(vararg entries: InstalledApp): Map<String, InstalledApp> =
        entries.associateBy { EntryKeys.appEntryKey(it) }

    private fun catalog(vararg packages: String): Map<String, InstalledApp> =
        packages.associateWith { pkg -> InstalledApp(pkg, "名称$pkg", "Main", 0L, false) }
}
