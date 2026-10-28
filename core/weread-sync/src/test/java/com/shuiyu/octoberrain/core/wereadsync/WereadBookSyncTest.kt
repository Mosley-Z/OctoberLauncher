package com.shuiyu.octoberrain.core.wereadsync

import com.shuiyu.octoberrain.core.data.WereadDataGate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 使用真实响应结构验证逐书进度，不将周期榜单当作累计值。 */
class WereadBookSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val requests = mutableListOf<String>()
    private var now = 1_000_000L
    private var progressAvailable = true
    private var shelfJson = """{"books":[{"bookId":"b1","title":"本书","readUpdateTime":100}]}"""
    private var progressJson = """{"book":{"progress":35,"readingTime":103831,"recordReadingTime":0}}"""

    private fun sync(): WereadBookSync =
        WereadBookSync(
            WereadRequest { api, business ->
                requests += "$api:${business["bookId"]?.jsonPrimitive?.content.orEmpty()}"
                val raw =
                    when (api) {
                        "/shelf/sync" -> shelfJson
                        "/user/notebooks" -> """{"books":[],"hasMore":0}"""
                        "/readdata/detail" -> """{"baseTime":0,"readLongest":[],"totalReadTime":0}"""
                        else -> progressJson.takeIf { progressAvailable }
                    }
                raw?.let { Json.parseToJsonElement(it).jsonObject }
            },
            folder.root,
            { now },
        )

    @Test fun assetRefreshFetchesPriceIndexWithoutDownloadingBodies() {
        val calls = mutableListOf<String>()
        val worker =
            WereadBookSync(
                WereadRequest { api, _ ->
                    calls += api
                    val raw =
                        when (api) {
                            "/shelf/sync" -> shelfJson
                            "/book/getprogress" -> progressJson
                            "/readdata/detail" -> """{"readLongest":[]}"""
                            "/user/notebooks" -> """{"books":[{"bookId":"b1","book":{"centPrice":1250}}],"hasMore":0}"""
                            else -> error("Unexpected body request: $api")
                        }
                    Json.parseToJsonElement(raw).jsonObject
                },
                folder.root,
            )
        assertTrue(worker.refreshAssets().success)
        val index = File(folder.root, "nt_index.json").readText()
        assertTrue(index.contains("1250"))
        assertEquals(1, calls.count { it == "/user/notebooks" })
    }

    @Test fun priceIndexFailureReportsPartialAndPreservesPreviousPrices() {
        val old = """{"books":[{"bookId":"b1","centPrice":1250}]}"""
        File(folder.root, "nt_index.json").writeText(old)
        val worker =
            WereadBookSync(
                WereadRequest { api, _ ->
                    when (api) {
                        "/shelf/sync" -> shelfJson
                        "/book/getprogress" -> progressJson
                        "/readdata/detail" -> """{"readLongest":[]}"""
                        else -> null
                    }?.let { Json.parseToJsonElement(it).jsonObject }
                },
                folder.root,
            )
        val report = worker.refreshAssets()
        assertFalse(report.success)
        assertTrue(report.message.contains("价格索引未更新"))
        assertEquals(old, File(folder.root, "nt_index.json").readText())
    }

    @Test fun manualAssetsFillWholeShelfThenOnlyChangedBooks() {
        shelfJson = "{\"books\":[" +
            (1..12).joinToString(",") {
                "{\"bookId\":\"b$it\",\"readUpdateTime\":100}"
            } + "]}"
        assertTrue(sync().refreshAssets().success)
        assertEquals(12, requests.count { it.startsWith("/book/getprogress") })
        requests.clear()
        assertTrue(sync().refreshAssets().success)
        assertEquals(listOf("/shelf/sync:", "/readdata/detail:", "/user/notebooks:"), requests)
        requests.clear()
        shelfJson = shelfJson.replace("\"b1\",\"readUpdateTime\":100", "\"b1\",\"readUpdateTime\":101")
        assertTrue(sync().refreshAssets().success)
        assertEquals(listOf("/shelf/sync:", "/book/getprogress:b1", "/readdata/detail:", "/user/notebooks:"), requests)
        assertEquals(12, sync().cachedAssets().size)
    }

    @Test fun unchangedManualAssetDoesNotExpireAfterADay() {
        assertTrue(sync().refreshAssets().success)
        requests.clear()
        now += 3 * 24 * 60 * 60 * 1000L
        assertTrue(sync().refreshAssets().success)
        assertEquals(listOf("/shelf/sync:", "/readdata/detail:", "/user/notebooks:"), requests)
    }

    @Test fun absentCumulativeTimeIsPartialAndRetainsPriorValue() {
        assertTrue(sync().refreshAssets().success)
        shelfJson = shelfJson.replace("100", "101")
        progressJson = """{"book":{"progress":45}}"""
        assertFalse(sync().refreshAssets().success)
        assertEquals(103831, sync().cachedAssets().single().readingSeconds)
    }

    @Test fun firstExplanationNeverReturnsAfterAnAttemptOrLegacyAssets() {
        assertEquals(WereadAssetRefreshState.FIRST_NOTICE, WereadAssetRefreshState.notice(folder.root))
        WereadAssetRefreshState.save(folder.root, WereadSyncReport(false, "部分未完成"), now)
        assertEquals(null, WereadAssetRefreshState.notice(folder.root))
        val legacyRoot = folder.newFolder("legacy")
        WereadStore(legacyRoot).writeAssets(listOf(WereadAssetBook("old", "历史", "", 700, 0, 0, 50, false)))
        assertEquals(null, WereadAssetRefreshState.notice(legacyRoot))
    }

    @Test fun fullAssetsRefetchCachedHistoryAndRetainUserEdits() {
        val store = WereadStore(folder.root)
        store.writeAssets(listOf(WereadAssetBook("history", "历史", "", 700, 0, 0, 30, false)))
        val edits = WereadDeskEditsStore(folder.root)
        assertTrue(edits.setPrice("history", 1234))
        assertTrue(edits.hide("history"))
        assertTrue(sync().refreshAssets().success)
        requests.clear()
        assertTrue(sync().refreshAssets(forceAll = true).success)
        assertTrue("/book/getprogress:history" in requests)
        assertEquals(2, requests.count { it.startsWith("/book/getprogress:") })
        assertEquals(1234, edits.priceFen("history"))
        assertTrue("history" in edits.hiddenIds())
        assertEquals(null, WereadAssetRefreshState.notice(folder.root))
    }

    @Test fun fullFailurePreservesOldHistoryAndShowsPartialStatus() {
        val store = WereadStore(folder.root)
        store.writeAssets(listOf(WereadAssetBook("history", "历史", "", 700, 0, 0, 30, false)))
        progressAvailable = false
        assertFalse(sync().refreshAssets(forceAll = true).success)
        assertEquals(700, sync().cachedAssets().single().readingSeconds)
        assertEquals(null, WereadAssetRefreshState.notice(folder.root))
    }

    @Test fun failedManualAssetsStopAndCanResumeWithoutLosingCache() {
        assertTrue(sync().refreshAssets().success)
        shelfJson = shelfJson.replace("100", "101")
        progressAvailable = false
        assertFalse(sync().refreshAssets().success)
        assertEquals(1, sync().cachedAssets().size)
        progressAvailable = true
        requests.clear()
        assertTrue(sync().refreshAssets().success)
        assertEquals(listOf("/shelf/sync:", "/book/getprogress:b1", "/readdata/detail:", "/user/notebooks:"), requests)
    }

    @Test
    fun `整数进度零一三十五一百均不缩放且累计秒优先`() {
        listOf(0, 1, 35, 100).forEach { pct ->
            progressJson = """{"book":{"progress":$pct,"readingTime":103831,"recordReadingTime":999999}}"""
            val book = sync().shelf()!!.single()
            assertEquals(pct, book.progress)
            assertEquals(103831, book.readingSeconds)
            assertEquals(pct == 100, book.finished)
        }
    }

    @Test
    fun `时长缺失或零才回退并规范完读秒时间戳`() {
        progressJson = """{"book":{"progress":100,"readingTime":0,"recordReadingTime":900,"finishTime":1700000000}}"""
        val book = sync().shelf()!!.single()
        assertEquals(900, book.readingSeconds)
        assertTrue(book.finished)
        assertEquals(1_700_000_000_000L, book.finishTimeMillis)
    }

    @Test
    fun `失败保留跨实例缓存首次失败保持未知`() {
        sync().shelf()
        progressAvailable = false
        val cached = sync()
        assertEquals(35, cached.shelf()!!.single().progress)
        assertEquals(103831, cached.assets()!!.single().readingSeconds)
        assertEquals(1, cached.warnings.size)
        shelfJson = """{"books":[{"bookId":"uncached","readUpdateTime":200}]}"""
        assertEquals(-1, sync().shelf()!!.single().progress)
    }

    @Test
    fun `历史资产全量补齐且新鲜缓存仅补查最近本书`() {
        shelfJson =
            """
            {"books":[{"bookId":"recent","readUpdateTime":200},
            {"bookId":"old","readUpdateTime":100},
            {"bookId":"unread","readUpdateTime":0}]}
            """.trimIndent()
        val first = sync()
        first.shelf()
        assertEquals(2, first.assets()!!.size)
        assertEquals(1, requests.count { it == "/shelf/sync:" })
        assertEquals(2, requests.count { it.startsWith("/book/getprogress") })
        requests.clear()
        val second = sync()
        second.shelf()
        assertEquals(2, second.assets()!!.size)
        assertEquals(listOf("/shelf/sync:", "/book/getprogress:recent"), requests)
    }

    @Test
    fun `服务选最近包括已读完书且周期榜单不覆盖累计或历史资产`() =
        runBlocking {
            val store = WereadStore(folder.root)
            store.writeAssets(listOf(WereadAssetBook("history", "历史", "", 700, 0, 0, 30, false)))
            val provider =
                object : WereadSyncProvider {
                    override suspend fun shelf() =
                        listOf(
                            WereadShelfBook("longest", "长书", "", "", 50000, 20, false, 0, 100),
                            WereadShelfBook("recent", "新书", "", "", 100, 100, true, 1, 200),
                        )

                    override suspend fun stats(
                        mode: String,
                        baseTimeSec: Long,
                    ) = """{"readLongest":[{"book":{"bookId":"recent"},"readTime":999999}]}"""

                    override suspend fun assets(): List<WereadAssetBook>? = null

                    override suspend fun bill(
                        mode: String,
                        periodStartSec: Long,
                        statsRaw: String,
                    ): String? = null

                    override suspend fun notes(): WereadNoteBatch? = null
                }
            WereadSyncService(store, provider).sync(listOf(WereadSyncPeriod("overall", 0)))
            val current = Json.parseToJsonElement(File(folder.root, "book.json").readText()).jsonObject
            assertEquals("recent", current["bookId"]!!.jsonPrimitive.content)
            assertEquals("100", current["readingSec"]!!.jsonPrimitive.content)
            val assets =
                Json
                    .parseToJsonElement(
                        File(folder.root, "assets.json").readText(),
                    ).jsonObject["books"] as JsonArray
            assertEquals(
                "history",
                assets
                    .single()
                    .jsonObject["id"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `离线多书仅尝试一次失败请求并保留下一轮续补`() {
        shelfJson =
            """
            {"books":[{"bookId":"recent","readUpdateTime":300},
            {"bookId":"old","readUpdateTime":200},
            {"bookId":"older","readUpdateTime":100}]}
            """.trimIndent()
        progressAvailable = false
        val books = sync()
        books.shelf()
        assertTrue(books.assets()!!.isEmpty())
        assertEquals(1, requests.count { it.startsWith("/book/getprogress") })
        assertTrue(books.warnings.single().contains("3 本"))
        requests.clear()
        progressAvailable = true
        books.shelf()
        assertEquals(3, books.assets()!!.size)
        assertEquals(3, requests.count { it.startsWith("/book/getprogress") })
    }

    @Test
    fun `不支持进度的合法响应不阻断其他书`() {
        shelfJson = """{"books":[{"bookId":"recent","readUpdateTime":200},{"bookId":"old","readUpdateTime":100}]}"""
        progressJson = "{}"
        val books = sync()
        books.shelf()
        assertTrue(books.assets()!!.isEmpty())
        assertEquals(2, requests.count { it.startsWith("/book/getprogress") })
        assertTrue(books.warnings.isEmpty())
    }

    @Test
    fun `历史书补查后书架与资产均保存本轮真实进度`() =
        runBlocking {
            val bookSync = sync()
            shelfJson =
                """{"books":[{"bookId":"recent","readUpdateTime":200},{"bookId":"history","readUpdateTime":100}]}"""
            val provider =
                object : WereadSyncProvider {
                    override suspend fun shelf() = bookSync.shelf()

                    override suspend fun assets() = bookSync.assets()

                    override suspend fun stats(
                        mode: String,
                        baseTimeSec: Long,
                    ): String? = null

                    override suspend fun bill(
                        mode: String,
                        periodStartSec: Long,
                        statsRaw: String,
                    ): String? = null

                    override suspend fun notes(): WereadNoteBatch? = null
                }
            WereadSyncService(WereadStore(folder.root), provider).sync(emptyList(), includeAssets = true)
            val shelf =
                Json
                    .parseToJsonElement(
                        File(folder.root, "shelf.json").readText(),
                    ).jsonObject["books"] as JsonArray
            assertEquals(listOf("35", "35"), shelf.map { it.jsonObject["progress"]!!.jsonPrimitive.content })
            assertEquals(2, requests.count { it.startsWith("/book/getprogress") })
        }

    @Test
    fun `迁移期间不执行同步且异常释放同步门`() =
        runBlocking {
            val provider =
                object : WereadSyncProvider {
                    override suspend fun shelf(): List<WereadShelfBook>? = error("test failure")

                    override suspend fun stats(
                        mode: String,
                        baseTimeSec: Long,
                    ): String? = null

                    override suspend fun assets(): List<WereadAssetBook>? = null

                    override suspend fun bill(
                        mode: String,
                        periodStartSec: Long,
                        statsRaw: String,
                    ): String? = null

                    override suspend fun notes(): WereadNoteBatch? = null
                }
            val service = WereadSyncService(WereadStore(folder.root), provider)
            assertTrue(WereadDataGate.tryBeginImport())
            try {
                assertFalse(service.sync(emptyList()).success)
            } finally {
                WereadDataGate.endImport()
            }
            assertTrue(runCatching { service.sync(emptyList()) }.isFailure)
            assertTrue(WereadDataGate.tryBeginImport())
            WereadDataGate.endImport()
        }

    @Test
    fun `增量资产保留未补查的历史记录`() {
        val store = WereadStore(folder.root)
        store.writeAssets(listOf(WereadAssetBook("one", "一", "", 100, 0, 0, 20, false)))
        store.writeAssets(listOf(WereadAssetBook("two", "二", "", 200, 0, 0, 50, false)))
        val raw = Json.parseToJsonElement(File(folder.root, "assets.json").readText()).jsonObject
        assertEquals(2, (raw["books"] as JsonArray).size)
    }
}
