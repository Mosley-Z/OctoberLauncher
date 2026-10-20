package com.shuiyu.octoberrain.feature.widgets.kernel

import com.inkread.weekread.kernel.AssetItem
import com.inkread.weekread.kernel.BillItem
import com.inkread.weekread.kernel.DataSource
import com.inkread.weekread.kernel.FingerprintGrid
import com.inkread.weekread.kernel.NoteItem
import com.inkread.weekread.kernel.PeriodSnapshot
import com.inkread.weekread.kernel.RankFilter
import com.inkread.weekread.kernel.ShelfBook
import com.inkread.weekread.kernel.ShelfSnapshot
import com.inkread.weekread.kernel.TodoEntry
import com.inkread.weekread.kernel.load.KernelDataSource
import com.shuiyu.octoberrain.core.common.ContentSource
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.WereadAppearance
import com.shuiyu.octoberrain.core.common.WereadStatsPeriod
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

    /** 本记池缓存，供上一条与换一条在池内移动序号。 */
    private val notePoolCache = SnapshotCache<List<NoteItem>>()

    /** 本记当前条目序号，从零开始。 */
    @Volatile
    private var noteIndex: Int = 0

    /** 本书形态手动选定的书目编号，为空表示自动档（最近在读）。 */
    @Volatile
    private var preferredBookId: String? = null

    /** 最近一次读取是否因超时或异常失败。 */
    @Volatile
    private var failure: Boolean = false

    /** 读取书架快照，原盘优先；已手动选定书目时把该书目排到首位。 */
    override fun shelf(): ShelfSnapshot? {
        val snapshot =
            disk()?.shelf()?.takeIf { it.books.isNotEmpty() }
                ?: shelfCache.read { load { source -> KernelDataMapper.shelf(source.wereadShelf(SHELF_LIMIT)) } }
        return snapshot?.let { reorder(it) }
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
        val preferred = preferredBookId
        if (preferred != null) {
            shelf()?.books?.firstOrNull { it.bookId == preferred }?.let { return it }
        }
        return runCatching { disk()?.loader()?.currentBook() }.getOrNull()
    }

    /**
     * 返回手动选定的书目编号。
     *
     * @return 书目编号，自动档为空
     */
    fun selectedBookId(): String? = preferredBookId

    /**
     * 手动选定本书形态的目标书目。
     *
     * @param bookId 书目编号，传空表示回到自动档
     */
    fun selectBook(bookId: String?) {
        preferredBookId = bookId
        shelfCache.clear()
    }

    /**
     * 切到上一条本记。
     *
     * @return 是否发生切换
     */
    fun previousNote(): Boolean {
        if (noteIndex <= 0) return false
        noteIndex -= 1
        return true
    }

    /**
     * 切到下一条本记。
     *
     * @return 是否发生切换
     */
    fun nextNote(): Boolean {
        val count = notePool().size
        if (noteIndex >= count - 1) return false
        noteIndex += 1
        return true
    }

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
        val pool = notePool()
        if (pool.isEmpty()) return emptyList()
        val start = noteIndex.coerceIn(0, pool.size - 1)
        return pool.drop(start).take(limit)
    }

    /**
     * 读取本记池，原盘优先。
     *
     * @return 条目列表，数据缺失时为空
     */
    private fun notePool(): List<NoteItem> {
        val fromDisk = runCatching { disk()?.notes(NOTE_POOL_LIMIT) }.getOrNull().orEmpty()
        if (fromDisk.isNotEmpty()) return fromDisk
        return notePoolCache
            .read { load { source -> source.wereadNotes(NOTE_POOL_LIMIT).notes.map(KernelDataMapper::note) } }
            .orEmpty()
    }

    /**
     * 按手动选定书目调整书架顺序。
     *
     * 内核本书形态取书架首本，把选定书目排到首位即可复用内核绘制路径。
     *
     * @param snapshot 原始书架快照
     * @return 调整后的快照，未选定书目或书不在书架内时原样返回
     */
    private fun reorder(snapshot: ShelfSnapshot): ShelfSnapshot {
        val preferred = preferredBookId
        val index = if (preferred == null) -1 else snapshot.books.indexOfFirst { it.bookId == preferred }
        return if (index <= 0) {
            snapshot
        } else {
            val books = snapshot.books.toMutableList()
            books.add(0, books.removeAt(index))
            ShelfSnapshot().also { reordered ->
                reordered.books = books
                reordered.ids = snapshot.ids
                reordered.fetchedAt = snapshot.fetchedAt
            }
        }
    }

    /**
     * 读取待办列表，原盘优先。
     *
     * 微读墨记把待办写在 kernel_prefs 的 todo_list 键，导入后可直接读取；原盘没有待办时
     * 才回退到内容服务，避免已配置密钥却显示「请先设置密钥」的空态文案。
     */
    override fun todos(): List<TodoEntry> {
        val fromDisk = runCatching { disk()?.todos() }.getOrNull().orEmpty()
        if (fromDisk.isNotEmpty()) return fromDisk
        return todoCache.read { load { source -> KernelDataMapper.todo(source.wereadTodos()) } }.orEmpty()
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
        shelfCache.clear()
        weekCache.clear()
        monthCache.clear()
        notesCache.clear()
        notePoolCache.clear()
        todoCache.clear()
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

        /** 本记池读取条数，覆盖上一条与换一条的移动范围。 */
        const val NOTE_POOL_LIMIT: Int = 200

        /** 单次读取的最长等待时间，单位毫秒。 */
        const val READ_TIMEOUT_MS: Long = 4000L

        /** 月模式标识。 */
        const val MODE_MONTHLY: String = "monthly"

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
