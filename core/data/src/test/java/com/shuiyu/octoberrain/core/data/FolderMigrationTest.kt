package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.DesktopFolder
import com.shuiyu.octoberrain.core.common.LauncherConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 文件夹迁移与真实配置序列化、存储重载边界。 */
class FolderMigrationTest {
    @Test
    fun version17PreservesOrderAndHiddenEntriesIdempotently() {
        val desktop =
            DesktopConfig(
                order = listOf("special:settings", "app:b", "app:a", "app:a"),
                hiddenPackages = setOf("hidden"),
                hiddenSpecials = setOf("special:files"),
            )
        val migrated = ConfigMigration.migrate(LauncherConfig(version = 17, desktop = desktop))
        assertEquals(18, migrated.version)
        assertEquals(desktop, migrated.desktop)
        assertSame(migrated, ConfigMigration.migrate(migrated))
    }

    @Test
    fun existingLayoutAndFilesMigrationsPreserveFolderAndUnknownOrderKeys() {
        val order = listOf("folder:f1", "future:entry", "app:a")
        val config = LauncherConfig(version = 13, desktop = DesktopConfig(order = order))
        assertEquals(order, DesktopLayoutMigration.migrate(config, emptySet()).desktop.order)
        assertEquals(order, FilesEntryMigration.migrate(config).desktop.order)
    }

    @Test
    fun version18NormalizesDamagedFoldersIdempotently() {
        val config =
            LauncherConfig(
                version = 17,
                desktop =
                    DesktopConfig(
                        folders =
                            listOf(
                                DesktopFolder("f1", "组", listOf("app:a", "app:a", "special:files")),
                                DesktopFolder("f2", "空组", listOf("app:a")),
                            ),
                        order = listOf("folder:f1", "app:a", "folder:missing", "folder:f1", "future:entry"),
                    ),
            )
        val migrated = ConfigMigration.migrate(config)
        assertEquals(listOf("folder:f1", "future:entry", "folder:f2"), migrated.desktop.order)
        assertEquals(
            emptyList<String>(),
            migrated.desktop.folders
                .last()
                .memberKeys,
        )
        assertSame(migrated, ConfigMigration.migrate(migrated))
    }

    @Test
    fun rawLegacyJsonLoadsWithoutFoldersAndWritesMigrationOnlyOnce() {
        val persistence = Memory("""{"version":17,"desktop":{"order":["app:b","app:a"],"hiddenPackages":["hidden"]}}""")
        val first = JsonConfigStore(persistence).state.value
        assertEquals(18, first.version)
        assertEquals(listOf("app:b", "app:a"), first.desktop.order)
        assertEquals(setOf("hidden"), first.desktop.hiddenPackages)
        assertEquals(emptyList<DesktopFolder>(), first.desktop.folders)
        assertEquals(first, JsonConfigStore(persistence).state.value)
        assertEquals(1, persistence.writes)
    }

    @Test
    fun serializedExportPayloadAndReloadPreserveFoldersAndOrder() {
        val config =
            LauncherConfig(
                desktop =
                    DesktopConfig(
                        folders = listOf(DesktopFolder("f1", "阅读", listOf("app:b", "app:hidden", "app:uninstalled"))),
                        order = listOf("special:settings", "folder:f1", "app:a"),
                        hiddenPackages = setOf("hidden"),
                    ),
            )
        val raw = AppJson.format.encodeToString(LauncherConfig.serializer(), config)
        val root = AppJson.format.parseToJsonElement(raw)
        val decoded = AppJson.format.decodeFromJsonElement(LauncherConfig.serializer(), root)
        val imported =
            ConfigMigration.migrate(decoded, hasShelfSource(root), DesktopLayoutMigration.presentFields(root))
        assertEquals(config.desktop, imported.desktop)
        val persistence = Memory(raw)
        val store = JsonConfigStore(persistence)
        assertEquals(config.desktop, store.state.value.desktop)
        store.edit {
            it.copy(
                desktop =
                    it.desktop.copy(
                        folders = it.desktop.folders + DesktopFolder("f2", "空组"),
                        order = it.desktop.order + "folder:f2",
                    ),
            )
        }
        assertEquals(config.desktop.order + "folder:f2", store.state.value.desktop.order)
        assertEquals(store.state.value.desktop, JsonConfigStore(persistence).state.value.desktop)
    }

    private class Memory(
        private var raw: String,
    ) : ConfigPersistence {
        var writes = 0

        override fun read(): String = raw

        override fun write(value: String): Boolean {
            writes += 1
            raw = value
            return true
        }
    }
}
