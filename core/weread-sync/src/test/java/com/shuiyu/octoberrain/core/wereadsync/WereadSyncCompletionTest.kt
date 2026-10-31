package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WereadSyncCompletionTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun budgetTruncationPreservesOldPeriodAndRetryCompletes() =
        runBlocking {
            val store = WereadStore(folder.root)
            assertTrue(store.writeStats("monthly", 1, "{\"old\":true}", 0))
            var now = 0L
            var exhaustBudget = true
            val requested = mutableListOf<String>()
            val provider =
                object : Provider() {
                    override suspend fun stats(
                        mode: String,
                        baseTimeSec: Long,
                    ): String {
                        requested += mode
                        if (exhaustBudget) now = 45_001L
                        return "{}"
                    }
                }
            val service = WereadSyncService(store, provider, clock = { now })
            val periods = listOf(WereadSyncPeriod("weekly", 1), WereadSyncPeriod("monthly", 1))
            val report = service.sync(periods, includeLibrary = false)
            assertFalse(report.success)
            assertEquals(listOf("weekly"), requested)
            assertTrue(report.message.contains("统计仍有 1 期未完成"))
            val reloaded = WereadStore(folder.root)
            assertEquals("{\"old\":true}", reloaded.getString("d_monthly:1"))
            assertEquals("{}", reloaded.getString("d_weekly:1"))
            assertFalse(parseSyncState(reloaded.getString(WereadSyncService.KEY_SYNC_STATE))!!.success)
            exhaustBudget = false
            now = 0L
            requested.clear()
            assertTrue(service.sync(periods, includeLibrary = false).success)
            assertEquals(listOf("weekly", "monthly"), requested)
            assertEquals("{}", WereadStore(folder.root).getString("d_monthly:1"))
        }

    @Test fun failedStateWriteCannotReportSuccessAndCanRetry() =
        runBlocking {
            val blocker = folder.newFolder("kernel_prefs.json")
            val retained = java.io.File(blocker, "keep").apply { writeText("old") }
            val service = WereadSyncService(WereadStore(folder.root), Provider())
            val report = service.sync(emptyList(), includeLibrary = false)
            assertTrue(report.shelfCount > 0)
            assertFalse(report.success)
            assertEquals("同步状态写入失败，请检查存储空间", report.message)
            assertEquals("old", retained.readText())
            assertTrue(retained.delete())
            assertTrue(blocker.delete())
            assertTrue(service.sync(emptyList(), includeLibrary = false).success)
            assertTrue(parseSyncState(WereadStore(folder.root).getString(WereadSyncService.KEY_SYNC_STATE))!!.success)
        }

    @Test fun billExceptionFailsButUnsupportedNullRemainsValid() =
        runBlocking {
            val store = WereadStore(folder.root)
            assertTrue(store.writeBill("weekly", 1, "{\"old\":true}"))
            var fail = true
            val provider =
                object : Provider() {
                    override suspend fun bill(
                        mode: String,
                        periodStartSec: Long,
                        statsRaw: String,
                    ): String? {
                        check(!fail) { "fixture failure" }
                        return null
                    }
                }
            val service = WereadSyncService(store, provider)
            val periods = listOf(WereadSyncPeriod("weekly", 1))
            val report = service.sync(periods, includeLibrary = false)
            assertTrue(report.statCount > 0)
            assertFalse(report.success)
            assertTrue(report.message.contains("账单生成失败"))
            val reloaded = WereadStore(folder.root)
            assertFalse(parseSyncState(reloaded.getString(WereadSyncService.KEY_SYNC_STATE))!!.success)
            assertEquals("{\"old\":true}", reloaded.getString("bill_weekly_1"))
            fail = false
            assertTrue(service.sync(periods, includeLibrary = false).success)
        }

    private open class Provider : WereadSyncProvider {
        override suspend fun shelf() = listOf(WereadShelfBook("id", "title", "", "", 100, 37, false, 0, 1))

        override suspend fun assets(): List<WereadAssetBook>? = null

        override suspend fun notes(): WereadNoteBatch? = null

        override suspend fun stats(
            mode: String,
            baseTimeSec: Long,
        ) = "{}"

        override suspend fun bill(
            mode: String,
            periodStartSec: Long,
            statsRaw: String,
        ): String? = null
    }
}
