package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.DesktopFolder
import com.shuiyu.octoberrain.core.common.InstalledApp
import com.shuiyu.octoberrain.core.common.LauncherConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 旧包级入口键的组件键解析规则。 */
class ComponentKeyResolverTest {
    /** 内存配置仓库；edit 可配置为抛出异常以模拟写回失败。 */
    private class FakeConfigStore(
        config: LauncherConfig,
        private val failOnEdit: Boolean = false,
    ) : ConfigStore {
        private val mutable = MutableStateFlow(config)

        override val state: StateFlow<LauncherConfig> = mutable

        var editCount: Int = 0
            private set

        override fun edit(transform: (LauncherConfig) -> LauncherConfig) {
            editCount++
            if (failOnEdit) error("写入失败")
            mutable.value = transform(mutable.value)
        }
    }

    private fun app(
        packageName: String,
        activityName: String?,
    ): InstalledApp = InstalledApp(packageName, packageName, activityName, 0L, false)

    @Test
    fun singleCandidateIsReplacedInOrderFavoritesAndFolders() {
        val desktop =
            DesktopConfig(
                order = listOf("app:com.a", "special:files", "folder:f1"),
                favoriteEntryKeys = listOf("app:com.a"),
                folders = listOf(DesktopFolder("f1", "组", listOf("app:com.a", "special:settings"))),
            )
        val resolved = ComponentKeyResolver.resolve(desktop, listOf(app("com.a", "com.a.Main")))

        assertEquals(
            listOf("app:com.a/com.a.Main", "special:files", "folder:f1"),
            resolved.order,
        )
        assertEquals(listOf("app:com.a/com.a.Main"), resolved.favoriteEntryKeys)
        assertEquals(
            listOf("app:com.a/com.a.Main", "special:settings"),
            resolved.folders.single().memberKeys,
        )
        assertTrue(resolved.componentKeyMigrated)
        assertTrue(resolved.legacyComponentDecisions.isEmpty())
    }

    @Test
    fun multipleCandidatesKeepOldKeyAndRecordPendingDecision() {
        val desktop = DesktopConfig(order = listOf("app:com.a", "folder:f1"))
        val apps =
            listOf(
                app("com.a", "com.a.Main"),
                app("com.a", "com.a.Second"),
            )
        val resolved = ComponentKeyResolver.resolve(desktop, apps)

        assertEquals(listOf("app:com.a", "folder:f1"), resolved.order)
        assertEquals(mapOf("app:com.a" to ComponentKeyResolver.PENDING), resolved.legacyComponentDecisions)
        assertTrue(resolved.componentKeyMigrated)
    }

    @Test
    fun missingCandidateKeepsOldKeyWithoutDecision() {
        val desktop = DesktopConfig(order = listOf("app:com.missing"))
        val resolved = ComponentKeyResolver.resolve(desktop, listOf(app("com.a", "com.a.Main")))

        assertEquals(listOf("app:com.missing"), resolved.order)
        assertTrue(resolved.legacyComponentDecisions.isEmpty())
        assertTrue(resolved.componentKeyMigrated)
    }

    @Test
    fun existingDecisionsAreMergedInsteadOfOverwritten() {
        val desktop =
            DesktopConfig(
                order = listOf("app:com.a"),
                legacyComponentDecisions = mapOf("app:com.old" to ComponentKeyResolver.PENDING),
            )
        val apps = listOf(app("com.a", "com.a.Main"), app("com.a", "com.a.Second"))
        val resolved = ComponentKeyResolver.resolve(desktop, apps)

        assertEquals(
            mapOf(
                "app:com.old" to ComponentKeyResolver.PENDING,
                "app:com.a" to ComponentKeyResolver.PENDING,
            ),
            resolved.legacyComponentDecisions,
        )
    }

    @Test
    fun resolveIsIdempotentForMigratedConfig() {
        val desktop = DesktopConfig(order = listOf("app:com.a"))
        val apps = listOf(app("com.a", "com.a.Main"))
        val once = ComponentKeyResolver.resolve(desktop, apps)
        val twice = ComponentKeyResolver.resolve(once, apps)

        assertEquals(once, twice)
    }

    @Test
    fun resolveKeepsComponentKeysUntouched() {
        val desktop = DesktopConfig(order = listOf("app:com.a/com.a.Main", "folder:f1"))
        val resolved = ComponentKeyResolver.resolve(desktop, listOf(app("com.a", "com.a.Main")))

        assertEquals(listOf("app:com.a/com.a.Main", "folder:f1"), resolved.order)
    }

    @Test
    fun duplicatedKeysAreCollapsed() {
        val desktop = DesktopConfig(order = listOf("app:com.a", "app:com.a", "special:files"))
        val resolved = ComponentKeyResolver.resolve(desktop, listOf(app("com.a", "com.a.Main")))

        assertEquals(listOf("app:com.a/com.a.Main", "special:files"), resolved.order)
    }

    @Test
    fun runOnceWritesSnapshotAndMarker() {
        val store = FakeConfigStore(LauncherConfig(desktop = DesktopConfig(order = listOf("app:com.a"))))
        val apps = listOf(app("com.a", "com.a.Main"))

        val written = runBlocking { ComponentKeyResolver.runOnce(store, apps) }

        assertTrue(written)
        assertEquals(listOf("app:com.a/com.a.Main"), store.state.value.desktop.order)
        assertTrue(store.state.value.desktop.componentKeyMigrated)
    }

    @Test
    fun runOnceDoesNothingWhenAlreadyMigrated() {
        val desktop = DesktopConfig(order = listOf("app:com.a/com.a.Main"), componentKeyMigrated = true)
        val store = FakeConfigStore(LauncherConfig(desktop = desktop))

        val written = runBlocking { ComponentKeyResolver.runOnce(store, listOf(app("com.a", "com.a.Main"))) }

        assertFalse(written)
        assertEquals(0, store.editCount)
    }

    @Test
    fun runOnceSkipsEmptyCatalogWithoutWritingMarker() {
        val store = FakeConfigStore(LauncherConfig(desktop = DesktopConfig(order = listOf("app:com.a"))))

        val written = runBlocking { ComponentKeyResolver.runOnce(store, emptyList()) }

        assertFalse(written)
        assertEquals(0, store.editCount)
        assertFalse(store.state.value.desktop.componentKeyMigrated)
    }

    @Test
    fun runOnceReturnsFalseAndKeepsMarkerOffWhenWriteFails() {
        val store =
            FakeConfigStore(
                LauncherConfig(desktop = DesktopConfig(order = listOf("app:com.a"))),
                failOnEdit = true,
            )

        val written = runBlocking { ComponentKeyResolver.runOnce(store, listOf(app("com.a", "com.a.Main"))) }

        assertFalse(written)
        assertFalse(store.state.value.desktop.componentKeyMigrated)
        assertEquals(listOf("app:com.a"), store.state.value.desktop.order)
    }
}
