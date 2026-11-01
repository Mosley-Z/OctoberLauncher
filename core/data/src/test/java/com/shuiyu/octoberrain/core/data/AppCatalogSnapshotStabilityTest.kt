package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.InstalledApp
import com.shuiyu.octoberrain.core.common.LauncherConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 目录快照稳定性判定与旧入口键解析的触发条件。
 *
 * 覆盖首轮快照不完整时不迁移、等待窗口内连续两轮一致才迁移、间隔不足不迁移，
 * 以及轮询次数用尽后保持未迁移状态。
 */
class AppCatalogSnapshotStabilityTest {
    /** 内存配置仓库，供解析调用写回配置。 */
    private class FakeConfigStore(
        config: LauncherConfig,
    ) : ConfigStore {
        private val mutable = MutableStateFlow(config)

        override val state: StateFlow<LauncherConfig> = mutable

        override fun edit(transform: (LauncherConfig) -> LauncherConfig) {
            mutable.value = transform(mutable.value)
        }
    }

    /** 按生产判定规则模拟多轮刷新，记录上一轮入口键集合。 */
    private class CatalogRefreshDriver(
        private val store: FakeConfigStore,
    ) {
        private var previous: Set<String>? = null

        /** 执行一轮刷新：门控通过时才调用旧入口键解析。 */
        fun refresh(
            snapshot: List<InstalledApp>,
            elapsedMs: Long,
        ) {
            val keys = catalogKeys(snapshot)
            if (
                shouldMigrateLegacyKeys(
                    previous = previous,
                    current = keys,
                    hasConfig = true,
                    elapsedSinceFirstRefreshMs = elapsedMs,
                )
            ) {
                runBlocking { ComponentKeyResolver.runOnce(store, snapshot) }
            }
            previous = keys
        }
    }

    /** 构造指定包与 Activity 的目录条目。 */
    private fun app(
        packageName: String,
        activityName: String?,
    ): InstalledApp = InstalledApp(packageName, packageName, activityName, 0L, false)

    /** 文档管理应用的六个可启动入口，用于模拟完整快照。 */
    private fun completeDocumentsUi(): List<InstalledApp> =
        (1..6).map { app("com.android.documentsui", "com.android.documentsui.DocumentsActivity$it") }

    /** 构造仅包含桌面顺序的最小配置。 */
    private fun launcherConfig(order: List<String>): LauncherConfig =
        LauncherConfig(desktop = DesktopConfig(order = order))

    @Test
    fun emptySnapshotIsNeverStable() {
        assertFalse(isStableCatalog(null, emptySet()))
        assertFalse(isStableCatalog(emptySet(), emptySet()))
        assertFalse(
            shouldMigrateLegacyKeys(
                previous = null,
                current = emptySet(),
                hasConfig = true,
                elapsedSinceFirstRefreshMs = 5000,
            ),
        )
    }

    @Test
    fun firstNonEmptyRoundIsNotStable() {
        val current = catalogKeys(completeDocumentsUi())

        assertFalse(isStableCatalog(null, current))
        assertFalse(
            shouldMigrateLegacyKeys(
                previous = null,
                current = current,
                hasConfig = true,
                elapsedSinceFirstRefreshMs = 5000,
            ),
        )
    }

    @Test
    fun identicalConsecutiveRoundsAreStable() {
        val current = catalogKeys(completeDocumentsUi())

        assertTrue(isStableCatalog(current, current))
        assertTrue(
            shouldMigrateLegacyKeys(
                previous = current,
                current = current,
                hasConfig = true,
                elapsedSinceFirstRefreshMs = 2500,
            ),
        )
    }

    @Test
    fun changedSecondRoundIsNotStable() {
        val first = catalogKeys(completeDocumentsUi().take(1))
        val second = catalogKeys(completeDocumentsUi())

        assertFalse(isStableCatalog(first, second))
        assertFalse(
            shouldMigrateLegacyKeys(
                previous = first,
                current = second,
                hasConfig = true,
                elapsedSinceFirstRefreshMs = 5000,
            ),
        )
    }

    @Test
    fun missingConfigDisablesMigration() {
        val current = catalogKeys(completeDocumentsUi())

        assertFalse(
            shouldMigrateLegacyKeys(
                previous = current,
                current = current,
                hasConfig = false,
                elapsedSinceFirstRefreshMs = 5000,
            ),
        )
    }

    @Test
    fun identicalRoundsBeforeSettleWindowAreNotMigrated() {
        val current = catalogKeys(completeDocumentsUi())

        assertFalse(
            shouldMigrateLegacyKeys(
                previous = current,
                current = current,
                hasConfig = true,
                elapsedSinceFirstRefreshMs = 2000,
            ),
        )
    }

    @Test
    fun catalogKeysSeparatesActivitiesOfSamePackage() {
        val keys =
            catalogKeys(
                listOf(
                    app("com.a", "com.a.First"),
                    app("com.a", "com.a.Second"),
                    app("com.b", null),
                ),
            )

        assertEquals(
            setOf("com.a/com.a.First", "com.a/com.a.Second", "com.b/"),
            keys,
        )
    }

    @Test
    fun incompleteFirstSnapshotKeepsLegacyKeysUnresolved() {
        val store = FakeConfigStore(launcherConfig(order = listOf("app:com.android.documentsui")))
        val driver = CatalogRefreshDriver(store)

        // 首轮只返回一个入口，判定为不稳定，迁移推迟
        driver.refresh(completeDocumentsUi().take(1), elapsedMs = 0)

        val desktop = store.state.value.desktop
        assertFalse(desktop.componentKeyMigrated)
        assertEquals(listOf("app:com.android.documentsui"), desktop.order)
        assertTrue(desktop.legacyComponentDecisions.isEmpty())
    }

    @Test
    fun growingSnapshotIsNotMigratedUntilItRepeats() {
        val store = FakeConfigStore(launcherConfig(order = listOf("app:com.android.documentsui")))
        val driver = CatalogRefreshDriver(store)

        // 第一轮不完整、第二轮变多但与前一轮不同，两轮都不迁移
        driver.refresh(completeDocumentsUi().take(1), elapsedMs = 0)
        driver.refresh(completeDocumentsUi(), elapsedMs = 1000)

        val desktop = store.state.value.desktop
        assertFalse(desktop.componentKeyMigrated)
        assertEquals(listOf("app:com.android.documentsui"), desktop.order)
    }

    @Test
    fun stableSnapshotBeforeSettleWindowKeepsUnmigrated() {
        val store = FakeConfigStore(launcherConfig(order = listOf("app:com.android.documentsui")))
        val driver = CatalogRefreshDriver(store)

        // 两轮一致但距首次刷新不足等待窗口，仍不迁移
        driver.refresh(completeDocumentsUi(), elapsedMs = 0)
        driver.refresh(completeDocumentsUi(), elapsedMs = 2000)

        val desktop = store.state.value.desktop
        assertFalse(desktop.componentKeyMigrated)
        assertEquals(listOf("app:com.android.documentsui"), desktop.order)
    }

    @Test
    fun repeatingCompleteSnapshotMarksMultiCandidateKeyPending() {
        val store = FakeConfigStore(launcherConfig(order = listOf("app:com.android.documentsui")))
        val driver = CatalogRefreshDriver(store)

        // 首轮建立基线，第二轮一致且已越过等待窗口，多候选入口写待确认状态并保留原键
        driver.refresh(completeDocumentsUi(), elapsedMs = 0)
        driver.refresh(completeDocumentsUi(), elapsedMs = 2500)

        val desktop = store.state.value.desktop
        assertTrue(desktop.componentKeyMigrated)
        assertEquals(listOf("app:com.android.documentsui"), desktop.order)
        assertEquals("pending", desktop.legacyComponentDecisions["app:com.android.documentsui"])
    }

    @Test
    fun stableSingleCandidateSnapshotReplacesLegacyKey() {
        val store = FakeConfigStore(launcherConfig(order = listOf("app:com.single")))
        val driver = CatalogRefreshDriver(store)
        val snapshot = listOf(app("com.single", "com.single.Main"))

        driver.refresh(snapshot, elapsedMs = 0)
        driver.refresh(snapshot, elapsedMs = 2500)

        val desktop = store.state.value.desktop
        assertTrue(desktop.componentKeyMigrated)
        assertEquals(listOf("app:com.single/com.single.Main"), desktop.order)
        assertTrue(desktop.legacyComponentDecisions.isEmpty())
    }

    @Test
    fun pollingBudgetExhaustedKeepsUnmigrated() {
        val store = FakeConfigStore(launcherConfig(order = listOf("app:com.android.documentsui")))
        val driver = CatalogRefreshDriver(store)

        // 六轮轮询期间目录持续变化，始终没有稳定快照；等待窗口已过也不迁移
        for (round in 1..6) {
            driver.refresh(completeDocumentsUi().take(round), elapsedMs = round * 500L)
        }

        val desktop = store.state.value.desktop
        assertFalse(desktop.componentKeyMigrated)
        assertEquals(listOf("app:com.android.documentsui"), desktop.order)
        assertTrue(desktop.legacyComponentDecisions.isEmpty())
    }
}
