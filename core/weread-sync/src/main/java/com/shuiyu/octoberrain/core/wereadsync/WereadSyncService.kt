package com.shuiyu.octoberrain.core.wereadsync

import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.data.WereadDataGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
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
    /** 本轮分项请求失败说明，不含请求凭据。 */
    fun warnings(): List<String> = emptyList()

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
    val complete: Boolean = true,
    val remainingBooks: Int = 0,
    val updatedFiles: Int = notes.size,
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
 * @param noteCount 本轮更新的划线或想法文件数，缓存命中不计入
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
 * 先读取书架与统计，再补逐书资产并合并到书架，最后同步笔记。
 * 每一步失败保留已有数据，最终以报告形式汇总。
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
            if (!WereadDataGate.tryBeginSync()) {
                return@withLock WereadSyncReport(false, "同步或迁移正在进行，请稍后重试")
            }
            try {
                withContext(Dispatchers.IO) { runSync(bases) }
            } finally {
                WereadDataGate.endSync()
            }
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
        val assets = provider.assets()
        val shelfCount = writeShelf(enrichShelf(shelf, assets))
        val statCount = writeStats(stats)
        val billCount = writeBills(bases, stats)
        val assetCount = writeAssets(assets)
        val notes = provider.notes()
        val noteCount = writeNotes(notes)
        val warnings = provider.warnings().toMutableList()
        if (notes != null && !notes.complete) warnings += "笔记仍有 ${notes.remainingBooks} 本待补齐"
        val total = shelfCount + statCount + billCount + assetCount + noteCount
        val success = total > 0 && warnings.isEmpty()
        // 已取到数据却没有任何一条确认落盘，说明失败在写入侧而非网络侧。
        val fetchedSomething = shelf != null || stats.isNotEmpty()
        val warningText = warnings.joinToString("；")
        val message =
            when {
                warnings.isNotEmpty() ->
                    "部分同步：书架 $shelfCount 本、资产 $assetCount 本、笔记更新 $noteCount 份；$warningText"
                success ->
                    "同步完成：书架 $shelfCount 本、统计 $statCount 期、账单 $billCount 期、资产 $assetCount 本、笔记更新 $noteCount 份"
                fetchedSomething -> "同步写入失败，已取到数据但未能存储，请检查存储空间"
                else -> "同步未取得数据"
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

    /** 按预先取到的统计响应写入各周期，返回确认落盘的数量。 */
    private fun writeStats(stats: Map<String, String>): Int {
        var count = 0
        for ((key, raw) in stats) {
            val period = key.split(':')
            val mode = period.first()
            val base = period.last().toLongOrNull() ?: 0L
            if (store.writeStats(mode, base, raw, clock())) count += 1
        }
        return count
    }

    /** 周期在缓存中的键。 */
    private fun periodKey(period: WereadSyncPeriod): String = "${period.mode}:${period.baseTimeSec}"

    /** 逐本补查结束后再保存书架，确保本轮历史书进度与资产一致。 */
    private fun enrichShelf(
        shelf: List<WereadShelfBook>?,
        assets: List<WereadAssetBook>?,
    ): List<WereadShelfBook>? {
        val byId = assets.orEmpty().associateBy { it.bookId }
        return shelf?.map { book ->
            val asset = byId[book.bookId]
            if (asset == null) {
                book
            } else {
                book.copy(
                    readingSeconds = asset.readingSeconds,
                    progress = asset.progress,
                    finished = book.finished || asset.finished,
                    finishTimeMillis = maxOf(book.finishTimeMillis, asset.finishTimeMillis),
                )
            }
        }
    }

    /** 书架失败时保留缓存；自动本书按最近阅读选取，不按累计时长猜测。 */
    private fun writeShelf(shelf: List<WereadShelfBook>?): Int {
        if (shelf == null) return 0
        val stored = store.writeShelf(shelf, clock()) && store.writeShelfIds(shelf.map { it.bookId })
        return if (stored) {
            store.writeCurrentBook(shelf.filter { it.readUpdateTime > 0 }.maxByOrNull { it.readUpdateTime })
            shelf.size
        } else {
            0
        }
    }

    /** 写入各周期统计，返回确认落盘的数量。 */
    private suspend fun writeStats(bases: List<WereadSyncPeriod>): Int {
        var count = 0
        for (period in bases) {
            val raw = provider.stats(period.mode, period.baseTimeSec) ?: continue
            if (store.writeStats(period.mode, period.baseTimeSec, raw, clock())) count += 1
        }
        return count
    }

    /** 写入账单快照，返回确认落盘的数量。 */
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
            if (sheet != null && store.writeBill(period.mode, period.baseTimeSec, sheet)) {
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

    /** 资产只能来自逐书累计数据，周期 Top10 不能代替累计资产。 */
    private fun writeAssets(books: List<WereadAssetBook>?): Int {
        if (books == null) return 0
        return if (books.isNotEmpty() && store.writeAssets(books)) books.size else 0
    }

    /** 写入笔记索引与逐篇内容，返回确认落盘的数量。 */
    private fun writeNotes(batch: WereadNoteBatch?): Int {
        // 接口无数据时保留本地缓存；有数据但写盘失败时返回 0，表示本轮未确认落盘。
        val written = if (batch != null && store.writeNotes(batch.indexJson, batch.notes)) batch.updatedFiles else 0
        return written
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

        /** 统计取数的耗时预算，超过后跳过剩余周期；不包含逐书资产与笔记补全。 */
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
