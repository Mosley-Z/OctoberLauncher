package com.shuiyu.octoberrain.feature.wereadapp

import android.content.Context
import android.graphics.Bitmap
import com.inkread.weekread.kernel.BookProgress
import com.inkread.weekread.kernel.KernelStore
import com.inkread.weekread.kernel.NoteContent
import com.inkread.weekread.kernel.NoteItem
import com.inkread.weekread.kernel.PeriodCalendar
import com.inkread.weekread.kernel.PeriodSnapshot
import com.inkread.weekread.kernel.ShelfBook
import com.inkread.weekread.kernel.ShelfSnapshot
import com.inkread.weekread.kernel.TodoEntry
import com.inkread.weekread.kernel.load.KernelDataLoader
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.WereadRefreshRequest
import com.shuiyu.octoberrain.core.data.WereadBookSelection
import com.shuiyu.octoberrain.core.data.WereadNoteSelection
import com.shuiyu.octoberrain.core.render.WereadCoverStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 微读墨记页面的本地数据入口。
 *
 * 页面与桌面卡片共用 `filesDir/weread` 目录：周期、书目、笔记与待办全部从该目录读取，
 * 周期切换时按偏好中的原始统计 JSON 重建任意一期的快照，不发起网络请求。
 *
 * @param context 用于定位应用私有目录的上下文
 */
@Suppress("TooManyFunctions", "ReturnCount")
internal class WereadAppData(
    context: Context,
) {
    /** 微读墨记数据根目录。 */
    val root: File = WereadAppPrefs.dataDir(context.applicationContext ?: context)

    /** 桌面与 App 共享的本记抽取状态。 */
    val noteSelection: WereadNoteSelection = WereadNoteSelection(root)

    /** 保存手选书号，空串回到自动档。 */
    fun selectBook(bookId: String) {
        KernelStore(root).apply {
            putString("book_pick", bookId.trim())
            flush()
        }
        reload()
    }

    /** 当前手选书号。 */
    fun selectedBookId(): String = KernelStore(root).getString("book_pick", "").trim()

    /** 内核数据装载器，读取目录内的书架、书目、笔记与统计文件。 */
    private var loader: KernelDataLoader = KernelDataLoader(root)

    /** 偏好存储，读取周期原始 JSON 与页面设置。 */
    private var store: KernelStore = KernelStore(root)

    /** `stats.json` 解析结果的缓存，键为周期类型。 */
    private val periodStartCache: MutableMap<String, List<Long>> = HashMap()

    /** 上次解析 `stats.json` 时的文件修改时间，用于判断缓存是否失效。 */
    private var periodStartStamp: Long = -1L

    /** `nt_index.json` 中书目标题的缓存，键为书目编号。 */
    private var noteMetaCache: Map<String, NoteBookMeta> = emptyMap()

    /** 上次解析 `nt_index.json` 时的文件修改时间，用于判断缓存是否失效。 */
    private var noteMetaStamp: Long = -1L

    /** 丢弃装载器与偏好的内存缓存，下一次读取重新落盘。 */
    fun reload() {
        loader = KernelDataLoader(root)
        store = KernelStore(root)
        periodStartCache.clear()
        periodStartStamp = -1L
        noteMetaCache = emptyMap()
        noteMetaStamp = -1L
    }

    /** 笔记所属书的书名、作者与封面，用于补齐全书级别的展示字段。 */
    private data class NoteBookMeta(
        val title: String,
        val author: String,
        val coverUrl: String,
    )

    /** 读取偏好存储，供设置页读写同一个实例。 */
    fun prefs(): KernelStore = store

    /** 读取书架快照，文件缺失或解析失败时返回空。 */
    fun shelf(): ShelfSnapshot? = runCatching { loader.shelf() }.getOrNull()

    /**
     * 读取最近在读的书目进度。
     *
     * 优先使用 `book.json` 记录的当前书；该文件缺失时退回书架第一本。
     * 返回值中的封面地址用于页面内加载封面位图。
     */
    fun currentBook(): BookProgress? {
        val shelf = shelf()
        val selected = selectedBookId().ifEmpty { WereadBookSelection.automaticBookId(root) }
        val current = runCatching { loader.currentBook() }.getOrNull()
        val book: ShelfBook =
            current?.takeIf { it.bookId == selected }
                ?: shelf?.books?.firstOrNull { it.bookId == selected }
                ?: current
                ?: shelf?.books?.firstOrNull()
                ?: return null
        return BookProgress().apply {
            bookId = book.bookId
            title = book.title
            author = book.author
            coverUrl = book.coverUrl
            progress = book.progress
            readingSec = book.readingSec
            fetchedAt = shelf?.fetchedAt ?: 0L
            finishTime = book.finishTime.toInt()
            finished = book.finished
            deepLink = if (book.bookId.isEmpty()) "" else "weread://reading?bId=${book.bookId}"
        }
    }

    /**
     * 读取指定周期的统计快照。
     *
     * 偏好键格式与 `WereadStore.writeStats` 一致：字符串键 `d_<mode>:<baseTimeSec>` 保存
     * 网关原始 JSON，长整键 `t_<mode>:<baseTimeSec>` 保存抓取时刻。当前期缺少原始 JSON 时
     * 退回 [KernelDataLoader.period] 从 `stats.json` 读取的缓存。
     *
     * @param mode 周期类型，取 weekly 或 monthly
     * @param baseTimeSec 周期起点秒数
     * @return 可绘制的快照，该期没有数据时返回 null
     */
    fun period(
        mode: String,
        baseTimeSec: Long,
    ): PeriodSnapshot? {
        val raw = store.getString("d_$mode:$baseTimeSec", "")
        if (raw.isNotEmpty()) {
            val fetchedAt = store.getLong("t_$mode:$baseTimeSec", 0L)
            val parsed =
                runCatching {
                    PeriodSnapshot.parseInto(PeriodSnapshot(), JSONObject(raw), fetchedAt, mode, baseTimeSec)
                }.getOrNull()
            if (parsed != null) return parsed
        }
        val fallback = runCatching { loader.period(mode) }.getOrNull() ?: return null
        return if (fallback.baseTime == baseTimeSec) fallback else null
    }

    /**
     * 读取某一周期类型的全部已缓存起点，升序排列。
     *
     * 周期缓存由同步流程以 `d_<mode>:<baseTimeSec>` 为键写入 `stats.json`（扁平对象，
     * 值为该期原始统计 JSON）；内核偏好对象本身不提供键枚举接口，因此这里直接解析该
     * 文件。解析结果按文件修改时间缓存，文件未变化时复用。
     *
     * @param mode 周期类型，取 weekly 或 monthly
     * @return 已缓存的周期起点秒数，升序；没有缓存时返回空列表
     */
    fun cachedPeriodStarts(mode: String): List<Long> {
        val file = statsFile()
        val stamp = if (file.isFile) file.lastModified() else 0L
        if (stamp != periodStartStamp) {
            periodStartCache.clear()
            periodStartStamp = stamp
        }
        periodStartCache[mode]?.let { return it }
        val prefix = "d_$mode:"
        val starts =
            runCatching {
                val text = if (file.isFile) file.readText() else ""
                if (text.isEmpty()) {
                    emptyList()
                } else {
                    JSONObject(text)
                        .keys()
                        .asSequence()
                        .filter { it.startsWith(prefix) }
                        .mapNotNull { it.removePrefix(prefix).toLongOrNull() }
                        .sorted()
                        .toList()
                }
            }.getOrDefault(emptyList())
        periodStartCache[mode] = starts
        return starts
    }

    /** 统计缓存文件，同步流程把每一次抓取的周期快照合并写入该文件。 */
    private fun statsFile(): File = File(root, STATS_FILE_NAME)

    /**
     * 计算周期选择器可回退的最早起点。
     *
     * 取同步窗口起点、缓存起点与当前期起点三者的较小值：窗口保证未同步的历史期
     * 也能被选中并按需拉取；缓存中存在更早的周期时同样允许回退到该期。
     *
     * @param mode 周期类型，取 weekly 或 monthly
     * @return 周期选择器左边界秒数
     */
    fun earliestPeriodStart(mode: String): Long {
        val current = currentPeriodStart(mode)
        val cached = cachedPeriodStarts(mode).firstOrNull()
        val window = windowEarliestPeriodStart(mode)
        return minOf(current, window, cached ?: current)
    }

    /**
     * 计算同步窗口允许回退的最早起点。
     *
     * 窗口与[WereadSyncService] 的默认同步范围一致：周十二期、月十二期、年三期，
     * 含当前期，因此回退期数为总数减一。
     *
     * @param mode 周期类型，取 weekly 或 monthly
     * @return 窗口起点秒数；非周月周期返回当前期起点
     */
    fun windowEarliestPeriodStart(mode: String): Long {
        val current = currentPeriodStart(mode)
        val back =
            when (mode) {
                PeriodCalendar.WEEKLY -> SYNC_WINDOW_WEEKS - 1
                PeriodCalendar.MONTHLY -> SYNC_WINDOW_MONTHS - 1
                PeriodCalendar.ANNUALLY -> SYNC_WINDOW_YEARS - 1
                else -> 0
            }
        return if (back <= 0) current else PeriodCalendar.shift(mode, current, -back)
    }

    /**
     * 判断指定周期的统计是否已在本地缓存中。
     *
     * @param mode 周期类型，取 weekly 或 monthly
     * @param baseTimeSec 周期起点秒数
     * @return 已有可绘制快照时返回 true
     */
    fun hasPeriod(
        mode: String,
        baseTimeSec: Long,
    ): Boolean = period(mode, baseTimeSec) != null

    /**
     * 请求后台同步缺失的周期数据。
     *
     * 当前同步宿主只提供整轮同步入口，因此这里触发一轮同步并在结束后回调；
     * 同步运行在宿主的工作线程上，本方法不阻塞调用线程。宿主缺失或已在同步时
     * 返回 false，由页面自行轮询等待。
     *
     * @param mode 周期类型，取 weekly 或 monthly
     * @param baseTimeSec 目标周期起点秒数，保留给单期同步接口使用
     * @param onFinished 同步结束回调，参数表示本轮同步是否取得数据
     * @return 是否已启动新一轮同步
     */
    fun requestPeriod(
        mode: String,
        baseTimeSec: Long,
        onFinished: (Boolean) -> Unit,
    ): Boolean {
        val host = AppServices.wereadSync ?: return false
        if (host.isRunning()) return false
        return host.refresh(WereadRefreshRequest(mode, baseTimeSec)) { ok, _ ->
            InkLog.d(TAG, "周期同步结束 mode=$mode base=$baseTimeSec ok=$ok")
            onFinished(ok)
        }
    }

    /** 读取当前期起点，用于周期选择器的右边界。 */
    fun currentPeriodStart(mode: String): Long = PeriodCalendar.startOf(mode, PeriodCalendar.nowSec())

    /**
     * 读取笔记池。
     *
     * 内核装载器只从 `nt_index.json` 的**书目条目顶层**取书名与作者，而十雨同步写入的索引
     * 把书信息放在条目的 `book` 对象里，因此条目本身的书名、作者为空，本记页会显示
     * 「（未命名）」。这里按书目编号回查索引补齐书名、作者与封面；补不到时保持原值。
     *
     * @param limit 最多返回的条数，0 表示全部；按 `nt_index.json` 中的书目顺序拼接
     */
    fun notes(limit: Int = 0): List<NoteItem> {
        val list = runCatching { loader.notes(limit) }.getOrDefault(emptyList())
        if (list.isEmpty()) return list
        val meta = runCatching { noteBookMeta() }.getOrDefault(emptyMap())
        if (meta.isEmpty()) return list
        list.forEach { note ->
            val info = meta[note.bookId] ?: return@forEach
            if (note.title.isBlank()) note.title = info.title
            if (note.author.isBlank()) note.author = info.author
            if (note.coverUrl.isBlank()) note.coverUrl = info.coverUrl
        }
        return list
    }

    /**
     * 解析 `nt_index.json` 中每本书的展示字段，按文件修改时间缓存。
     *
     * @return 书目编号到书名、作者、封面的映射；索引缺失或解析失败时返回空映射
     */
    private fun noteBookMeta(): Map<String, NoteBookMeta> {
        val stamp = File(root, NOTE_INDEX_FILE).lastModified()
        if (stamp == noteMetaStamp) return noteMetaCache
        val map =
            runCatching {
                val text = store.readText(NOTE_INDEX_FILE) ?: return@runCatching emptyMap()
                val rootObject = JSONObject(text)
                val books: JSONArray = rootObject.optJSONArray("books") ?: JSONArray()
                (0 until books.length())
                    .mapNotNull { books.optJSONObject(it) }
                    .mapNotNull { entry -> noteBookMetaOf(entry) }
                    .toMap()
            }.getOrDefault(emptyMap())
        noteMetaCache = map
        noteMetaStamp = stamp
        return map
    }

    /**
     * 把索引中的一条书目条目转换为展示字段。
     *
     * @param entry 书目条目对象，书信息可能位于 `book` 子对象内
     * @return 书目编号与展示字段的键值对，缺少编号时返回空
     */
    private fun noteBookMetaOf(entry: JSONObject): Pair<String, NoteBookMeta>? {
        val bookId = entry.optString("bookId").takeIf { it.isNotEmpty() } ?: return null
        val book = entry.optJSONObject("book") ?: entry
        return bookId to
            NoteBookMeta(
                title = book.optString("title"),
                author = composeAuthor(book.optString("author"), book.optString("translator")),
                coverUrl = book.optString("cover"),
            )
    }

    /**
     * 拼接本记署名行的作者文案。
     *
     * 微读原版索引里的作者字段已含「著」「绘」等说明，十雨网关分别返回作者与译者，
     * 这里按通行格式拼成「作者 著 译者 译」；只有一项时直接返回该项。
     *
     * @param author 作者名，可为空
     * @param translator 译者名，可为空
     */
    private fun composeAuthor(
        author: String,
        translator: String,
    ): String =
        when {
            author.isEmpty() -> translator
            translator.isEmpty() -> author
            else -> "$author 著 $translator 译"
        }

    /**
     * 读取待办清单。
     *
     * 优先使用应用层装配的 [AppServices.wereadTodos]；该入口缺失时退回内核数据目录中的清单。
     */
    fun todos(): List<TodoEntry> {
        val host = AppServices.wereadTodos
        if (host != null) {
            return host.list().map { todo ->
                TodoEntry().apply {
                    id = todo.id
                    content = todo.content
                    dateSec = todo.dateSec
                    minuteOfDay = todo.minuteOfDay
                    done = todo.done
                    order = todo.order
                }
            }
        }
        return runCatching { loader.todos() }.getOrDefault(emptyList())
    }

    /** 把笔记条目转换为卡片内核使用的内容对象。 */
    fun contentOf(item: NoteItem): NoteContent =
        NoteContent().apply {
            bookmarkId = item.bookmarkId
            bookId = item.bookId
            title = item.title
            author = item.author
            coverUrl = item.coverUrl
            markText = item.markText
            ideaText = item.ideaText
            kind = item.kind
            range = item.range
            star = item.star
            chapterUid = item.chapterUid
            chapterIdx = item.chapterIdx
            chapterTitle = item.chapterTitle
            createTime = item.createTime
        }

    /**
     * 读取书目封面位图。
     *
     * 先查本地封面缓存，缺失时异步下载并在完成后回调，回调参数为不需要的参数占位。
     *
     * @param context 用于定位封面缓存目录的上下文
     * @param bookId 书目标识
     * @param coverUrl 封面远程地址，可为空
     * @param maxPx 解码后的最长边像素
     * @param onReady 下载完成后的回调
     */
    fun cover(
        context: Context,
        bookId: String,
        coverUrl: String?,
        maxPx: Int = COVER_PX,
        onReady: () -> Unit,
    ): Bitmap? = WereadCoverStore.bitmap(context, bookId, coverUrl, maxPx) { onReady() }

    private companion object {
        /** 封面解码后的最长边像素。 */
        const val COVER_PX: Int = 256

        /** 周期统计缓存文件名，同步流程按周期键合并写入。 */
        const val STATS_FILE_NAME: String = "stats.json"

        /** 笔记索引文件名，记录书目顺序与每本书的书名、作者等展示字段。 */
        const val NOTE_INDEX_FILE: String = "nt_index.json"

        /** 同步窗口覆盖的周数，与同步模块默认范围一致，含当前周。 */
        const val SYNC_WINDOW_WEEKS: Int = 12

        /** 同步窗口覆盖的月数，与同步模块默认范围一致，含当前月。 */
        const val SYNC_WINDOW_MONTHS: Int = 12

        /** 同步窗口覆盖的年数，与同步模块默认范围一致，含当前年。 */
        const val SYNC_WINDOW_YEARS: Int = 3

        /** 日志标签。 */
        const val TAG: String = "WereadAppData"
    }
}
