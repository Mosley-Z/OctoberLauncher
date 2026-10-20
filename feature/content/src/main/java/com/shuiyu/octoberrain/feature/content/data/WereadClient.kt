package com.shuiyu.octoberrain.feature.content.data

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.common.WereadHighlight
import com.shuiyu.octoberrain.core.common.WereadMonth
import com.shuiyu.octoberrain.core.common.WereadShelfItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * 微信读书接口客户端。
 *
 * 通过 i.weread.qq.com 的 agent 网关读取书架、阅读统计与划线；所有请求带
 * Authorization 与 skill_version，错误码统一映射为可读日志并返回空结果。
 * 书架、统计与划线结果在内存中缓存 30 分钟，封面下载到应用私有目录。
 */
class WereadClient internal constructor(
    private val context: Context?,
    private val transport: WereadTransport,
    private val configProvider: (() -> IntegrationsConfig)?,
    private val cache: WereadCache?,
) {
    /** 生产装配入口：默认 HTTP 传输与以应用缓存目录为根的统计缓存。 */
    constructor(appContext: Context) : this(
        appContext,
        HttpWereadTransport(),
        null,
        WereadCache(File(appContext.cacheDir, "weread")),
    )

    companion object {
        private const val TAG = "OctoberRain"
        private const val ENDPOINT = "https://i.weread.qq.com/api/agent/gateway"
        private const val SKILL_VERSION = "1.0.5"
        private const val CACHE_TTL_MS = 30L * 60L * 1000L
        private const val COVER_TTL_MS = 7L * 24L * 60L * 60L * 1000L
        private const val DEFAULT_TIMEOUT_MS = 15000
        private const val PROGRESS_TIMEOUT_MS = 15000
        private const val MAX_PROGRESS_REQUESTS = 8
        private const val PROGRESS_CONCURRENCY = 2
        private const val PROGRESS_TOTAL_TIMEOUT_MS = 20000L
        private const val MAX_HIGHLIGHT_BOOKS = 3
        private const val MAX_NOTE_BOOKS = 5
        private const val IMAGE_TIMEOUT_MS = 5000
        private const val SHELF_LIMIT_HINT = 40

        private val BOOK_SCHEMES = listOf("com.tencent.weread.eink", "com.tencent.weread")
    }

    private var cachedShelfAt = 0L
    private var cachedShelf: List<WereadShelfItem> = emptyList()
    private var cachedHighlightsAt = 0L
    private var cachedHighlights: List<WereadHighlight> = emptyList()

    /** 清除接口与统计缓存，下次访问重新请求。 */
    fun invalidate() {
        cachedShelfAt = 0L
        cachedShelf = emptyList()
        cachedHighlightsAt = 0L
        cachedHighlights = emptyList()
        cache?.invalidate()
    }

    /** 是否具备可用密钥；密钥由用户配置，空值视为未配置。 */
    fun isConfigured(): Boolean = resolvedKey().isNotEmpty()

    /** 取书架原始 JSON，供数据目录同步写入原版 shelf.json。 */
    suspend fun shelfRaw(): String? =
        withContext(Dispatchers.IO) {
            val key = apiKey() ?: return@withContext null
            callGateway(key, "/shelf/sync", JSONObject())?.toString()
        }

    /**
     * 取某一周期的统计原始 JSON。
     *
     * @param mode 周期类型，取 weekly、monthly、annually、overall
     * @param baseTimeSeconds 周期起点秒数，零表示按当前时刻计算
     * @return 网关原始响应，失败返回空值
     */
    suspend fun statsRaw(
        mode: String,
        baseTimeSeconds: Long = 0L,
    ): String? =
        withContext(Dispatchers.IO) {
            val key = apiKey() ?: return@withContext null
            val period = WereadPeriod.entries.firstOrNull { it.apiMode == mode } ?: WereadPeriod.WEEKLY
            val reference = baseTimeSeconds.takeIf { it > 0L } ?: (System.currentTimeMillis() / 1000L)
            val start = WereadParsing.periodStartSeconds(period, reference)
            val payload = cachedPayload(period, start) ?: requestStats(key, period, start)
            payload?.toString()
        }

    /** 读取书架，返回书籍与专辑条目。 */
    suspend fun shelf(limit: Int = 20): List<WereadShelfItem> =
        withContext(Dispatchers.IO) {
            val config = config()
            if (!config.wereadEnabled) return@withContext emptyList()
            val key = apiKey() ?: return@withContext emptyList()
            val now = System.currentTimeMillis()
            if (cachedShelf.isNotEmpty() && now - cachedShelfAt < CACHE_TTL_MS) {
                return@withContext cachedShelf.take(limit)
            }
            val payload = callGateway(key, "/shelf/sync", JSONObject()) ?: return@withContext cachedShelf.take(limit)
            val parsed = parseShelf(payload)
            if (parsed.isEmpty()) {
                cachedShelf = emptyList()
                cachedShelfAt = now
                return@withContext emptyList()
            }
            val withProgress = attachProgress(key, parsed)
            cachedShelf = withProgress
            cachedShelfAt = System.currentTimeMillis()
            withProgress.take(limit)
        }

    /** 读取本月与上月阅读统计，密钥缺失或请求失败返回 null。 */
    suspend fun month(): WereadMonth? =
        withContext(Dispatchers.IO) {
            val key = apiKey() ?: return@withContext null
            val current = fetchMonth(key, offsetMonths = 0) ?: return@withContext null
            val previous = fetchMonth(key, offsetMonths = -1)
            val calendar = Calendar.getInstance()
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val dailyMinutes = ArrayList<Int>(daysInMonth)
            for (day in 1..daysInMonth) {
                dailyMinutes.add(((current[day] ?: 0L) / 60L).toInt())
            }
            val monthMinutes = (current.values.sum() / 60L).toInt()
            val lastMonthMinutes = (previous?.values?.sum() ?: 0L) / 60L
            WereadMonth(
                monthMinutes = monthMinutes,
                lastMonthMinutes = lastMonthMinutes.toInt(),
                days = dailyMinutes,
            )
        }

    /** 读取本周逐日统计，baseTimeSeconds 为 0 时取当前周。 */
    suspend fun weekly(baseTimeSeconds: Long = 0L): WereadStats? = stats(WereadPeriod.WEEKLY, baseTimeSeconds)

    /** 读取本年逐月统计，baseTimeSeconds 为 0 时取当前年。 */
    suspend fun annually(baseTimeSeconds: Long = 0L): WereadStats? = stats(WereadPeriod.ANNUALLY, baseTimeSeconds)

    /** 读取累计统计，周期起点固定为 0。 */
    suspend fun overall(): WereadStats? = stats(WereadPeriod.OVERALL, 0L)

    /**
     * 分页读取笔记。
     *
     * 先请求笔记本概览，再为当前页最多五本书拉取划线与个人想法；游标为上一页
     * 最后一本书的排序值，无更多数据时返回 null。
     */
    suspend fun notes(
        limit: Int = 20,
        cursor: String? = null,
    ): WereadNotePage =
        withContext(Dispatchers.IO) {
            val key = apiKey() ?: return@withContext WereadNotePage()
            val pageSize = limit.coerceIn(1, 50)
            val business =
                buildJsonObject {
                    put("count", pageSize)
                    cursor?.toLongOrNull()?.let { put("lastSort", it) }
                }
            val overview = requestGateway(key, "/user/notebooks", business) ?: return@withContext WereadNotePage()
            val entries = WereadParsing.notebookEntries(overview)
            val collected = ArrayList<WereadNote>()
            for (entry in entries.take(MAX_NOTE_BOOKS)) {
                if (collected.size >= pageSize) break
                collected.addAll(fetchNotes(key, entry))
            }
            WereadNotePage(
                notes = collected.sortedByDescending { it.createdAt }.take(pageSize),
                nextCursor = if (WereadParsing.hasMore(overview)) entries.lastOrNull()?.sort?.toString() else null,
                total = WereadParsing.totalNotes(overview),
            )
        }

    /** 汇总统计请求，优先读取按周期分键的缓存。 */
    private suspend fun stats(
        period: WereadPeriod,
        baseTimeSeconds: Long,
    ): WereadStats? =
        withContext(Dispatchers.IO) {
            val key = apiKey() ?: return@withContext null
            val reference = baseTimeSeconds.takeIf { it > 0L } ?: (System.currentTimeMillis() / 1000L)
            val start = WereadParsing.periodStartSeconds(period, reference)
            val payload = cachedPayload(period, start) ?: requestStats(key, period, start)
            payload?.let { WereadParsing.stats(period, it, start) }
        }

    /** 读取统计缓存中的响应对象。 */
    private fun cachedPayload(
        period: WereadPeriod,
        start: Long,
    ): JsonObject? =
        cache?.get(period.apiMode, start)?.let { text ->
            runCatching { AppJson.format.parseToJsonElement(text) as? JsonObject }.getOrNull()
        }

    /** 请求统计接口并写入缓存。 */
    private fun requestStats(
        key: String,
        period: WereadPeriod,
        start: Long,
    ): JsonObject? {
        val business =
            buildJsonObject {
                put("mode", period.apiMode)
                put("baseTime", if (period == WereadPeriod.OVERALL) 0L else start)
            }
        return requestGateway(key, "/readdata/detail", business)?.also { payload ->
            cache?.put(period.apiMode, start, payload.toString())
        }
    }

    /** 拉取单本书的划线与想法并合并。 */
    private fun fetchNotes(
        key: String,
        entry: NotebookEntry,
    ): List<WereadNote> {
        val marks =
            requestGateway(
                key,
                "/book/bookmarklist",
                buildJsonObject { put("bookId", entry.bookId) },
                PROGRESS_TIMEOUT_MS,
            )
        val reviews =
            requestGateway(
                key,
                "/review/list/mine",
                buildJsonObject {
                    put("bookid", entry.bookId)
                    put("count", MAX_NOTE_BOOKS)
                },
                PROGRESS_TIMEOUT_MS,
            )
        return WereadParsing.notes(entry, marks, reviews)
    }

    /** 新版网关请求：使用 kotlinx.serialization 构建与校验响应。 */
    private fun requestGateway(
        apiKey: String,
        apiName: String,
        business: JsonObject,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): JsonObject? {
        val body =
            buildJsonObject {
                put("api_name", apiName)
                put("skill_version", SKILL_VERSION)
                business.forEach { (name, value) -> put(name, value) }
            }
        val response = transport.post(ENDPOINT, "Bearer $apiKey", body.toString(), timeoutMs) ?: return null
        return validateGateway(apiName, response)
    }

    /** 校验 HTTP 状态与业务错误码，返回解析后的对象。 */
    @Suppress("ReturnCount")
    private fun validateGateway(
        apiName: String,
        response: RawResponse,
    ): JsonObject? {
        if (response.status == 401) {
            InkLog.w(TAG, "微信读书密钥无效或已过期")
            return null
        }
        if (response.status !in 200..299) {
            InkLog.w(TAG, "微信读书接口 $apiName 返回 HTTP ${response.status}")
            return null
        }
        val json = runCatching { AppJson.format.parseToJsonElement(response.body) as? JsonObject }.getOrNull()
        if (json == null) {
            InkLog.w(TAG, "微信读书接口 $apiName 响应无法解析")
            return null
        }
        val code = if (json.containsKey("errcode")) json.intOrZero("errcode") else json.intOrZero("code")
        if (code == 0) return json
        if (code == -2012 || code == 401) {
            InkLog.w(TAG, "微信读书密钥无效：$code")
        } else {
            InkLog.w(TAG, "微信读书接口 $apiName 返回错误码 $code")
        }
        return null
    }

    /** 读取划线，按最近时间优先返回。 */
    suspend fun highlights(limit: Int = 5): List<WereadHighlight> =
        withContext(Dispatchers.IO) {
            val key = apiKey() ?: return@withContext emptyList()
            val now = System.currentTimeMillis()
            if (cachedHighlights.isNotEmpty() && now - cachedHighlightsAt < CACHE_TTL_MS) {
                return@withContext cachedHighlights.take(limit)
            }
            val payload =
                callGateway(key, "/user/notebooks", JSONObject().apply { put("count", 10) })
                    ?: return@withContext cachedHighlights.take(limit)
            val notebooks = payload.optJSONArray("books") ?: payload.optJSONArray("notebooks")
            if (notebooks == null) return@withContext emptyList()
            val highlights = ArrayList<WereadHighlight>()
            val inspected = minOf(notebooks.length(), MAX_HIGHLIGHT_BOOKS)
            for (index in 0 until inspected) {
                val notebook = notebooks.optJSONObject(index) ?: continue
                val bookId = notebook.optString("bookId").ifBlank { notebook.optString("book_id") }
                if (bookId.isBlank()) continue
                val bookTitle =
                    notebook
                        .optJSONObject("book")
                        ?.optString("title")
                        .orEmpty()
                        .ifBlank { notebook.optString("bookTitle") }
                val marks =
                    callGateway(
                        key,
                        "/book/bookmarklist",
                        JSONObject().apply { put("bookId", bookId) },
                        PROGRESS_TIMEOUT_MS,
                    ) ?: continue
                val array =
                    marks.optJSONArray("updated") ?: marks.optJSONArray("chapters")
                        ?: marks.optJSONArray("bookmarks") ?: continue
                for (markIndex in 0 until array.length()) {
                    val mark = array.optJSONObject(markIndex) ?: continue
                    val text = mark.optString("markText").ifBlank { mark.optString("text") }
                    if (text.isBlank()) continue
                    highlights.add(
                        WereadHighlight(
                            text = text,
                            bookTitle = bookTitle,
                            createdAt = normalizeTimestamp(mark.optLong("createTime", 0L)),
                        ),
                    )
                    if (highlights.size >= limit) break
                }
                if (highlights.size >= limit) break
            }
            val ordered = highlights.sortedByDescending { it.createdAt }.take(limit)
            cachedHighlights = ordered
            cachedHighlightsAt = System.currentTimeMillis()
            ordered
        }

    /** 读取整合设置，测试环境可注入固定配置。 */
    private fun config(): IntegrationsConfig = configProvider?.invoke() ?: ContentPaths.integrations()

    /**
     * 返回当前生效的密钥。
     *
     * 密钥由用户填写，空值表示未配置；调用方在空密钥时跳过请求。
     */
    private fun resolvedKey(): String = config().wereadApiKey.trim()

    /** 返回可用密钥：集成关闭或未配置时返回 null。 */
    private fun apiKey(): String? {
        if (!config().wereadEnabled) return null
        return resolvedKey().takeIf { it.isNotEmpty() }
    }

    /** 解析书架响应中的 books 与 albums 数组，并按最近阅读时间降序排序。 */
    private fun parseShelf(payload: JSONObject): List<WereadShelfItem> {
        val entries = ArrayList<ShelfEntry>()
        val books = payload.optJSONArray("books")
        if (books != null) {
            for (index in 0 until minOf(books.length(), SHELF_LIMIT_HINT)) {
                val item = books.optJSONObject(index) ?: continue
                val bookId = item.optString("bookId").ifBlank { item.optString("book_id") }
                if (bookId.isBlank()) continue
                entries.add(
                    ShelfEntry(
                        item =
                            WereadShelfItem(
                                bookId = bookId,
                                title = item.optString("title").ifBlank { "未命名书籍" },
                                author = item.optString("author"),
                                coverPath = downloadCover(item.optString("cover"), bookId),
                                progress = readProgress(item),
                                deepLink = deepLink(bookId),
                            ),
                        sortKey = item.optLong("readUpdateTime", 0L),
                    ),
                )
            }
        }
        val albums = payload.optJSONArray("albums")
        if (albums != null) {
            for (index in 0 until minOf(albums.length(), SHELF_LIMIT_HINT)) {
                val item = albums.optJSONObject(index) ?: continue
                val info = item.optJSONObject("albumInfo")
                val extra = item.optJSONObject("albumInfoExtra") ?: info?.optJSONObject("albumInfoExtra")
                val albumId =
                    info
                        ?.optString("albumId")
                        .orEmpty()
                        .ifBlank { item.optString("albumId") }
                        .ifBlank { item.optString("bookId") }
                if (albumId.isBlank()) continue
                entries.add(
                    ShelfEntry(
                        item =
                            WereadShelfItem(
                                bookId = albumId,
                                title =
                                    info
                                        ?.optString("name")
                                        .orEmpty()
                                        .ifBlank { item.optString("name") }
                                        .ifBlank { item.optString("title") }
                                        .ifBlank { "未命名专辑" },
                                author =
                                    info
                                        ?.optString("authorName")
                                        .orEmpty()
                                        .ifBlank { item.optString("authorName") }
                                        .ifBlank { item.optString("author") },
                                coverPath =
                                    downloadCover(
                                        info?.optString("cover").orEmpty().ifBlank { item.optString("cover") },
                                        albumId,
                                    ),
                                progress = readProgress(item),
                                deepLink = deepLink(albumId),
                            ),
                        sortKey =
                            extra?.optLong("lectureReadUpdateTime", 0L)
                                ?: item.optLong("lectureReadUpdateTime", 0L),
                    ),
                )
            }
        }
        // 有阅读或收听记录的条目按时间降序排在前面，无记录的排最后，同一时间按书名排序。
        return entries
            .sortedWith(
                compareByDescending<ShelfEntry> { it.sortKey > 0L }
                    .thenByDescending { it.sortKey }
                    .thenBy { it.item.title },
            ).map { it.item }
    }

    /** 书架解析中间结果：条目与最近阅读/收听时间，仅用于排序。 */
    private data class ShelfEntry(
        val item: WereadShelfItem,
        val sortKey: Long,
    )

    /**
     * 为书架前若干本缺少进度的书籍补齐进度。
     *
     * 最多请求前八本，每批并发两本，整体从开始到结束最多等待二十秒；超时后
     * 保留已经取到的进度，未完成的条目按零进度返回，避免页面长时间停留。
     */
    private suspend fun attachProgress(
        key: String,
        items: List<WereadShelfItem>,
    ): List<WereadShelfItem> {
        if (items.isEmpty()) return items
        val result = items.toMutableList()
        val targets =
            result
                .withIndex()
                .filter { it.value.progress <= 0f }
                .take(MAX_PROGRESS_REQUESTS)
        if (targets.isEmpty()) return result
        val deadline = System.currentTimeMillis() + PROGRESS_TOTAL_TIMEOUT_MS
        for (chunk in targets.chunked(PROGRESS_CONCURRENCY)) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) break
            val updates =
                withTimeoutOrNull(remaining) {
                    coroutineScope {
                        chunk
                            .map { (index, item) ->
                                async(Dispatchers.IO) {
                                    val progress = fetchProgress(key, item.bookId)
                                    if (progress > 0f) index to progress else null
                                }
                            }.awaitAll()
                    }
                } ?: break
            for (update in updates) {
                if (update == null) continue
                result[update.first] = result[update.first].copy(progress = update.second)
            }
        }
        return result
    }

    /** 请求单本书的阅读进度，失败或解析不到时返回零。 */
    private fun fetchProgress(
        key: String,
        bookId: String,
    ): Float {
        val payload =
            callGateway(
                key,
                "/book/getprogress",
                JSONObject().apply { put("bookId", bookId) },
                PROGRESS_TIMEOUT_MS,
            ) ?: return 0f
        return readProgress(payload)
    }

    /**
     * 读取书籍进度。
     *
     * 兼容四种响应结构：顶层 readingProgress.progress、顶层 progress、book 对象内
     * 的 readingProgress.progress 与 book.progress；数值按百分比换算为 0 到 1。
     */
    private fun readProgress(payload: JSONObject): Float {
        val book = payload.optJSONObject("book")
        val raw =
            payload
                .optJSONObject("readingProgress")
                ?.takeIf { it.has("progress") }
                ?.optDouble("progress", 0.0)
                ?: book
                    ?.optJSONObject("readingProgress")
                    ?.takeIf { it.has("progress") }
                    ?.optDouble("progress", 0.0)
                ?: if (payload.has("progress")) {
                    payload.optDouble("progress", 0.0)
                } else {
                    null
                        ?: book?.takeIf { it.has("progress") }?.optDouble("progress", 0.0)
                        ?: 0.0
                }
        return (raw / 100.0).coerceIn(0.0, 1.0).toFloat()
    }

    /** 读取单月每日阅读秒数，失败返回 null。 */
    private fun fetchMonth(
        key: String,
        offsetMonths: Int,
    ): Map<Int, Long>? {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, offsetMonths)
        val business =
            JSONObject().apply {
                put("mode", "monthly")
                put("month", String.format("%04d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1))
                put("beginTime", startOfMonthSeconds(offsetMonths))
                put("endTime", startOfNextMonthSeconds(offsetMonths))
            }
        val payload = callGateway(key, "/readdata/detail", business) ?: return null
        return extractDailySeconds(payload)
    }

    /**
     * 从统计响应中提取按日秒数，兼容字典与数组两种结构。
     *
     * 字典的键可能是当月第几日，也可能是当日零点的 Unix 秒级时间戳；两种形式
     * 都换算为月内日期，避免真实接口以时间戳为键时整月数据被丢弃。
     */
    private fun extractDailySeconds(payload: JSONObject): Map<Int, Long> {
        val result = HashMap<Int, Long>()
        val dictionary = payload.optJSONObject("readTimes") ?: payload.optJSONObject("daily")
        if (dictionary != null) {
            val keys = dictionary.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val day = resolveDay(key) ?: continue
                val seconds = normalizeSeconds(dictionary.optLong(key, 0L))
                if (seconds > 0L) result[day] = (result[day] ?: 0L) + seconds
            }
        }
        val array =
            payload.optJSONArray("readTimes") ?: payload.optJSONArray("days")
                ?: payload.optJSONArray("data")
        if (array != null) {
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val day = entry.optInt("day", entry.optInt("date", 0))
                if (day !in 1..31) continue
                val seconds =
                    normalizeSeconds(
                        entry.optLong("readTime", entry.optLong("seconds", entry.optLong("duration", 0L))),
                    )
                if (seconds > 0L) result[day] = (result[day] ?: 0L) + seconds
            }
        }
        return result
    }

    /** 统一秒与毫秒单位。 */
    private fun normalizeSeconds(value: Long): Long = if (value > 100_000L) value / 1000L else value

    /**
     * 将统计字典的键换算为月内日期。
     *
     * 纯数字且大于一亿视为 Unix 秒级时间戳，按本地时区取当日日期；其余按
     * “当月第几日”或“年-月-日”格式解析；无法识别时返回 null。
     */
    private fun resolveDay(key: String): Int? {
        val numeric = key.toLongOrNull()
        if (numeric != null) {
            if (numeric > 100_000_000L) {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = numeric * 1000L
                return calendar.get(Calendar.DAY_OF_MONTH)
            }
            return numeric.toInt().takeIf { it in 1..31 }
        }
        return key.substringAfterLast('-').toIntOrNull()?.takeIf { it in 1..31 }
    }

    /** 统一秒与毫秒时间戳。 */
    private fun normalizeTimestamp(value: Long): Long = if (value in 1..100_000_000_000L) value * 1000L else value

    /** 返回所在月份起始时间的秒数。 */
    private fun startOfMonthSeconds(offsetMonths: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, offsetMonths)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / 1000L
    }

    /** 返回所在月份下一月份起始时间的秒数。 */
    private fun startOfNextMonthSeconds(offsetMonths: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, offsetMonths + 1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / 1000L
    }

    /** 下载封面到私有目录并返回本地路径，失败返回 null。 */
    private fun downloadCover(
        url: String,
        bookId: String,
    ): String? {
        if (url.isBlank() || bookId.isBlank()) return null
        val appContext = context ?: return null
        val directory = ContentPaths.filesDirectory(appContext, "weread_covers")
        val target = File(directory, "${sanitize(bookId)}.img")
        if (target.isFile &&
            target.length() > 0L &&
            System.currentTimeMillis() - target.lastModified() < COVER_TTL_MS
        ) {
            return target.absolutePath
        }
        val connection =
            try {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = IMAGE_TIMEOUT_MS
                    readTimeout = IMAGE_TIMEOUT_MS
                }
            } catch (error: IOException) {
                InkLog.w(TAG, "封面地址无法解析：${error.message}")
                return null
            }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() > 0L) target.absolutePath else null
        } catch (error: IOException) {
            InkLog.w(TAG, "封面下载失败：${error.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    /** 生成文件名安全的标识。 */
    private fun sanitize(value: String): String =
        value
            .map { char -> if (char.isLetterOrDigit() || char == '-' || char == '_') char else '_' }
            .joinToString("")

    /** 生成微信读书阅读跳转链接。 */
    private fun deepLink(bookId: String): String = "weread://reading?bId=$bookId"

    /** 返回微信读书客户端包名清单，供宿主选择打开方式。 */
    fun clientPackages(): List<String> = BOOK_SCHEMES

    /** 发起网关请求并校验业务错误码。 */
    private fun callGateway(
        apiKey: String,
        apiName: String,
        business: JSONObject,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): JSONObject? {
        val body =
            JSONObject().apply {
                put("api_name", apiName)
                put("skill_version", SKILL_VERSION)
                val keys = business.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, business.get(key))
                }
            }
        val response = transport.post(ENDPOINT, "Bearer $apiKey", body.toString(), timeoutMs) ?: return null
        if (response.status == 401) {
            InkLog.w(TAG, "微信读书密钥无效或已过期")
            return null
        }
        if (response.status !in 200..299) {
            InkLog.w(TAG, "微信读书接口 $apiName 返回 HTTP ${response.status}")
            return null
        }
        val json =
            runCatching { JSONObject(response.body) }.getOrElse {
                InkLog.w(TAG, "微信读书接口 $apiName 响应无法解析")
                return null
            }
        val code =
            when {
                json.has("errcode") -> json.optInt("errcode", 0)
                json.has("code") -> json.optInt("code", 0)
                else -> 0
            }
        if (code != 0) {
            if (code == -2012 || code == 401) {
                InkLog.w(TAG, "微信读书密钥无效：$code")
            } else {
                InkLog.w(TAG, "微信读书接口 $apiName 返回错误码 $code")
            }
            return null
        }
        return json
    }
}
