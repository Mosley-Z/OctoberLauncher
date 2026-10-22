package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.NegativePageConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 旧书源迁移必须保留新选择、模块顺序和负一屏行数。 */
class ShelfMigrationTest {
    @Test
    fun upgradeDiscardsOldShelfTimerAndPreservesOtherSyncSettings() {
        val raw = """{"version":12,"desktop":{"negativePage":{"shelfRows":5,"shelfSource":"local",
            "shelfRefreshMode":"auto","shelfRefreshMinutes":360}},
            "integrations":{"wereadRefreshMinutes":180,"wereadSource":"synced"}}"""
        val old = AppJson.format.decodeFromString(LauncherConfig.serializer(), raw)
        val next = ConfigMigration.migrate(old)
        assertEquals(13, next.version)
        assertEquals(5, next.desktop.negativePage.shelfRows)
        assertEquals("local", next.desktop.negativePage.shelfSource)
        assertEquals(old.integrations, next.integrations)
        val saved = AppJson.format.encodeToString(LauncherConfig.serializer(), next)
        assertFalse(saved.contains("shelfRefreshMode"))
        assertFalse(saved.contains("shelfRefreshMinutes"))
        assertEquals(next, ConfigMigration.migrate(next))
    }

    @Test
    fun oldToggleCombinationsMigrateOnce() {
        listOf(false, true).forEach { local ->
            listOf(false, true).forEach { weread ->
                val old =
                    LauncherConfig(
                        version = 10,
                        desktop = DesktopConfig(negativePage = NegativePageConfig(shelfRows = 5)),
                        integrations =
                            IntegrationsConfig(
                                bookshelfLocalEnabled = local,
                                bookshelfWereadEnabled = weread,
                                bookshelfRows = 1,
                            ),
                    )
                val next = ConfigMigration.migrate(old)
                assertEquals(if (local && !weread) "local" else "weread", next.desktop.negativePage.shelfSource)
                assertEquals(5, next.desktop.negativePage.shelfRows)
                assertEquals(next, ConfigMigration.migrate(next))
            }
        }
    }

    @Test
    fun explicitSourceOverridesOldTogglesIncludingDefaultChoice() {
        listOf("weread", "local", "unknown").forEach { selected ->
            val old =
                LauncherConfig(
                    version = 10,
                    desktop = DesktopConfig(negativePage = NegativePageConfig(shelfSource = selected)),
                    integrations = IntegrationsConfig(bookshelfLocalEnabled = true, bookshelfWereadEnabled = false),
                )
            val next = ConfigMigration.migrate(old, shelfSourcePresent = true)
            assertEquals(if (selected == "local") "local" else "weread", next.desktop.negativePage.shelfSource)
        }
    }

    @Test
    fun rawPresenceDistinguishesMissingFromDefault() {
        assertFalse(hasShelfSource(AppJson.format.parseToJsonElement("""{"desktop":{"negativePage":{}}}""")))
        assertTrue(
            hasShelfSource(
                AppJson.format.parseToJsonElement("""{"desktop":{"negativePage":{"shelfSource":"weread"}}}"""),
            ),
        )
    }

    @Test
    fun versionNineDoesNotRerunModuleMigration() {
        val modules = listOf("recent", "bookshelf", "bill")
        val old =
            LauncherConfig(version = 9, desktop = DesktopConfig(negativePage = NegativePageConfig(modules = modules)))
        assertEquals(
            modules,
            ConfigMigration
                .migrate(old)
                .desktop.negativePage.modules,
        )
    }
}
