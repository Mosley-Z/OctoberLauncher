package com.shuiyu.octoberrain.feature.content

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.BookItem
import com.shuiyu.octoberrain.core.common.ContentSource
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.ReadingSnapshot
import com.shuiyu.octoberrain.core.common.WeatherSnapshot
import com.shuiyu.octoberrain.core.common.WereadBillSnapshot
import com.shuiyu.octoberrain.core.common.WereadBucketSnapshot
import com.shuiyu.octoberrain.core.common.WereadFingerprintSnapshot
import com.shuiyu.octoberrain.core.common.WereadHighlight
import com.shuiyu.octoberrain.core.common.WereadMonth
import com.shuiyu.octoberrain.core.common.WereadNoteSnapshot
import com.shuiyu.octoberrain.core.common.WereadNotesSnapshot
import com.shuiyu.octoberrain.core.common.WereadRankEntry
import com.shuiyu.octoberrain.core.common.WereadShelfItem
import com.shuiyu.octoberrain.core.common.WereadStatsPeriod
import com.shuiyu.octoberrain.core.common.WereadStatsSnapshot
import com.shuiyu.octoberrain.core.wereadsync.WereadStore
import com.shuiyu.octoberrain.core.wereadsync.WereadSyncService
import com.shuiyu.octoberrain.feature.content.data.BookshelfRepository
import com.shuiyu.octoberrain.feature.content.data.ContentPaths
import com.shuiyu.octoberrain.feature.content.data.FileLibrary
import com.shuiyu.octoberrain.feature.content.data.KernelWereadReader
import com.shuiyu.octoberrain.feature.content.data.WeatherClient
import com.shuiyu.octoberrain.feature.content.data.WereadClient
import com.shuiyu.octoberrain.feature.content.data.WereadStats
import com.shuiyu.octoberrain.feature.content.data.WereadSyncBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * 内容数据源实现。
 *
 * 聚合书架、微信读书、天气与文件管理数据，对宿主提供
 * [ContentSource] 契约，对模块内页面提供扩展读取能力。所有读取都带超时与降级
 * 回退：超时或异常时返回空结果，避免阻塞桌面首屏与页面切换。
 */
class ContentSourceImpl(
    context: Context,
) : ContentSource {
    private val version = MutableStateFlow(0L)
    override val dataVersion = version.asStateFlow()

    private companion object {
        /** 微读数据目录名，与内核 KernelDataLoader 的根目录一致。 */
        private const val WEREAD_DATA_DIR = "weread"

        private const val TAG = "内容数据源"
        private const val TIMEOUT_SCAN_MS = 8000L

        /** 书架请求：同步书架后还会补齐至多八本书的进度，总耗时按请求与补齐相加预留。 */
        private const val TIMEOUT_SHELF_MS = 38000L

        /** 月度统计：本月与上月两次请求。 */
        private const val TIMEOUT_MONTH_MS = 35000L

        /** 划线：笔记列表一次请求加至多三本书的划线请求。 */
        private const val TIMEOUT_HIGHLIGHTS_MS = 65000L

        /** 周期统计：周、年、累计各一次请求。 */
        private const val TIMEOUT_STATS_MS = 45000L

        /** 笔记分页：概览一次请求加至多五本书的明细请求。 */
        private const val TIMEOUT_NOTES_MS = 65000L

        /** 阅读快照：按微信读书来源读取，缺失时保留空态。 */
        private const val TIMEOUT_READING_SNAPSHOT_MS = 42000L
        private const val TIMEOUT_WEATHER_MS = 12000L

        /** 书架数据内存缓存有效期，三十分钟。 */
        private const val SHELF_CACHE_TTL_MS = 30L * 60L * 1000L

        /** 微信读书书架一次缓存的条目上限，覆盖书架页与负一屏的最大需求。 */
        private const val SHELF_CACHE_LIMIT = 30
    }

    /** 一次书架读取结果，本地与微信读书两侧数据均可能为空。 */
    data class ShelfData(
        val local: List<BookItem>,
        val weread: List<WereadShelfItem>,
    )

    /** 带时间戳的缓存值，用于判断是否仍在有效期内。 */
    private data class Timed<T>(
        val value: T,
        val at: Long,
        val directory: String? = null,
    ) {
        /** 是否在三十分钟有效期内。 */
        fun isFresh(now: Long): Boolean = now - at <= SHELF_CACHE_TTL_MS
    }

    private val appContext = context.applicationContext ?: context
    private val bookshelf = BookshelfRepository(appContext)

    /** 本地书架缓存按目录与有效期区分，不因切换展示来源或行数清空。 */
    @Volatile
    private var localBooksCache: Timed<List<BookItem>>? = null

    /** 微信读书书架缓存，同样只按时间戳判断是否过期。 */
    @Volatile
    private var wereadShelfCache: Timed<List<WereadShelfItem>>? = null

    /** 文件管理能力，供文件页调用。 */
    val files = FileLibrary(appContext)

    /** 微信读书客户端，页面据此判断是否已配置密钥。 */
    val wereadClient = WereadClient(appContext)

    /** 数据目录写入与同步编排，写入格式与内核读取点一一对应。 */
    private val wereadSync =
        WereadSyncService(
            store = WereadStore(File(appContext.filesDir, WEREAD_DATA_DIR)),
            provider = WereadSyncBridge(File(appContext.filesDir, WEREAD_DATA_DIR)),
        )

    /** 微读墨记原盘读取，卡片与负一屏优先走这里，与微读自身共用同一份数据。 */
    private val kernelWeread = KernelWereadReader(File(appContext.filesDir, "weread"))

    /** 天气客户端，页面据此判断是否启用。 */
    val weatherClient = WeatherClient()

    /**
     * 本地书籍是否可读。
     *
     * 书籍目录已配置即可读，目录内容为空时由扫描结果自然返回空列表。判断不再读取旧的
     * 书架来源开关，书架展示哪一侧改由负一屏书源决定。
     */
    fun localShelfAvailable(): Boolean = ContentPaths.integrations().booksDir.isNotBlank()

    /**
     * 微信读书数据是否可读。
     *
     * 只取决于微信读书集成总开关。旧的书架来源开关不参与判断，避免关闭书架来源后
     * 月统计、摘录与阅读指纹等数据一并失效。
     */
    fun wereadAvailable(): Boolean = ContentPaths.integrations().wereadEnabled

    /**
     * 读取完整书架，不受公共契约的条目数量限制；本地来源关闭时直接返回空。
     *
     * 命中三十分钟内存缓存时直接返回缓存；否则重新扫描并刷新缓存，扫描失败
     * 时退回上一次缓存。
     */
    suspend fun bookshelfAll(): List<BookItem> = localBooks(force = false)

    /** 读取本地书架，[force] 为 true 时绕过有效期直接重新扫描。 */
    private suspend fun localBooks(force: Boolean): List<BookItem> {
        if (!localShelfAvailable()) return emptyList()
        val now = System.currentTimeMillis()
        val directory = ContentPaths.integrations().booksDir
        val cached = localBooksCache?.takeIf { it.directory == directory }
        if (cached == null && localBooksCache != null) {
            localBooksCache = null
            bookshelf.invalidate()
        }
        if (!force && cached != null && cached.isFresh(now)) return cached.value
        var fetched: List<BookItem>? = null
        val value =
            guarded(
                fallback = cached?.value ?: emptyList(),
                label = "书架扫描",
                timeoutMs = TIMEOUT_SCAN_MS,
            ) {
                bookshelf.scan(deadlineMs = TIMEOUT_SCAN_MS - 1000L).also { fetched = it }
            }
        fetched?.let {
            if (ContentPaths.integrations().booksDir == directory) {
                localBooksCache = Timed(it, System.currentTimeMillis(), directory)
            }
        }
        return value
    }

    /** 仅取已落盘的索引，不触发联网，供书架展示和升级承接。 */
    internal fun storedWereadShelf(): List<WereadShelfItem> = kernelWeread.shelf(0)

    /** 本地用户书架刷新不吞掉目录/权限错误，不把旧列表当作扫描成功。 */
    internal suspend fun refreshLocalDisplayShelf(): List<BookItem> {
        check(ContentPaths.hasStorageAccess())
        val root = ContentPaths.booksRoot()
        check(root.isDirectory || (!root.exists() && root.mkdirs()))
        val items = bookshelf.scan(deadlineMs = TIMEOUT_SCAN_MS - 1000L)
        localBooksCache = Timed(items, System.currentTimeMillis(), ContentPaths.integrations().booksDir)
        return items
    }

    /** 清空全部数据缓存，供手动刷新与配置变更后调用。 */
    fun invalidate() {
        localBooksCache = null
        wereadShelfCache = null
        bookshelf.invalidate()
        wereadClient.invalidate()
        weatherClient.invalidate()
        version.update { it + 1L }
    }

    /**
     * 清空书架两侧的内存缓存。
     *
     * 供负一屏切换书源后立即按新书源重新读取，避免沿用另一侧三十分钟内的旧结果；
     * 不递减数据版本，配置写入本身已触发界面刷新。
     */
    fun invalidateShelfCaches() {
        localBooksCache = null
        wereadShelfCache = null
    }

    /**
     * 读取当前有效期内缓存的书架数据。
     *
     * 两侧来源按 [local] 与 [weread] 选取；任一所需来源缺少未过期缓存时返回
     * null，由调用方决定是否等待网络或磁盘读取。行数不参与缓存键，缓存保存
     * 完整列表，调用方按行数截取。
     */
    fun cachedShelfData(
        local: Boolean,
        weread: Boolean,
    ): ShelfData? {
        val now = System.currentTimeMillis()
        val localItems: List<BookItem> =
            if (local) {
                localBooksCache
                    ?.takeIf { it.directory == ContentPaths.integrations().booksDir && it.isFresh(now) }
                    ?.value ?: return null
            } else {
                emptyList()
            }
        val wereadItems: List<WereadShelfItem> =
            if (weread) {
                wereadShelfCache?.takeIf { it.isFresh(now) }?.value ?: return null
            } else {
                emptyList()
            }
        return ShelfData(local = localItems, weread = wereadItems)
    }

    /**
     * 强制重新读取两侧书架数据并刷新缓存。
     *
     * 供书架页与负一屏在展示缓存后做后台刷新，也供手动刷新使用；来源关闭时
     * 对应一侧直接返回空列表，不发起扫描或网络请求。
     */
    suspend fun refreshShelfData(
        local: Boolean,
        weread: Boolean,
    ): ShelfData =
        ShelfData(
            local = if (local) localBooks(force = true) else emptyList(),
            weread = if (weread) wereadShelfItems(force = true) else emptyList(),
        )

    override suspend fun readingSnapshot(): ReadingSnapshot? =
        guarded(
            fallback = null,
            label = "阅读快照",
            timeoutMs = TIMEOUT_READING_SNAPSHOT_MS,
        ) {
            wereadReadingSnapshot()
        }

    override suspend fun weather(): WeatherSnapshot? =
        guarded(
            fallback = null,
            label = "天气",
            timeoutMs = TIMEOUT_WEATHER_MS,
        ) {
            weatherClient.current()
        }

    override suspend fun bookshelf(limit: Int): List<BookItem> {
        val all = bookshelfAll()
        return if (limit <= 0 || limit >= all.size) all else all.take(limit)
    }

    override suspend fun wereadShelf(limit: Int): List<WereadShelfItem> {
        val all = wereadShelfItems(force = false)
        return if (limit <= 0 || limit >= all.size) all else all.take(limit)
    }

    /**
     * 读取微信读书书架，[force] 为 true 时绕过三十分钟有效期。
     *
     * 缓存保存 [SHELF_CACHE_LIMIT] 条，进度同步仍由客户端限制在最近八本；
     * 请求失败时退回上一次缓存。
     */
    private suspend fun wereadShelfItems(force: Boolean): List<WereadShelfItem> {
        if (!wereadAvailable()) return emptyList()
        val now = System.currentTimeMillis()
        val cached = wereadShelfCache
        if (!force && cached != null && cached.isFresh(now)) return cached.value
        val local = kernelWeread.shelf(SHELF_CACHE_LIMIT)
        if (local.isNotEmpty()) {
            wereadShelfCache = Timed(local, now)
            return local
        }
        var fetched: List<WereadShelfItem>? = null
        val value =
            guarded(
                fallback = cached?.value ?: emptyList(),
                label = "微信读书书架",
                timeoutMs = TIMEOUT_SHELF_MS,
            ) {
                wereadClient.shelf(SHELF_CACHE_LIMIT).also { fetched = it }
            }
        fetched?.let { wereadShelfCache = Timed(it, System.currentTimeMillis()) }
        return value
    }

    override suspend fun wereadMonth(): WereadMonth? {
        if (!wereadAvailable()) return null
        return guarded(
            fallback = null,
            label = "微信读书月度统计",
            timeoutMs = TIMEOUT_MONTH_MS,
        ) {
            wereadClient.month()
        }
    }

    override suspend fun wereadHighlights(limit: Int): List<WereadHighlight> {
        if (!wereadAvailable()) return emptyList()
        return guarded(
            fallback = emptyList(),
            label = "微信读书划线",
            timeoutMs = TIMEOUT_HIGHLIGHTS_MS,
        ) {
            wereadClient.highlights(limit)
        }
    }

    override suspend fun wereadPeriod(period: WereadStatsPeriod): WereadStatsSnapshot? {
        if (!wereadAvailable()) return null
        return guarded(
            fallback = null,
            label = "微信读书周期统计",
            timeoutMs = TIMEOUT_STATS_MS,
        ) {
            when (period) {
                WereadStatsPeriod.WEEKLY -> wereadClient.weekly()?.toSnapshot(WereadStatsPeriod.WEEKLY)
                WereadStatsPeriod.ANNUALLY -> wereadClient.annually()?.toSnapshot(WereadStatsPeriod.ANNUALLY)
                WereadStatsPeriod.OVERALL -> wereadClient.overall()?.toSnapshot(WereadStatsPeriod.OVERALL)
                WereadStatsPeriod.MONTHLY -> wereadClient.month()?.toStatsSnapshot()
            }
        }
    }

    override suspend fun wereadNotes(limit: Int): WereadNotesSnapshot {
        if (!wereadAvailable() || !wereadClient.isConfigured()) return WereadNotesSnapshot()
        return guarded(
            fallback = WereadNotesSnapshot(),
            label = "微信读书笔记",
            timeoutMs = TIMEOUT_NOTES_MS,
        ) {
            val page = wereadClient.notes(limit)
            WereadNotesSnapshot(
                notes =
                    page.notes.map {
                        WereadNoteSnapshot(
                            bookTitle = it.bookTitle,
                            chapterTitle = it.chapterTitle,
                            text = it.text,
                            thought = it.thought,
                            createdAt = it.createdAt,
                        )
                    },
                total = page.total,
            )
        }
    }

    override suspend fun wereadBill(): WereadBillSnapshot? {
        if (!wereadAvailable()) return null
        return kernelWeread.bill() ?: networkBill()
    }

    /** 从网络读取月度账单，微读原盘缺失时使用。 */
    private suspend fun networkBill(): WereadBillSnapshot? =
        guarded(
            fallback = null,
            label = "微信读书月度账单",
            timeoutMs = TIMEOUT_MONTH_MS,
        ) {
            wereadClient.month()?.let {
                WereadBillSnapshot(
                    monthMinutes = it.monthMinutes,
                    lastMonthMinutes = it.lastMonthMinutes,
                    days = it.days,
                )
            }
        }

    override suspend fun wereadRank(limit: Int): List<WereadRankEntry> {
        if (!wereadAvailable()) return emptyList()
        val local = kernelWeread.rank(limit)
        return if (local.isNotEmpty()) local else networkRank(limit)
    }

    /**
     * 从网络读取阅读排行，微读原盘缺失时使用。
     *
     * @param limit 条目上限，非正数按一条处理
     */
    private suspend fun networkRank(limit: Int): List<WereadRankEntry> =
        guarded(
            fallback = emptyList(),
            label = "微信读书阅读排行",
            timeoutMs = TIMEOUT_SHELF_MS,
        ) {
            val count = limit.coerceAtLeast(1)
            wereadShelf(SHELF_CACHE_LIMIT)
                .sortedByDescending { it.progress }
                .take(count)
                .map { WereadRankEntry(title = it.title, author = it.author, progress = it.progress) }
        }

    override suspend fun wereadFingerprint(): WereadFingerprintSnapshot? {
        if (!wereadAvailable()) return null
        return kernelWeread.fingerprint() ?: networkFingerprint()
    }

    /** 从网络读取阅读指纹，微读原盘缺失时使用。 */
    private suspend fun networkFingerprint(): WereadFingerprintSnapshot? {
        if (!wereadClient.isConfigured()) return null
        return guarded(
            fallback = null,
            label = "微信读书阅读指纹",
            timeoutMs = TIMEOUT_STATS_MS,
        ) {
            val overall = wereadClient.overall()
            val month = wereadClient.month()
            if (overall == null && month == null) return@guarded null
            val monthMinutes = month?.monthMinutes ?: 0
            val totalMinutes = overall?.let { (it.totalSeconds / 60L).toInt() } ?: monthMinutes
            val readDays = overall?.readDays ?: month?.days?.count { it > 0 } ?: 0
            val average =
                overall?.let { (it.dayAverageSeconds / 60L).toInt() }
                    ?: if (readDays > 0) monthMinutes / readDays else 0
            WereadFingerprintSnapshot(
                totalMinutes = totalMinutes,
                readDays = readDays,
                dayAverageMinutes = average,
                days = month?.days ?: emptyList(),
            )
        }
    }

    /** 把数据层的周期统计转换为组件侧快照，时长由秒换算为分钟。 */
    private fun WereadStats.toSnapshot(period: WereadStatsPeriod): WereadStatsSnapshot =
        WereadStatsSnapshot(
            period = period,
            totalMinutes = (totalSeconds / 60L).toInt(),
            readDays = readDays,
            dayAverageMinutes = (dayAverageSeconds / 60L).toInt(),
            buckets =
                buckets.map {
                    WereadBucketSnapshot(startMillis = it.startMillis, minutes = (it.seconds / 60L).toInt())
                },
        )

    /** 把月度统计转换为周期快照，逐日分钟数依次作为分桶。 */
    private fun WereadMonth.toStatsSnapshot(): WereadStatsSnapshot {
        val activeDays = days.count { it > 0 }
        return WereadStatsSnapshot(
            period = WereadStatsPeriod.MONTHLY,
            totalMinutes = monthMinutes,
            readDays = activeDays,
            dayAverageMinutes = if (activeDays > 0) monthMinutes / activeDays else 0,
            buckets = days.map { WereadBucketSnapshot(minutes = it) },
        )
    }

    override suspend fun searchBooks(query: String): List<BookItem> {
        if (!localShelfAvailable()) return emptyList()
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        return guarded(
            fallback = emptyList(),
            label = "书架检索",
            timeoutMs = TIMEOUT_SCAN_MS,
        ) {
            bookshelf.search(keyword, limit = 30, deadlineMs = TIMEOUT_SCAN_MS - 1000L)
        }
    }

    override suspend fun refreshAll() {
        invalidate()
        runCatching { AppServices.refresh.requestPartial() }
            .onFailure { InkLog.w(TAG, "请求刷新失败：${it.message}") }
        syncWereadDataDirectory()
    }

    /**
     * 把微读网关数据写入 `filesDir/weread`，供内核与负一屏直接读取。
     *
     * 单独成函数以便在刷新链路中单独捕获异常：数据目录同步失败不应阻断界面刷新。
     */
    private suspend fun syncWereadDataDirectory() {
        if (!wereadClient.isConfigured()) return
        if (ContentPaths.integrations().effectiveWereadSource != "synced") return
        runCatching { wereadSync.sync() }
            .onFailure { error -> InkLog.w(TAG, "微读数据目录同步失败：${error.message}") }
    }

    /** 微信读书侧的阅读快照，来源关闭时返回 null。 */
    private suspend fun wereadReadingSnapshot(): ReadingSnapshot? {
        if (!wereadAvailable()) return null
        if (!wereadClient.isConfigured()) return null
        val shelf = wereadShelf(1).firstOrNull() ?: return null
        val month = runCatching { wereadClient.month() }.getOrNull()
        return ReadingSnapshot(
            source = "weread",
            title = shelf.title,
            author = shelf.author,
            coverPath = shelf.coverPath,
            progress = shelf.progress,
            todayMinutes = 0,
            monthMinutes = month?.monthMinutes ?: 0,
            lastReadAt = System.currentTimeMillis(),
        )
    }

    /**
     * 执行带超时的读取。
     *
     * 超时或业务异常时记录日志并返回 [fallback]；调用方协程被取消时继续抛出
     * 取消异常，保证页面切换后不再占用线程。
     */
    private suspend fun <T> guarded(
        fallback: T,
        label: String,
        timeoutMs: Long,
        block: suspend () -> T,
    ): T =
        try {
            withTimeout(timeoutMs) { block() }
        } catch (error: TimeoutCancellationException) {
            InkLog.w(TAG, "$label 超时（${timeoutMs}ms），返回降级结果")
            fallback
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            InkLog.w(TAG, "$label 失败：${error.message}，返回降级结果", error)
            fallback
        }
}
