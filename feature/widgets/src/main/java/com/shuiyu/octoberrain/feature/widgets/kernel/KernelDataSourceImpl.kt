package com.shuiyu.octoberrain.feature.widgets.kernel

import android.os.Handler
import android.os.Looper
import com.inkread.weekread.kernel.AssetItem
import com.inkread.weekread.kernel.BillItem
import com.inkread.weekread.kernel.DataSource
import com.inkread.weekread.kernel.FingerprintGrid
import com.inkread.weekread.kernel.KernelStore
import com.inkread.weekread.kernel.NoteItem
import com.inkread.weekread.kernel.PeriodSnapshot
import com.inkread.weekread.kernel.RankFilter
import com.inkread.weekread.kernel.ShelfBook
import com.inkread.weekread.kernel.ShelfSnapshot
import com.inkread.weekread.kernel.TodoEntry
import com.inkread.weekread.kernel.load.KernelDataLoader
import com.inkread.weekread.kernel.load.KernelDataSource
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.ContentSource
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.WereadAppearance
import com.shuiyu.octoberrain.core.common.WereadRefreshRequest
import com.shuiyu.octoberrain.core.common.WereadStatsPeriod
import com.shuiyu.octoberrain.core.common.parseRecentReadTimes
import com.shuiyu.octoberrain.core.common.sortByRecentRead
import com.shuiyu.octoberrain.core.data.WereadBookSelection
import com.shuiyu.octoberrain.core.data.WereadNoteLibrary
import com.shuiyu.octoberrain.core.data.WereadNoteSelection
import com.shuiyu.octoberrain.feature.widgets.WereadBookCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * 内核数据源实现。
 *
 * 优先读微读墨记写入 filesDir/weread 的原盘数据，与微读自身共用一份来源；
 * 原盘缺失时退回内容服务的网络读取。内核在自身线程同步调用，网络读取以阻塞方式
 * 执行并设超时；读取失败或超时均返回空结构，不向内核抛出异常。单次读取结果缓存
 * 半分钟，避免绑定与绘制阶段重复请求；读取失败记录标记，供组件向内核写入错误文案。
 *
 * 数据源接口本身固定七个读取方法，适配层另有缓存失效与失败标记两个入口，
 * 函数数量由接口与状态维护共同决定，因此对函数数量度量局部豁免。
 *
 * @param content 内容服务提供者，返回空表示当前未装配内容模块
 * @param kernelRoot 微读原盘根目录提供者，返回空表示尚未导入数据
 */
@Suppress("TooManyFunctions")
internal class KernelDataSourceImpl(
    private val content: () -> ContentSource?,
    private val kernelRoot: () -> File?,
) : DataSource {
    /** 原盘数据源，根目录变化时重建。 */
    @Volatile
    private var disk: KernelDataSource? = null

    /** 原盘数据源对应的根目录，用于判断是否需要重建。 */
    @Volatile
    private var diskRoot: File? = null

    /** 书架缓存。 */
    private val shelfCache = SnapshotCache<ShelfSnapshot>()

    /** 周统计缓存。 */
    private val weekCache = SnapshotCache<PeriodSnapshot>()

    /** 月统计缓存。 */
    private val monthCache = SnapshotCache<PeriodSnapshot>()

    /** 笔记缓存。 */
    private val notesCache = SnapshotCache<List<NoteItem>>()

    /** 待办缓存。 */
    private val todoCache = SnapshotCache<List<TodoEntry>>()

    /** 最近阅读时间缓存，文件未变化时复用，避免每次读取书架都解析资产文件。 */
    @Volatile
    private var readTimesCache: ReadTimesCache? = null

    /** App 与桌面共享的持久化本记状态。 */
    private var selection: WereadNoteSelection? = null
    private var selectionRoot: File? = null

    private fun noteSelection(): WereadNoteSelection? {
        val root = kernelRoot() ?: return null
        if (selectionRoot != root) {
            selection = WereadNoteSelection(root)
            selectionRoot = root
        }
        return selection
    }

    /** 最近一次读取是否因超时或异常失败。 */
    @Volatile
    private var failure: Boolean = false

    /** 读取书架快照，原盘优先；已手动选定书目时把该书目排到首位。 */
    override fun shelf(): ShelfSnapshot? = orderedShelf()

    /**
     * 读取书架的原始快照，原盘优先。
     *
     * 该入口保持数据源给出的顺序，排序在 [orderedShelf] 中统一完成，
     * 书架与轮播因此共用同一顺序。
     *
     * @return 书架快照，无数据时为空
     */
    private fun rawShelf(): ShelfSnapshot? =
        preferredShelf(
            { loaderShelf() },
            { disk()?.shelf() },
            { networkShelf() },
        )

    /**
     * 读取内核加载器给出的书架。
     *
     * 该入口与负一屏书架使用同一份数据文件，原盘存在时优先采用；
     * 快照包装补齐标识集合与抓取时刻，下游按 [ShelfSnapshot] 统一消费。
     *
     * @return 书架快照，数据目录缺失或无书目时为空
     */
    private fun loaderShelf(): ShelfSnapshot? {
        val root = kernelRoot()?.takeIf { it.isDirectory } ?: return null
        val books = runCatching { KernelDataLoader(root).shelf() }.getOrNull()?.books.orEmpty()
        return shelfOf(books)
    }

    /**
     * 读取内容服务的网络书架，结果按缓存时限复用。
     *
     * @return 网络书架快照，读取失败时为空
     */
    private fun networkShelf(): ShelfSnapshot? =
        shelfCache.read { load { source -> KernelDataMapper.shelf(source.wereadShelf(SHELF_LIMIT)) } }

    /**
     * 把书目列表包装成内核书架快照。
     *
     * @param books 书目列表
     * @return 快照，列表为空时为空
     */
    private fun shelfOf(books: List<ShelfBook>): ShelfSnapshot? =
        books.takeIf { it.isNotEmpty() }?.let { items ->
            ShelfSnapshot().also { snapshot ->
                snapshot.books = items.toList()
                snapshot.ids = items.map { book -> book.bookId }.toSet()
                snapshot.fetchedAt = System.currentTimeMillis()
            }
        }

    /**
     * 读取按最近阅读时间排序的书架快照。
     *
     * 排序键来自微读资产文件，时间新的排在前面；缺少时间的书目保持原相对顺序排在末尾。
     * 书目顺序与上次选中的书无关，选中记忆由 book_pick 单独承担。
     *
     * @return 排序后的书架快照，无数据时为空
     */
    private fun orderedShelf(): ShelfSnapshot? = rawShelf()?.let { recentFirstShelf(it, readTimes()) }

    /**
     * 读取书目最近阅读时间。
     *
     * 资产文件未变化时直接复用上次解析结果；文件缺失或解析失败返回空映射，
     * 此时书架保持原有顺序。
     *
     * @return 书目编号到 Unix 秒时间的映射
     */
    private fun readTimes(): Map<String, Long> {
        val file = kernelRoot()?.resolve(ASSETS_FILE)?.takeIf { it.isFile } ?: return emptyMap()
        val modified = file.lastModified()
        val size = file.length()
        val cached = readTimesCache
        return when {
            cached != null && cached.modified == modified && cached.size == size -> cached.times
            else -> {
                val times = runCatching { parseRecentReadTimes(file.readText()) }.getOrDefault(emptyMap())
                readTimesCache = ReadTimesCache(modified, size, times)
                times
            }
        }
    }

    /**
     * 读取最近阅读前十本轮播书目，完整书架不受截取影响。
     *
     * 进度取内核百分数；累计阅读秒数在内容服务路径下缺失，此时按逐书资产补齐，
     * 资产同样缺失的条目保留零值，由组件隐藏时长。
     *
     * @return 轮播书目，无数据时为空
     */
    fun bookCards(): List<WereadBookCard> {
        val books = rawShelf()?.let { recentCarouselBooks(it, readTimes()) }.orEmpty()
        if (books.isEmpty()) return emptyList()
        val assets = if (books.any { it.readingSec <= 0 }) assetSeconds() else emptyMap()
        return books.map { book ->
            WereadBookCard(
                bookId = book.bookId,
                title = book.title,
                author = book.author,
                coverPath = book.coverUrl,
                progressPercent = book.progress.coerceIn(0, PERCENT_MAX),
                readingSeconds = book.readingSec.takeIf { it > 0 } ?: assets[book.bookId] ?: 0,
            )
        }
    }

    /**
     * 读取逐书累计阅读秒数，原盘缺失时返回空映射。
     *
     * @return 书目编号到阅读秒数的映射
     */
    private fun assetSeconds(): Map<String, Int> =
        runCatching { assets(ASSET_SORT_DEFAULT, false) }
            .getOrNull()
            .orEmpty()
            .associate { it.bookId to it.readTimeSec }

    /**
     * 读取指定书目的最近一条有效划线。
     *
     * 原盘笔记库按书目存放划线，读取后按创建时间取最新一条；没有划线或读取失败
     * 时返回空串，组件据此完全隐藏右上角笔记区域。
     *
     * @param bookId 书目编号
     * @return 划线正文，可为空串
     */
    fun latestMark(bookId: String): String? =
        kernelRoot()?.takeIf { bookId.isNotBlank() }?.let { root ->
            runCatching { WereadNoteLibrary(root).items(bookId) }
                .getOrNull()
                .orEmpty()
                .filter { it.markText.isNotBlank() }
                .maxByOrNull { it.createTime }
                ?.markText
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

    /**
     * 读取当前在读书目，手动选定书目优先于原盘记录。
     *
     * 微读把当前书单独写在 book.json，携带阅读进度与累计阅读秒数；书架快照
     * 不带这两项，因此本书形态以本方法结果为准。
     *
     * @return 当前在读书目，原盘缺失且未手动选定时为空
     */
    fun currentBook(): ShelfBook? {
        val cached = runCatching { disk()?.loader()?.currentBook() }.getOrNull()
        val selected = selectedBookId()
        val id = selected ?: kernelRoot()?.let(WereadBookSelection::automaticBookId).orEmpty()
        return cached?.takeIf { it.bookId == id }
            ?: shelf()?.books?.firstOrNull { it.bookId == id }
            ?: cached?.takeIf { selected == null && id.isEmpty() }
    }

    /**
     * 返回手动选定的书目编号。
     *
     * @return 书目编号，自动档为空
     */
    fun selectedBookId(): String? =
        kernelRoot()?.let { root ->
            KernelStore(root).getString("book_pick", "").trim().takeIf { it.isNotEmpty() }
        }

    /**
     * 手动选定本书形态的目标书目。
     *
     * @param bookId 书目编号，传空表示回到自动档
     */
    fun selectBook(bookId: String?) {
        kernelRoot()?.let { root ->
            KernelStore(root).apply {
                putString("book_pick", bookId.orEmpty().trim())
                flush()
            }
        }
        shelfCache.clear()
        AppServices.wereadSync?.refresh(WereadRefreshRequest("book", bookId = bookId.orEmpty())) { _, _ ->
            invalidate()
            Handler(Looper.getMainLooper()).post { AppServices.refresh.requestPartial() }
        }
    }

    /**
     * 切到上一条本记。
     *
     * @return 是否发生切换
     */
    fun previousNote(): Boolean = noteSelection()?.previous(desktop = true) != null

    /** 前进一条，写入与 App 共用的当天条目及历史。 */
    fun nextNote(): Boolean = noteSelection()?.current(desktop = true, advance = true) != null

    /** 绘制进度直接读共享选择状态，不再依赖 App 曾经绘制过的旧计数。 */
    fun noteProgress(): IntArray? = noteSelection()?.progress(desktop = true)

    fun noteSlot(): Int = noteSelection()?.slot(desktop = true) ?: 0

    /** 按模式读取周期统计，原盘优先；模式取值 weekly、monthly、annually、overall。 */
    override fun period(mode: String): PeriodSnapshot? {
        disk()?.period(mode)?.takeIf { it.rawJson != null || it.totalSec > 0 }?.let { return filteredRank(it) }
        val cache = if (mode == MODE_MONTHLY) monthCache else weekCache
        val period = if (mode == MODE_MONTHLY) WereadStatsPeriod.MONTHLY else WereadStatsPeriod.WEEKLY
        val mapped = cache.read { load { source -> KernelDataMapper.period(source.wereadPeriod(period), mode) } }
        return mapped?.let(::filteredRank)
    }

    /**
     * 按外观偏好过滤月历排名条目。
     *
     * 外观偏好开启「榜单只取书架书目」时，用内核提供的过滤工具保留书架书目编号集合内的条目；
     * 关闭、无条目或书架为空时原样返回。
     *
     * @param period 周期统计快照
     * @return 过滤后的快照
     */
    private fun filteredRank(period: PeriodSnapshot): PeriodSnapshot {
        val items = period.longest
        val shelfIds =
            if (items.isNullOrEmpty() || !appearance().rankShelfOnly) {
                emptySet()
            } else {
                runCatching { shelf()?.ids }.getOrNull().orEmpty()
            }
        items
            ?.takeIf { shelfIds.isNotEmpty() }
            ?.let { period.longest = RankFilter.apply(it, true, shelfIds) }
        return period
    }

    /**
     * 读取组件外观偏好。
     *
     * 每次读取当前原盘根目录下的偏好文件，使内置页面改动设置后下一次刷新即生效；
     * 尚未导入数据时取默认外观。
     *
     * @return 外观偏好快照
     */
    private fun appearance(): WereadAppearance = kernelRoot()?.let(WereadAppearanceCache::read) ?: WereadAppearance()

    /**
     * 读取逐笔账单，原盘优先。
     *
     * @param mode 周期类型，weekly 或 monthly
     * @param offset 从最新一期向更早回退的期数
     */
    override fun bill(
        mode: String,
        offset: Int,
    ): List<BillItem> = disk()?.bill(mode, offset).orEmpty()

    /**
     * 读取逐书阅读资产，原盘优先。
     *
     * @param sort 排序方式
     * @param folded 是否折叠仅显示书架内书目
     */
    override fun assets(
        sort: Int,
        folded: Boolean,
    ): List<AssetItem> = disk()?.assets(sort, folded).orEmpty()

    /**
     * 读取阅读指纹网格，原盘优先。
     *
     * @param bookCount 参与绘制的书目数量
     */
    override fun fingerprint(bookCount: Int): FingerprintGrid? = disk()?.fingerprint(bookCount)

    /**
     * 读取本记窗口，从当前条目序号开始取指定条数。
     *
     * 内核只渲染单条本记，窗口长度决定「上一条」「换一条」能否继续移动。
     *
     * @param limit 读取条数
     */
    override fun notes(limit: Int): List<NoteItem> {
        val selection = noteSelection()
        if (selection != null && selection.books().isNotEmpty()) {
            return listOfNotNull(selection.current(desktop = true)).take(limit.coerceAtLeast(0))
        }
        return notesCache
            .read {
                load { source -> source.wereadNotes(limit).notes.map(KernelDataMapper::note) }
            }.orEmpty()
    }

    /**
     * 读取待办列表，原盘优先。
     *
     * 微读墨记把待办写在 kernel_prefs 的 todo_list 键，导入后可直接读取；原盘没有待办时
     * 才回退到内容服务，避免已配置密钥却显示「请先设置密钥」的空态文案。
     */
    override fun todos(): List<TodoEntry> {
        val fromDisk = runCatching { disk()?.todos() }.getOrNull().orEmpty()
        if (kernelRoot()?.resolve("kernel_prefs.json")?.isFile == true) return fromDisk.filterNot { it.done }
        return todoCache
            .read {
                load { source ->
                    KernelDataMapper.todo(source.wereadTodos())
                }
            }.orEmpty()
            .filterNot { it.done }
    }

    /**
     * 取原盘数据源，根目录变化时重建。
     *
     * 导入完成或数据目录被清除后根目录可能变化，按当前根目录判断是否复用。
     */
    private fun disk(): KernelDataSource? {
        val root = kernelRoot() ?: return null
        if (disk == null || diskRoot != root) {
            disk = KernelDataSource(root)
            diskRoot = root
        }
        return disk
    }

    /**
     * 返回最近一次读取是否失败。
     *
     * 读取超时或内容服务抛出异常时置为真，成功读取时保持原值由 [invalidate] 清除。
     */
    fun hasFailure(): Boolean = failure

    /** 清空全部缓存与失败标记，供下一次预取重新读取。 */
    fun invalidate() {
        failure = false
        disk = null
        shelfCache.clear()
        weekCache.clear()
        monthCache.clear()
        notesCache.clear()
        todoCache.clear()
        readTimesCache = null
    }

    /**
     * 阻塞读取内容服务数据。
     *
     * 超时与异常均记录失败标记并返回空，协程取消向调用方传播。
     *
     * @param reader 针对内容服务的挂起读取函数
     * @return 读取结果，内容服务缺失、超时或异常时为 null
     */
    @Suppress("TooGenericExceptionCaught") // 内容服务各读取路径抛出的异常类型未统一，统一记录后按空数据返回。
    private fun <T> load(reader: suspend (ContentSource) -> T): T? {
        val source = content() ?: return null
        return try {
            runBlocking { withTimeout(READ_TIMEOUT_MS) { reader(source) } }
        } catch (timeout: TimeoutCancellationException) {
            failure = true
            InkLog.w(TAG, "内核数据读取超时", timeout)
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = true
            InkLog.w(TAG, "内核数据读取失败", error)
            null
        }
    }

    /** 数据源适配常量。 */
    private companion object {
        /** 书架读取条数，与内置绘制使用的条数一致。 */
        const val SHELF_LIMIT: Int = 20

        /** 单次读取的最长等待时间，单位毫秒。 */
        const val READ_TIMEOUT_MS: Long = 4000L

        /** 月模式标识。 */
        const val MODE_MONTHLY: String = "monthly"

        /** 逐书资产读取的默认排序标识。 */
        const val ASSET_SORT_DEFAULT: Int = 0

        /** 阅读进度的百分数上限。 */
        const val PERCENT_MAX: Int = 100

        /** 日志标签。 */
        const val TAG: String = "KernelDataSource"
    }
}

/**
 * 快照缓存：在有效期内复用最近一次读取结果。
 *
 * 空结果不写入缓存，避免失败状态延续到下一轮绘制；刷新时由数据源统一清空。
 *
 * @param T 缓存值类型
 */
internal class SnapshotCache<T : Any> {
    /** 最近一次读取结果。 */
    private var cached: T? = null

    /** 最近一次读取时刻，单位毫秒。 */
    private var cachedAt: Long = 0L

    /**
     * 读取缓存，过期时调用加载函数重新取值。
     *
     * @param loader 缓存未命中时执行的加载函数
     * @return 缓存值或加载结果，加载失败时为 null
     */
    fun read(loader: () -> T?): T? {
        val now = System.currentTimeMillis()
        val current = cached
        return if (current != null && now - cachedAt <= TTL_MS) {
            current
        } else {
            loader()?.also {
                cached = it
                cachedAt = now
            }
        }
    }

    /** 清空缓存，强制下一次读取访问数据源。 */
    fun clear() {
        cached = null
        cachedAt = 0L
    }

    /** 缓存有效期，单位毫秒。 */
    private companion object {
        /** 有效期取半分钟，覆盖一次绘制周期内的重复读取。 */
        const val TTL_MS: Long = 30_000L
    }
}

/** 书架资产文件名。 */
private const val ASSETS_FILE: String = "assets.json"

/**
 * 最近阅读时间缓存。
 *
 * @property modified 文件最后修改时间，单位毫秒
 * @property size 文件字节数
 * @property times 书目编号到最近阅读时间的映射
 */
internal data class ReadTimesCache(
    val modified: Long,
    val size: Long,
    val times: Map<String, Long>,
)

/**
 * 按最近阅读时间重排书架快照。
 *
 * 顺序只由最近阅读时间决定，与 book_pick 记忆的选中书无关；列表为空时原样返回。
 *
 * @param snapshot 原始书架快照
 * @param readTimes 书目编号到最近阅读时间的映射
 * @return 排序后的书架快照
 */
internal fun recentFirstShelf(
    snapshot: ShelfSnapshot,
    readTimes: Map<String, Long>,
): ShelfSnapshot {
    val sorted = sortByRecentRead(snapshot.books, ShelfBook::bookId, readTimes)
    return if (sorted === snapshot.books) {
        snapshot
    } else {
        ShelfSnapshot().also { ordered ->
            ordered.books = sorted
            ordered.ids = snapshot.ids
            ordered.fetchedAt = snapshot.fetchedAt
        }
    }
}

/**
 * 按优先级逐级读取书架快照，命中即停止。
 *
 * 调用顺序即优先级：内核加载器、原盘数据、网络缓存。空列表视为无数据，
 * 继续调用下一来源；后面的来源在前一来源给出有效书架时不会被求值。
 *
 * @param providers 按优先级排列的读取函数
 * @return 第一个含书目的快照，全部为空时为空
 */
internal fun preferredShelf(vararg providers: () -> ShelfSnapshot?): ShelfSnapshot? {
    for (provider in providers) {
        val snapshot = provider()
        if (snapshot != null && snapshot.books.isNotEmpty()) return snapshot
    }
    return null
}

/** 仅轮播使用最近阅读前十本，书架原快照与选中记忆均不改写。 */
internal fun recentCarouselBooks(
    snapshot: ShelfSnapshot,
    readTimes: Map<String, Long>,
): List<ShelfBook> = sortByRecentRead(snapshot.books, ShelfBook::bookId, readTimes).take(10)
