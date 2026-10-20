package com.shuiyu.octoberrain.core.wereadsync

import com.shuiyu.octoberrain.core.common.InkLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 从微信读书取得数据并转成存储模型的提供者。
 *
 * 网络协议与字段解析留在调用方，本模块只关心「写入什么」与「写到哪里」，
 * 这样解析规则改动不会牵动存储格式，存储格式可用纯单元测试覆盖。
 */
interface WereadSyncProvider {
    /** 拉取书架，失败返回空值。 */
    suspend fun shelf(): List<WereadShelfBook>?

    /** 拉取某一周期的统计原始 JSON，失败返回空值。 */
    suspend fun stats(
        mode: String,
        baseTimeSec: Long,
    ): String?

    /** 拉取阅读资产书目，失败返回空值。 */
    suspend fun assets(): List<WereadAssetBook>?

    /**
     * 把某一期的统计响应转成账单快照，失败返回空值。
     *
     * @param mode 周期类型
     * @param periodStartSec 周期起点秒数
     * @param statsRaw 同一轮已取到的统计响应
     */
    suspend fun bill(
        mode: String,
        periodStartSec: Long,
        statsRaw: String,
    ): String?

    /** 拉取笔记索引与逐篇内容，失败返回空值。 */
    suspend fun notes(): WereadNoteBatch?
}

/**
 * 一次笔记拉取的结果。
 *
 * @param indexJson 笔记索引原始 JSON
 * @param notes 逐篇笔记文件
 */
data class WereadNoteBatch(
    val indexJson: String,
    val notes: List<WereadNoteFile>,
)

/**
 * 同步结果。
 *
 * @param success 是否全部写入完成
 * @param message 面向界面的说明
 * @param shelfCount 写入的书目数量
 * @param statCount 写入的统计周期数量
 * @param assetCount 写入的资产书目数量
 * @param billCount 写入的账单期数
 * @param noteCount 写入的笔记篇数
 */
data class WereadSyncReport(
    val success: Boolean,
    val message: String,
    val shelfCount: Int = 0,
    val statCount: Int = 0,
    val assetCount: Int = 0,
    val billCount: Int = 0,
    val noteCount: Int = 0,
)

/**
 * 把提供者的数据编排为内核可读的数据目录，并记录同步状态。
 *
 * 同步顺序固定为书架、统计、账单、资产、笔记：书架最先写入，界面在后续步骤完成前
 * 就能显示书目；每一步失败都不阻断后续步骤，最终以报告形式汇总。
 *
 * @param store 数据目录写入器
 * @param provider 数据提供者
 * @param clock 当前时刻，便于测试注入
 */
@Suppress("TooManyFunctions")
class WereadSyncService(
    private val store: WereadStore,
    private val provider: WereadSyncProvider,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** 同步互斥锁：偏好与账单文件是「读整档、合并、写回」，并发会互相覆盖。 */
    private val mutex = Mutex()

    /**
     * 执行一次完整同步。
     *
     * @param bases 需要拉取的周期起点，按周期类型给出；缺省只取当期与全时段
     * @return 同步报告
     */
    suspend fun sync(bases: List<WereadSyncPeriod> = defaultPeriods()): WereadSyncReport =
        mutex.withLock {
            // 同步包含网络请求与多次整档写入，调用方可能在主线程协程中等待；
            // 统一切到 IO 调度器，避免周期数量增多后阻塞界面。
            withContext(Dispatchers.IO) { runSync(bases) }
        }

    /**
     * 串行执行同步步骤。
     *
     * 存储层的偏好文件与账单文件采用「读整档、合并、写回」的方式更新，两次同步并发
     * 执行会互相覆盖对方的键，因此整个流程加互斥锁；账单一律复用同一轮已取到的统计
     * 响应，既省一次网络请求，也避免两次请求落在不同周期上。
     *
     * @param bases 需要拉取的周期起点
     * @return 同步报告
     */
    private suspend fun runSync(bases: List<WereadSyncPeriod>): WereadSyncReport {
        val started = clock()
        val shelf = provider.shelf()
        val stats = fetchStats(bases)
        val times = longestByBook(stats)
        val shelfCount = writeShelf(shelf, times)
        val statCount = writeStats(stats)
        val billCount = writeBills(bases, stats)
        val assetCount = writeAssets(shelf, stats)
        val noteCount = writeNotes()
        val total = shelfCount + statCount + billCount + assetCount + noteCount
        val success = total > 0
        val message =
            if (success) {
                "同步完成：书架 $shelfCount 本、统计 $statCount 期、账单 $billCount 期、资产 $assetCount 本、笔记 $noteCount 篇"
            } else {
                "同步未取得数据"
            }
        store.putString(KEY_SYNC_STATE, stateJson(success, message, started))
        InkLog.d(TAG, "微读同步结束：$message")
        return WereadSyncReport(
            success = success,
            message = message,
            shelfCount = shelfCount,
            statCount = statCount,
            assetCount = assetCount,
            billCount = billCount,
            noteCount = noteCount,
        )
    }

    /** 一次取回所有周期的统计响应，键为「周期类型:起点秒数」。 */
    private suspend fun fetchStats(bases: List<WereadSyncPeriod>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val started = clock()
        for (period in bases) {
            if (exceededBudget(started, bases.size - result.size)) break
            val raw = provider.stats(period.mode, period.baseTimeSec)
            if (raw != null) result[periodKey(period)] = raw
        }
        return result
    }

    /**
     * 判断本轮取数是否已超出时间预算。
     *
     * @param started 本轮开始时刻，毫秒
     * @param remaining 尚未取回的周期数
     * @return 超出预算时返回真，调用方据此结束循环
     */
    private fun exceededBudget(
        started: Long,
        remaining: Int,
    ): Boolean {
        val over = clock() - started > MAX_FETCH_MILLIS
        if (over) InkLog.w(TAG, "同步周期过多，已跳过剩余 $remaining 期")
        return over
    }

    /** 按预先取到的统计响应写入各周期，返回写入数量。 */
    private fun writeStats(stats: Map<String, String>): Int {
        var count = 0
        for ((key, raw) in stats) {
            val period = key.split(':')
            val mode = period.first()
            val base = period.last().toLongOrNull() ?: 0L
            store.writeStats(mode, base, raw, clock())
            count += 1
        }
        return count
    }

    /** 周期在缓存中的键。 */
    private fun periodKey(period: WereadSyncPeriod): String = "${period.mode}:${period.baseTimeSec}"

    /**
     * 写入书架与当前书目，返回写入数量。
     *
     * 网关书架响应不带逐书时长，时长只在统计的阅读榜单里出现，因此先用统计
     * 合并出时长再落盘，「本书」卡片才能显示真实的阅读时长。
     *
     * @param shelf 书架条目
     * @param times 书目标识到累计秒数的映射
     * @return 写入数量
     */
    private suspend fun writeShelf(
        shelf: List<WereadShelfBook>?,
        times: Map<String, Int>,
    ): Int {
        if (shelf.isNullOrEmpty()) return 0
        val enriched =
            shelf.map { book ->
                book.copy(readingSeconds = times[book.bookId] ?: book.readingSeconds)
            }
        store.writeShelf(enriched, clock())
        store.writeShelfIds(enriched.map { book -> book.bookId })
        store.writeCurrentBook(currentOf(enriched, times))
        return enriched.size
    }

    /**
     * 选出「本书」卡片要展示的书目。
     *
     * 书架顺序按加入时间而非阅读时间，直接取第一本未读完的书会落到刚加进书架、
     * 尚无阅读记录的书上。统计的阅读榜单带逐书时长，时长大于零说明确实在读，
     * 因此在有记录且未读完的书里取时长最大的一本；都没有记录时退回第一本未读完的书。
     *
     * @param shelf 已合并时长的书架条目
     * @param times 书目标识到累计秒数的映射
     * @return 当前书目，书架为空时返回 null
     */
    private fun currentOf(
        shelf: List<WereadShelfBook>,
        times: Map<String, Int>,
    ): WereadShelfBook? {
        val reading = shelf.filter { book -> !book.finished && (times[book.bookId] ?: 0) > 0 }
        return reading.maxByOrNull { book -> times[book.bookId] ?: 0 }
            ?: shelf.firstOrNull { book -> !book.finished }
    }

    /** 写入各周期统计，返回写入数量。 */
    private suspend fun writeStats(bases: List<WereadSyncPeriod>): Int {
        var count = 0
        for (period in bases) {
            val raw = provider.stats(period.mode, period.baseTimeSec) ?: continue
            store.writeStats(period.mode, period.baseTimeSec, raw, clock())
            count += 1
        }
        return count
    }

    /** 写入账单快照，返回写入数量。 */
    private suspend fun writeBills(
        bases: List<WereadSyncPeriod>,
        stats: Map<String, String>,
    ): Int {
        var count = 0
        billablePeriods(bases).forEach { period ->
            val raw = stats[periodKey(period)]
            val sheet =
                raw?.let { text ->
                    runCatching { provider.bill(period.mode, period.baseTimeSec, text) }.getOrNull()
                }
            if (sheet != null) {
                store.writeBill(period.mode, period.baseTimeSec, sheet)
                count += 1
            }
        }
        return count
    }

    /** 账单只覆盖周与月两个周期，年度与全时段没有票据形态。 */
    private fun billablePeriods(bases: List<WereadSyncPeriod>): List<WereadSyncPeriod> =
        bases.filter { period ->
            period.mode == WereadStore.MODE_WEEKLY || period.mode == WereadStore.MODE_MONTHLY
        }

    /** 写入资产书目，返回写入数量。 */
    private suspend fun writeAssets(
        shelf: List<WereadShelfBook>?,
        stats: Map<String, String>,
    ): Int {
        val direct = provider.assets().orEmpty()
        val books = if (direct.isNotEmpty()) direct else shelfAssets(shelf, longestByBook(stats))
        if (books.isNotEmpty()) store.writeAssets(books)
        return books.size
    }

    /**
     * 在网关未提供资产视图时，用书架条目与各期榜单合成一份资产清单。
     *
     * 网关书架响应只有书名、作者、封面与读完标记，没有逐书时长与进度，
     * 时长改从统计响应 `readLongest` 的 `readTime` 合并，读完时刻沿用书架记录；
     * 进度留零，内核会按完成标记换算为一百。只保留有阅读时长的书目，
     * 因为原版资产页按阅读时长排序，零时长的条目既排不进榜也带不出金额。
     *
     * @param shelf 书架条目
     * @param longest 各期榜单中逐书时长，键为书目标识
     * @return 资产条目，无阅读记录时返回空表
     */
    private fun shelfAssets(
        shelf: List<WereadShelfBook>?,
        longest: Map<String, Int>,
    ): List<WereadAssetBook> =
        shelf
            .orEmpty()
            .map { book ->
                WereadAssetBook(
                    bookId = book.bookId,
                    title = book.title,
                    author = book.author,
                    readingSeconds = longest[book.bookId] ?: book.readingSeconds,
                    priceFen = 0,
                    paidFen = 0,
                    progress = book.progress,
                    finished = book.finished,
                    finishTimeMillis = book.finishTimeMillis,
                )
            }.filter { asset -> asset.readingSeconds > 0 }

    /**
     * 从各期统计响应中汇总逐书阅读时长。
     *
     * `readLongest` 每期最多十条，元素形如 `{book:{bookId,…}, readTime:秒}`，
     * 同一本书出现在多期时取最大值，避免重复累加。
     *
     * @param stats 已取到的统计响应
     * @return 书目标识到秒数的映射
     */
    private fun longestByBook(stats: Map<String, String>): Map<String, Int> {
        val result = HashMap<String, Int>()
        stats.values.forEach { raw -> collectLongest(raw, result) }
        return result
    }

    /** 把单份统计响应里的榜单条目合并进逐书时长表。 */
    private fun collectLongest(
        raw: String,
        target: MutableMap<String, Int>,
    ) {
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        val list = root?.get("readLongest") as? JsonArray ?: return
        list.forEach { element -> mergeLongestEntry(element, target) }
    }

    /** 合并单条榜单条目，缺失字段时跳过。 */
    private fun mergeLongestEntry(
        element: JsonElement,
        target: MutableMap<String, Int>,
    ) {
        val item = runCatching { element.jsonObject }.getOrNull()
        val book = item?.get("book")?.let { node -> runCatching { node.jsonObject }.getOrNull() }
        val id = book?.get("bookId")?.jsonPrimitive?.contentOrNull
        val seconds = item?.get("readTime")?.jsonPrimitive?.intOrNull ?: 0
        if (id != null && seconds > 0) target.merge(id, seconds, ::maxOf)
    }

    /** 写入笔记索引与逐篇内容，返回写入数量。 */
    private suspend fun writeNotes(): Int {
        val batch = provider.notes() ?: return 0
        store.writeNotes(batch.indexJson, batch.notes)
        return batch.notes.size
    }

    /** 生成同步状态 JSON，供设置页展示与幂等判断。 */
    private fun stateJson(
        success: Boolean,
        message: String,
        startedAt: Long,
    ): String =
        buildJsonObject {
            put("success", success)
            put("message", message)
            put("startedAtMillis", startedAt)
            put("finishedAtMillis", clock())
        }.toString()

    companion object {
        private const val TAG = "WereadSyncService"

        /** 同步状态在内核键值文件中的键名。 */
        const val KEY_SYNC_STATE: String = "weread_sync_state"

        /** 一周取数的耗时上限，超过后跳过剩余周期，保证一轮同步不超过一分钟。 */
        private const val MAX_FETCH_MILLIS: Long = 45_000L

        /** 默认同步覆盖的周数，含当前周。 */
        const val PERIOD_WEEK_COUNT: Int = 12

        /** 默认同步覆盖的月数，含当前月。 */
        const val PERIOD_MONTH_COUNT: Int = 12

        /** 默认同步覆盖的年数，含当前年。 */
        const val PERIOD_YEAR_COUNT: Int = 3

        /**
         * 默认同步范围：最近十二周、最近十二个月、最近三年与全时段。
         *
         * 周期起点必须与内核 `PeriodCalendar.startOf` 的取值一致，否则写出的键名
         * 与内核查询的键名对不上：周以周一零点为起点，月以当月一日零点为起点，
         * 年以当年一月一日零点为起点，全时段固定为零。列表按由近到远排列，
         * 截断时优先保留近期周期。
         *
         * @param nowMillis 计算基准时刻，默认取系统当前时间
         * @return 二十八期同步起点
         */
        fun defaultPeriods(nowMillis: Long = System.currentTimeMillis()): List<WereadSyncPeriod> {
            val zone = ZoneId.systemDefault()
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val monthStart = today.withDayOfMonth(1)
            val yearStart = today.withDayOfYear(1)
            val periods = ArrayList<WereadSyncPeriod>(28)
            repeat(PERIOD_WEEK_COUNT) { offset ->
                periods +=
                    WereadSyncPeriod(
                        WereadStore.MODE_WEEKLY,
                        startSec(monday.minusWeeks(offset.toLong()), zone),
                    )
            }
            repeat(PERIOD_MONTH_COUNT) { offset ->
                periods +=
                    WereadSyncPeriod(
                        WereadStore.MODE_MONTHLY,
                        startSec(monthStart.minusMonths(offset.toLong()), zone),
                    )
            }
            repeat(PERIOD_YEAR_COUNT) { offset ->
                periods +=
                    WereadSyncPeriod(
                        WereadStore.MODE_ANNUALLY,
                        startSec(yearStart.minusYears(offset.toLong()), zone),
                    )
            }
            periods += WereadSyncPeriod(WereadStore.MODE_OVERALL, 0L)
            return periods
        }

        /** 把本地日期换算为零点秒数。 */
        private fun startSec(
            date: LocalDate,
            zone: ZoneId,
        ): Long = date.atStartOfDay(zone).toEpochSecond()
    }
}

/**
 * 一个待拉取的周期。
 *
 * @param mode 周期类型，取 weekly、monthly、annually、overall
 * @param baseTimeSec 周期起点秒数，零表示由网关按当前时刻计算
 */
data class WereadSyncPeriod(
    val mode: String,
    val baseTimeSec: Long,
)

/** 解析同步状态文本，供界面读取；结构不合法时返回空值。 */
fun parseSyncState(text: String?): JsonSyncState? {
    if (text.isNullOrBlank()) return null
    return runCatching {
        val json = Json.parseToJsonElement(text).let { element -> element as kotlinx.serialization.json.JsonObject }
        JsonSyncState(
            success = (json["success"]?.toString() ?: "false").toBoolean(),
            message = json["message"]?.toString()?.trim('"').orEmpty(),
            finishedAtMillis = (json["finishedAtMillis"]?.toString() ?: "0").toLong(),
        )
    }.getOrNull()
}

/**
 * 同步状态的可读结构。
 *
 * @param success 上次同步是否成功
 * @param message 上次同步说明
 * @param finishedAtMillis 上次完成时刻
 */
data class JsonSyncState(
    val success: Boolean,
    val message: String,
    val finishedAtMillis: Long,
)
