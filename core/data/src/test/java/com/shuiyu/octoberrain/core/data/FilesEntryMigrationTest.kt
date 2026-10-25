package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesEntryMigrationTest {
    @Test fun legacyRestoreOnlyChangesFilesVisibility() {
        val desktop =
            DesktopConfig(
                hiddenSpecials = setOf("special:files", "special:weread"),
                hiddenPackages = setOf("example.hidden"),
                order = listOf("app:com.android.rk", "special:settings"),
            )
        val migrated = ConfigMigration.migrate(LauncherConfig(version = 15, desktop = desktop))
        assertEquals(16, migrated.version)
        assertEquals(desktop.copy(hiddenSpecials = setOf("special:weread")), migrated.desktop)
        assertFalse(DesktopConfig().hiddenSpecials.contains("special:files"))
    }

    @Test fun userCanHideAgainAfterMigrationAndKeepChoiceAcrossReload() {
        val old = LauncherConfig(version = 15, desktop = DesktopConfig(hiddenSpecials = setOf("special:files")))
        val persistence = Memory(AppJson.format.encodeToString(LauncherConfig.serializer(), old))
        val first = JsonConfigStore(persistence)
        assertFalse(
            first.state.value.desktop.hiddenSpecials
                .contains("special:files"),
        )
        assertEquals(1, persistence.writes)
        first.edit { it.copy(desktop = it.desktop.copy(hiddenSpecials = it.desktop.hiddenSpecials + "special:files")) }
        val writes = persistence.writes
        val reopened = JsonConfigStore(persistence)
        assertTrue(
            reopened.state.value.desktop.hiddenSpecials
                .contains("special:files"),
        )
        assertEquals(writes, persistence.writes)
        assertEquals(reopened.state.value, ConfigMigration.migrate(reopened.state.value))
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
