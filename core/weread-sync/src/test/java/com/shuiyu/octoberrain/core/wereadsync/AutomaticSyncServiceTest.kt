package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AutomaticSyncServiceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `automatic scope skips library but still derives current bills`() =
        runBlocking {
            var assets = 0
            var notes = 0
            val provider =
                object : WereadSyncProvider {
                    override suspend fun shelf(): List<WereadShelfBook> = emptyList()

                    override suspend fun assets(): List<WereadAssetBook>? {
                        assets++
                        return null
                    }

                    override suspend fun notes(): WereadNoteBatch? {
                        notes++
                        return null
                    }

                    override suspend fun stats(
                        mode: String,
                        baseTimeSec: Long,
                    ): String = "{}"

                    override suspend fun bill(
                        mode: String,
                        periodStartSec: Long,
                        statsRaw: String,
                    ): String = """{"items":[]}"""
                }
            val service = WereadSyncService(WereadStore(folder.root), provider)
            val periods = WereadSyncService.automaticPeriods()
            assertEquals(listOf("weekly", "monthly", "overall"), periods.map { it.mode })
            val report = service.sync(periods, includeLibrary = false)
            assertTrue(report.success)
            assertEquals(3, report.statCount)
            assertEquals(2, report.billCount)
            assertEquals(0, assets)
            assertEquals(0, notes)
            val manual = service.sync()
            assertTrue(manual.success)
            assertEquals(28, manual.statCount)
            assertEquals(24, manual.billCount)
            assertEquals(1, assets)
            assertEquals(1, notes)
        }
}
