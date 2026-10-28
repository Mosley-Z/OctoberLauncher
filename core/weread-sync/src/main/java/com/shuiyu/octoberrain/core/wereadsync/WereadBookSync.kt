package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 * 书架元数据与逐书累计进度；同一轮书架、资产共享请求结果。
 * 私有函数包含同一份磁盘协议的字段解析与缓存读写，保留在此避免跨类共享可变缓存。
 */
@Suppress("TooManyFunctions")
class WereadBookSync(
    private val request: WereadRequest,
    private val root: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val cache = readCache().toMutableMap()
    private val requested = mutableSetOf<String>()
    private val failures = mutableSetOf<String>()
    private var strictProgress = false
    private var cacheBatchSize = 1
    private var pendingProgress = 0
    private var networkFailed = false
    val warnings: List<String>
        get() = if (failures.isEmpty()) emptyList() else listOf("${failures.size} 本书进度暂未取得，已保留缓存或未知状态")

    private var shelfSnapshot: List<WereadShelfBook>? = null

    /** 每次完整同步重新读取书架，最近在读始终补查，历史书按变化和有效期补查。 */
    fun shelf(refreshCurrent: Boolean = true): List<WereadShelfBook>? {
        requested.clear()
        cache.clear()
        cache.putAll(readCache())
        failures.clear()
        networkFailed = false
        shelfSnapshot = null
        val response = request.request("/shelf/sync", JsonObject(emptyMap()))
        val books = response?.get("books") as? JsonArray ?: return null
        val parsed = books.mapNotNull { (it as? JsonObject)?.let(::shelfBook) }
        val current = parsed.filter { it.readUpdateTime > 0 }.maxByOrNull { it.readUpdateTime }
        if (refreshCurrent) current?.let { fetch(it, force = true) }
        shelfSnapshot = parsed.map(::enrich)
        return shelfSnapshot
    }

    /** 不从阅读榜单推算累计值；失败保留持久缓存，成功结果逐书落盘便于续补。 */
    fun assets(): List<WereadAssetBook>? {
        val books = shelfSnapshot ?: shelf() ?: return null
        books.filter { it.readUpdateTime > 0 }.forEach { fetch(it, force = false) }
        return cachedAssets()
    }

    /** 资产专用手动刷新：无条数上限；逐书源版本未变时不因缓存年龄重复出网。 */
    fun refreshAssets(forceAll: Boolean = false): WereadSyncReport {
        val report = runAssetRefresh(forceAll)
        WereadAssetRefreshState.save(root, report, clock())
        return report
    }

    private fun runAssetRefresh(forceAll: Boolean): WereadSyncReport {
        val books = shelf(refreshCurrent = false) ?: return WereadSyncReport(false, "读取书架失败，已保留资产")
        val candidates =
            if (forceAll) {
                (books + historyBooks()).distinctBy {
                    it.bookId
                }
            } else {
                books.distinctBy { it.bookId }
            }
        val skipped = refreshAssetCandidates(candidates, forceAll)
        val store = WereadStore(root)
        val saved =
            store.writeShelf(books.map(::enrich), clock()) &&
                store.writeShelfIds(books.map { it.bookId }) &&
                store.writeAssets(cachedAssets())
        val pricesUpdated = refreshAssetPrices(store)
        val priceIndex = if (networkFailed) null else WereadNoteSync(request, root, clock).sync(bookIds = emptySet())
        val indexUpdated = priceIndex != null && store.writeNotes(priceIndex.indexJson, emptyList())
        val success = saved && failures.isEmpty() && pricesUpdated && indexUpdated
        val detail = "范围 ${candidates.size} 本，查询 ${requested.size} 本，复用 $skipped 本"
        val message =
            if (success) {
                "资产刷新完成：$detail；统计与笔记价格索引已更新；未提供价格的书仍显示未知"
            } else {
                "资产部分刷新：$detail；进度未完成 ${failures.size} 本；" +
                    "统计${if (pricesUpdated) "已更新" else "未更新"}；价格索引${if (indexUpdated) "已更新" else "未更新"}；保留旧值"
            }
        return WereadSyncReport(success, message)
    }

    private fun refreshAssetCandidates(
        candidates: List<WereadShelfBook>,
        forceAll: Boolean,
    ): Int {
        var skipped = 0
        strictProgress = true
        cacheBatchSize = 16
        try {
            for (book in candidates) {
                val previous = cache[book.bookId]
                val unchanged =
                    previous != null &&
                        previous.long("at") > 0 &&
                        book.readUpdateTime > 0 &&
                        previous.long("readUpdateTime") == book.readUpdateTime &&
                        (previous.long("fin") > 0) == book.finished
                if (!forceAll && unchanged) skipped++ else fetch(book, force = true)
            }
        } finally {
            if (pendingProgress > 0) saveCache()
            pendingProgress = 0
            cacheBatchSize = 1
            strictProgress = false
        }
        return skipped
    }

    private fun historyBooks(): List<WereadShelfBook> =
        cachedAssets().map { asset ->
            WereadShelfBook(
                asset.bookId,
                asset.title,
                asset.author,
                "",
                asset.readingSeconds,
                asset.progress,
                asset.finished,
                asset.finishTimeMillis,
                asset.readUpdateTime,
            )
        }

    /** 原价链来自已有账单/统计及手填价，getprogress自身不包含价格。只补一次统计，不逐书猜价。 */
    private fun refreshAssetPrices(store: WereadStore): Boolean {
        val payload =
            if (networkFailed) {
                null
            } else {
                request.request(
                    "/readdata/detail",
                    buildJsonObject {
                        put("mode", "overall")
                        put("baseTime", 0)
                    },
                )
            }
        val valid = payload?.keys?.any { it in setOf("readLongest", "totalReadTime", "readTimes") } == true
        return payload != null && valid && store.writeStats("overall", 0L, payload.toString(), clock())
    }

    /** 复用已补查的累计值，不产生网络请求。 */
    fun cachedAssets(): List<WereadAssetBook> =
        cache.mapNotNull { (id, value) -> asset(id, value) }.sortedByDescending { it.readingSeconds }

    /** 卡片只补当前选中书，不启动整库资产预热。 */
    fun currentBook(selectedId: String): Pair<List<WereadShelfBook>, WereadShelfBook?>? {
        val books = shelf() ?: return null
        val selected =
            books.firstOrNull { it.bookId == selectedId }
                ?: books.filter { it.readUpdateTime > 0 }.maxByOrNull { it.readUpdateTime }
        selected?.let { fetch(it, force = true) }
        return books.map(::enrich) to selected?.let(::enrich)
    }

    /** Refresh only the candidate carousel, retaining full shelf metadata and cached assets. */
    fun recentBooks(
        selectedId: String,
        force: Boolean,
    ): List<WereadShelfBook>? {
        val books = shelf() ?: return null
        val candidates = books.sortedByDescending { it.readUpdateTime }.distinctBy { it.bookId }.take(10)
        candidates.forEach { fetch(it, force) }
        val enriched = books.map(::enrich)
        val ids = candidates.map { it.bookId }.toSet()
        val saved = storeRecent(enriched, selectedId, ids)
        return enriched.filter { it.bookId in ids }.takeIf { saved }
    }

    private fun storeRecent(
        books: List<WereadShelfBook>,
        selectedId: String,
        ids: Set<String>,
    ): Boolean {
        val store = WereadStore(root)
        return store.writeShelf(books, clock()) &&
            store.writeShelfIds(books.map { it.bookId }) &&
            store.writeAssets(cachedAssets()) &&
            store.writeCurrentBook(
                books.firstOrNull { it.bookId == selectedId && it.bookId in ids }
                    ?: books.filter { it.bookId in ids }.maxByOrNull { it.readUpdateTime },
            )
    }

    private fun fetch(
        book: WereadShelfBook,
        force: Boolean,
    ) {
        if (!needsFetch(book, force)) return
        // 首次传输或鉴权失败后立即停止出网，剩余待补保留到下一轮。
        if (networkFailed) {
            failures += book.bookId
        } else {
            requested += book.bookId
            val business = buildJsonObject { put("bookId", book.bookId) }
            val response = request.request("/book/getprogress", business)
            if (response == null) {
                networkFailed = true
                failures += book.bookId
            } else {
                // 公众号、听书等合法响应可能没有进度，不阻断其他书。
                val payload = response["book"] as? JsonObject
                val supported = payload?.keys?.any { it in PROGRESS_FIELDS } == true
                val complete = !strictProgress || payload?.let(::cumulativeTimeKnown) == true
                if (payload != null && supported && complete) {
                    saveProgress(book, payload)
                } else if (strictProgress) {
                    failures += book.bookId
                }
            }
        }
    }

    /** 资产完整统计不能把接口未提供的累计时长当作零覆盖缓存。 */
    private fun cumulativeTimeKnown(payload: JsonObject): Boolean =
        listOf("readingTime", "recordReadingTime").any { key ->
            (payload[key]?.jsonPrimitive?.intOrNull ?: -1) >= 0
        }

    private fun needsFetch(
        book: WereadShelfBook,
        force: Boolean,
    ): Boolean {
        val previous = cache[book.bookId]
        val fresh =
            previous != null &&
                previous.long("at") > 0 &&
                clock() - previous.long("at") in 0..CACHE_TTL &&
                previous.long("readUpdateTime") == book.readUpdateTime
        return book.bookId !in requested && (force || !fresh)
    }

    private fun saveProgress(
        book: WereadShelfBook,
        payload: JsonObject,
    ) {
        val seconds = payload.int("readingTime").takeIf { it > 0 } ?: payload.int("recordReadingTime")
        val finish = timestampMillis(payload.long("finishTime"))
        val progress = payload.int("progress", -1).takeIf { it in 0..100 } ?: -1
        val record =
            buildJsonObject {
                put("title", book.title)
                put("author", book.author)
                put("sec", seconds.coerceAtLeast(0))
                put("pct", progress)
                put("fin", if (book.finished || progress == 100) maxOf(finish, book.finishTimeMillis, 1L) else finish)
                put("at", clock())
                put("readUpdateTime", book.readUpdateTime)
            }
        cache[book.bookId] = record
        pendingProgress++
        if (pendingProgress >= cacheBatchSize) {
            saveCache()
            pendingProgress = 0
        }
    }

    private fun enrich(book: WereadShelfBook): WereadShelfBook {
        val progress = cache[book.bookId] ?: return book
        return book.copy(
            readingSeconds = progress.int("sec"),
            progress = progress.int("pct", -1),
            finished = book.finished || progress.long("fin") > 0,
            finishTimeMillis = maxOf(book.finishTimeMillis, progress.long("fin")),
        )
    }

    private fun shelfBook(item: JsonObject): WereadShelfBook? {
        val id = item.text("bookId")
        if (id.isBlank()) return null
        val finish = timestampMillis(item.long("finishTime"))
        return WereadShelfBook(
            bookId = id,
            title = item.text("title"),
            author = item.text("author"),
            coverUrl = item.text("cover"),
            readingSeconds = 0,
            progress = -1,
            finished = item.int("finishReading") == 1 || finish > 0,
            finishTimeMillis = finish,
            readUpdateTime = item.long("readUpdateTime"),
            deepLink = item.text("readerLink").ifBlank { item.text("deepLink") },
        )
    }

    private fun asset(
        id: String,
        value: JsonObject,
    ): WereadAssetBook? =
        WereadAssetBook(
            bookId = id,
            title = value.text("title"),
            author = value.text("author"),
            readingSeconds = value.int("sec"),
            priceFen = 0,
            paidFen = 0,
            progress = value.int("pct", -1),
            finished = value.long("fin") > 0,
            finishTimeMillis = value.long("fin"),
            fetchedAtMillis = value.long("at"),
            readUpdateTime = value.long("readUpdateTime"),
        )

    /** 升级首次请求失败时仍使用已迁移的资产和本书；旧记录没有有效期，下一轮继续补查。 */
    private fun readCache(): Map<String, JsonObject> {
        val result = LinkedHashMap<String, JsonObject>()
        val assets = readObject("assets.json")?.get("books") as? JsonArray
        assets.orEmpty().forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val id = item.text("id")
            if (id.isNotBlank()) result[id] = item
        }
        readObject("book.json")?.let { book ->
            val id = book.text("bookId")
            if (id.isNotBlank() && id !in result) {
                result[id] =
                    buildJsonObject {
                        put("title", book.text("title"))
                        put("author", book.text("author"))
                        put("sec", book.int("readingSec"))
                        put("pct", book.int("progress", -1))
                        put("fin", book.long("finishTime"))
                    }
            }
        }
        readObject(CACHE_FILE)?.forEach { (key, value) ->
            if (value is JsonObject) result[key] = value
        }
        return result
    }

    private fun readObject(name: String): JsonObject? =
        runCatching { Json.parseToJsonElement(File(root, name).readText()) as? JsonObject }.getOrNull()

    private fun saveCache() {
        // 独立缓存不含密钥；临时文件替换避免中断损坏上一轮进度。
        runCatching {
            root.mkdirs()
            val target = File(root, CACHE_FILE)
            val temporary = File(root, "$CACHE_FILE.tmp")
            temporary.writeText(JsonObject(cache).toString())
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        }
    }

    private fun JsonObject.text(key: String): String = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.int(
        key: String,
        default: Int = 0,
    ): Int = get(key)?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.long(key: String): Long = get(key)?.jsonPrimitive?.longOrNull ?: 0L

    private fun timestampMillis(value: Long): Long = if (value in 1..9_999_999_999L) value * 1000 else value

    private companion object {
        val PROGRESS_FIELDS = setOf("progress", "readingTime", "recordReadingTime", "finishTime")
        const val CACHE_FILE = "progress_cache.json"
        const val CACHE_TTL = 24 * 60 * 60 * 1000L
    }
}
