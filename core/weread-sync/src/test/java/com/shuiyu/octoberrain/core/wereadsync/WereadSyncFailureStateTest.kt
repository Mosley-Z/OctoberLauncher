package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WereadSyncFailureStateTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `partial transport failure stays failed after store reload`() =
        runBlocking {
            val message = "网络或服务暂不可用，已保留密钥与已有数据，请稍后重试"
            var failure: String? = null
            val provider =
                object : WereadSyncProvider {
                    override suspend fun shelf(): List<WereadShelfBook> = emptyList()

                    override suspend fun assets(): List<WereadAssetBook> = emptyList()

                    override suspend fun notes(): WereadNoteBatch? = null

                    override suspend fun bill(
                        mode: String,
                        periodStartSec: Long,
                        statsRaw: String,
                    ): String? = null

                    override suspend fun stats(
                        mode: String,
                        baseTimeSec: Long,
                    ): String? {
                        if (mode == "monthly") {
                            failure = message
                            return null
                        }
                        return "{}"
                    }
                }
            val service = WereadSyncService(WereadStore(folder.root), provider, failureMessage = { failure })
            val report = service.sync(listOf(WereadSyncPeriod("weekly", 1), WereadSyncPeriod("monthly", 1)))
            assertTrue(report.statCount > 0)
            assertFalse(report.success)
            assertEquals(message, report.message)
            val restored = parseSyncState(WereadStore(folder.root).getString(WereadSyncService.KEY_SYNC_STATE))!!
            assertFalse(restored.success)
            assertEquals(report.message, restored.message)

            failure = null
            val recovered = service.sync(listOf(WereadSyncPeriod("weekly", 1)))
            assertTrue(recovered.success)
            val restoredSuccess = parseSyncState(WereadStore(folder.root).getString(WereadSyncService.KEY_SYNC_STATE))!!
            assertTrue(restoredSuccess.success)
            assertEquals(recovered.message, restoredSuccess.message)
        }

    @Test fun currentBookWriteFailureCannotHideBehindSuccessfulStats() =
        runBlocking {
            val blocker = folder.newFolder("book.json")
            java.io.File(blocker, "keep").writeText("existing")
            val provider =
                object : WereadSyncProvider {
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
            val service = WereadSyncService(WereadStore(folder.root), provider)
            val report = service.sync(listOf(WereadSyncPeriod("overall", 0)), includeLibrary = false)
            assertFalse(report.success)
            assertTrue(report.statCount > 0)
            assertTrue(report.message.contains("当前书目写入失败"))
            assertEquals("existing", java.io.File(blocker, "keep").readText())
            assertFalse(parseSyncState(WereadStore(folder.root).getString(WereadSyncService.KEY_SYNC_STATE))!!.success)
            assertTrue(java.io.File(blocker, "keep").delete())
            assertTrue(blocker.delete())
            assertTrue(service.sync(listOf(WereadSyncPeriod("overall", 0)), includeLibrary = false).success)
        }

    @Test fun failedNoteBodyDoesNotPublishNewIndex() {
        val store = WereadStore(folder.root)
        assertTrue(store.writeNotes("old-index", emptyList()))
        val blocker = folder.newFolder("nt_block.json")
        java.io.File(blocker, "keep").writeText("old")
        assertFalse(store.writeNotes("new-index", listOf(WereadNoteFile("nt_block.json", "new-body"))))
        assertEquals("old-index", java.io.File(folder.root, "nt_index.json").readText())
        assertEquals("old", java.io.File(blocker, "keep").readText())
    }
}
