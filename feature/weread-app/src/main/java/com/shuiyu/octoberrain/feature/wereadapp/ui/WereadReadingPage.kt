package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.inkread.weekread.core.BookStats
import com.inkread.weekread.core.NoteStats
import com.inkread.weekread.core.PeriodStats
import com.inkread.weekread.core.TodoItem
import com.inkread.weekread.feature.InsightPageView
import com.inkread.weekread.feature.NoteExport
import com.inkread.weekread.feature.WeekCardView
import com.inkread.weekread.kernel.BookProgress
import com.inkread.weekread.kernel.HitRegions
import com.inkread.weekread.kernel.NoteItem
import com.inkread.weekread.kernel.PeriodCalendar
import com.inkread.weekread.kernel.ShelfBook
import com.inkread.weekread.ui.NavDropView
import com.inkread.weekread.ui.PeriodPickerView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.wereadapp.WereadAppData
import com.shuiyu.octoberrain.feature.wereadapp.WereadAppPrefs
import com.shuiyu.octoberrain.feature.wereadapp.WereadCardForm
import com.shuiyu.octoberrain.feature.wereadapp.WereadMicroBridge
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * 阅读页。
 *
 * 版式与微读墨记 `mp_reader` 一致：顶部四段主导航（首项「本周▽」聚合形态下拉）、
 * 周期选择器、全屏卡片、洞察页与底部整宽刷新按钮。卡片与洞察页直接复用
 * `com.inkread.weekread` 下的微读控件，数据由 [WereadAppData] 读取十雨落盘的
 * `files/weread` 后转换成微读模型注入。
 *
 * @param context 创建视图的上下文
 */
@Suppress("TooManyFunctions")
internal class WereadReadingPage(
    context: Context,
) : FrameLayout(context) {
    /** 阅读页对外回调。 */
    internal interface Listener {
        /** 请求打开墨台。 */
        fun onOpenDesk()

        /** 请求打开微信读书中的指定书目。 */
        fun onOpenBook(bookId: String)

        /** 请求打开微信读书的阅读统计页。 */
        fun onOpenReading()

        /** 请求显示轻提示。 */
        fun onToast(message: String)

        /** 当前形态文案变化，用于同步外层主导航首项。 */
        fun onFormChanged(label: String)
    }

    /** 外部回调。 */
    var listener: Listener? = null

    /** 数据访问入口。 */
    private val data = WereadAppData(context)

    /** 当前页签内容形态。 */
    private enum class Stage {
        CARD,
        INSIGHT,
    }

    /** 周期选择器。 */
    private val picker = PeriodPickerView(context)

    /** 卡片区，复用微读控件。 */
    private val card = WeekCardView(context)

    /** 洞察页，复用微读控件。 */
    private val insight = InsightPageView(context)

    /** 底部整宽刷新按钮。 */
    private val refreshButton =
        InkWidgets.button(context, "刷新", filled = false) {
            refresh()
        }

    /** 形态下拉浮层，扳机行由主导航首项承担。 */
    private val navDrop = NavDropView(context)

    /** 形态池内可选的卡片形态列表，顺序与下拉前几项一致。 */
    private var navForms: List<WereadCardForm> = FORMS

    /** 周形态的周期起点秒数。 */
    private var weekBase: Long = 0L

    /** 月形态的周期起点秒数。 */
    private var monthBase: Long = 0L

    /** 当前形态。 */
    private var form: WereadCardForm = WereadCardForm.WEEKLY

    /** 当前阶段。 */
    private var stage: Stage = Stage.CARD

    /** 本记池。 */
    private var notePool: List<NoteItem> = emptyList()

    /** 本记池当前下标。 */
    private var noteIndex: Int = 0

    /** 是否只看想法。 */
    private var ideasOnly: Boolean = false

    /** 选中的书目编号，空串表示全部书。 */
    private var bookFilter: String = ""

    /** 等待同步的周期类型。 */
    private var pendingMode: String = ""

    /** 等待同步的周期起点，0 表示没有进行中的拉取。 */
    private var pendingBase: Long = 0L

    /** 等待同步的起始时刻。 */
    private var pendingSince: Long = 0L

    /** 主线程定时器，用于轮询同步结果。 */
    private val handler = Handler(Looper.getMainLooper())

    /** 单轮同步等待的轮询任务。 */
    private val pollTask =
        object : Runnable {
            override fun run() {
                pollPending()
            }
        }

    init {
        setBackgroundColor(InkColors.PAPER)
        addView(buildContent(), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(navDrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        navDrop.setTriggerless(true)
        refreshNavItems()
        navDrop.setListener { index -> pickNav(index) }
        picker.setListener { delta -> shiftPeriod(delta) }
        card.setOpenListener(cardOpenListener())
        card.setNoteListener(cardNoteListener())
        card.setFullscreen(true)
        card.setPadXRatio(PAD_X_RATIO)
        insight.visibility = View.GONE
        refreshButton.setOnClickListener { refresh() }
        applyForm(WereadCardForm.WEEKLY)
    }

    /**
     * 按可视高度封顶测量。
     *
     * 阅读页内部用权重分配卡片区高度，底部刷新行固定在垂直根末尾。若父容器以
     * `AT_MOST` 或 `UNSPECIFIED` 测量本页（例如滚动容器或某些负一屏宿主），
     * LinearLayout 会按内容需要的高度测量，权重失效后刷新行会被挤到屏幕外。
     * 这里把高度测量改为「至多可视高度」，保证刷新按钮在任何宿主里都留在屏内。
     *
     * @param widthMeasureSpec 宽度测量规格
     * @param heightMeasureSpec 高度测量规格
     */
    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val viewport = resources.displayMetrics.heightPixels
        val height =
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                viewport
            } else {
                MeasureSpec.getSize(heightMeasureSpec).coerceAtMost(viewport)
            }
        val capped =
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY &&
                MeasureSpec.getSize(heightMeasureSpec) == height
            ) {
                heightMeasureSpec
            } else {
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
            }
        super.onMeasure(widthMeasureSpec, capped)
    }

    /** 页面回到前台时重新读取数据并重绘。 */
    fun refresh() {
        cancelPending()
        data.reload()
        refreshNavItems()
        emitFormLabel()
        when (stage) {
            Stage.INSIGHT -> bindInsight()
            Stage.CARD -> bindCard()
        }
    }

    /** 释放定时任务与图片资源。 */
    fun release() {
        cancelPending()
        handler.removeCallbacksAndMessages(null)
        card.setCoverBitmap(null, null)
    }

    /** 构造页面主体：周期行、卡片区、刷新按钮。 */
    private fun buildContent(): View {
        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        root.addView(picker, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dpRound(PICKER_HEIGHT_DP)))
        val stageHost = FrameLayout(context)
        stageHost.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        stageHost.addView(insight, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        root.addView(stageHost, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(InkWidgets.divider(context))
        val buttonRow = LinearLayout(context)
        buttonRow.orientation = LinearLayout.HORIZONTAL
        val padX = dp(12f)
        val padY = dp(8f)
        buttonRow.setPadding(padX, padY, padX, padY)
        buttonRow.addView(
            refreshButton,
            LinearLayout.LayoutParams(0, dp(40f), 1f),
        )
        root.addView(buttonRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        return root
    }

    /** 把 dp 值换算为像素。 */
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    /**
     * 把 dp 四舍五入换算为整数像素。
     *
     * 周期行与微读 `activity_main.xml` 的 44dp 对齐；直接截断会在 2.25 倍密度等
     * 档位产生 1px 偏差，因此这里用 `TypedValue` 取整。
     *
     * @param value dp 值
     * @return 四舍五入后的像素值
     */
    private fun dpRound(value: Float): Int =
        TypedValue
            .applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                resources.displayMetrics,
            ).roundToInt()

    /** 展开形态下拉，供外层主导航首项点击时调用。 */
    fun toggleFormMenu() {
        navDrop.toggle()
    }

    /** 把当前形态文案同步给外层主导航。 */
    private fun emitFormLabel() {
        val label = if (stage == Stage.INSIGHT) "足迹" else form.label
        listener?.onFormChanged(label)
    }

    /** 按形态池重建下拉项目；至少保留一张卡片，末项「足迹」不参与过滤。 */
    private fun refreshNavItems() {
        val mask = WereadAppPrefs.settings(context).poolMask
        val enabled = FORMS.filterIndexed { index, _ -> mask and POOL_BITS[index] != 0 }
        navForms = enabled.ifEmpty { FORMS }
        val labels = navForms.map { it.label } + NAV_INSIGHT
        navDrop.setLabels(labels.toTypedArray())
    }

    /** 处理形态下拉选择。 */
    private fun pickNav(index: Int) {
        navDrop.collapse()
        if (index >= navForms.size) {
            showInsight()
            return
        }
        showForm(navForms[index])
    }

    /** 切到指定形态并重绘卡片。 */
    private fun showForm(next: WereadCardForm) {
        stage = Stage.CARD
        card.visibility = View.VISIBLE
        insight.visibility = View.GONE
        picker.visibility =
            if (next == WereadCardForm.WEEKLY || next == WereadCardForm.MONTHLY) {
                View.VISIBLE
            } else {
                View.GONE
            }
        applyForm(next)
    }

    /** 切到洞察页。 */
    private fun showInsight() {
        stage = Stage.INSIGHT
        cancelPending()
        card.visibility = View.GONE
        insight.visibility = View.VISIBLE
        picker.visibility = View.GONE
        emitFormLabel()
        bindInsight()
    }

    /** 应用形态：更新导航文案与周期选择器后重绑数据。 */
    private fun applyForm(next: WereadCardForm) {
        form = next
        emitFormLabel()
        when (next) {
            WereadCardForm.WEEKLY -> picker.setPeriod(PeriodCalendar.WEEKLY, ensureWeekBase())
            WereadCardForm.MONTHLY -> picker.setPeriod(PeriodCalendar.MONTHLY, ensureMonthBase())
            else -> Unit
        }
        bindCard()
    }

    /** 周形态当前起点。 */
    private fun ensureWeekBase(): Long {
        if (weekBase == 0L) weekBase = data.currentPeriodStart(PeriodCalendar.WEEKLY)
        return weekBase
    }

    /** 月形态当前起点。 */
    private fun ensureMonthBase(): Long {
        if (monthBase == 0L) monthBase = data.currentPeriodStart(PeriodCalendar.MONTHLY)
        return monthBase
    }

    /** 当前形态对应的周期起点。 */
    private fun currentBase(): Long =
        when (form) {
            WereadCardForm.MONTHLY -> ensureMonthBase()
            else -> ensureWeekBase()
        }

    /** 周期选择器移动。 */
    private fun shiftPeriod(delta: Int) {
        if (form != WereadCardForm.WEEKLY && form != WereadCardForm.MONTHLY) return
        val mode = form.mode
        val current = currentBase()
        val target = PeriodCalendar.shift(mode, current, delta)
        val earliest = data.earliestPeriodStart(mode)
        val latest = data.currentPeriodStart(mode)
        val clamped = target.coerceIn(earliest, latest)
        if (clamped == current) return
        if (form == WereadCardForm.MONTHLY) monthBase = clamped else weekBase = clamped
        picker.setPeriod(mode, clamped)
        bindCard()
        if (!data.hasPeriod(mode, clamped)) requestPeriod(mode, clamped)
    }

    /** 请求整轮同步以补齐缺失周期。 */
    private fun requestPeriod(
        mode: String,
        base: Long,
    ) {
        if (pendingBase != 0L) return
        val started =
            data.requestPeriod(mode, base) {
                post {
                    data.reload()
                    finishPending()
                }
            }
        if (!started) return
        pendingMode = mode
        pendingBase = base
        pendingSince = System.currentTimeMillis()
        card.setPlaceholder("同步中…", "正在同步该周期数据")
        handler.postDelayed(pollTask, POLL_INTERVAL_MS)
    }

    /** 轮询同步结果，超时或取到数据后收尾。 */
    private fun pollPending() {
        if (pendingBase == 0L) return
        data.reload()
        val ready = data.hasPeriod(pendingMode, pendingBase)
        val timedOut = System.currentTimeMillis() - pendingSince > SYNC_TIMEOUT_MS
        if (ready || timedOut) {
            finishPending()
            return
        }
        handler.postDelayed(pollTask, POLL_INTERVAL_MS)
    }

    /** 收尾等待态并重绘当前周期。 */
    private fun finishPending() {
        val mode = pendingMode
        val base = pendingBase
        val timedOut = System.currentTimeMillis() - pendingSince > SYNC_TIMEOUT_MS
        cancelPending()
        if (mode == PeriodCalendar.WEEKLY) weekBase = base else monthBase = base
        picker.setPeriod(mode, base)
        bindCard()
        if (!data.hasPeriod(mode, base)) {
            val hint = if (timedOut) "同步超时，该周期暂无数据" else "该周期暂无阅读数据"
            listener?.onToast(hint)
        }
    }

    /** 取消等待态。 */
    private fun cancelPending() {
        handler.removeCallbacks(pollTask)
        pendingMode = ""
        pendingBase = 0L
        pendingSince = 0L
    }

    /** 绑定卡片数据。 */
    private fun bindCard() {
        if (stage != Stage.CARD) return
        val prefs = WereadAppPrefs.settings(context)
        card.setMode(form.mode)
        card.setMonthHeatmap(prefs.monthHeatmap)
        card.setNoteFullSize(noteFullSize(prefs.exportSize))
        card.setNoteCardSize(prefs.noteCardSize.toFloat())
        card.setAchievementText(null)
        card.setNoteSlot(if (ideasOnly) 1 else 0)
        when (form) {
            WereadCardForm.WEEKLY, WereadCardForm.MONTHLY -> bindPeriodCard()
            WereadCardForm.BOOK -> bindBookCard()
            WereadCardForm.NOTE -> bindNoteCard()
            WereadCardForm.TODO -> bindTodoCard()
        }
    }

    /** 绑定周、月统计。 */
    private fun bindPeriodCard() {
        val mode = form.mode
        val base = currentBase()
        val prefs = data.prefs()
        val raw = runCatching { prefs.getString("d_$mode:$base", null) }.getOrNull()
        if (raw.isNullOrEmpty()) {
            card.setStats(null)
            card.setPlaceholder("暂无记录", "这个周期还没有阅读数据")
            return
        }
        val fetchedAt = runCatching { prefs.getLong("t_$mode:$base", 0L) }.getOrDefault(0L)
        val stats =
            runCatching {
                PeriodStats.parse(JSONObject(raw), fetchedAt, mode, base)
            }.getOrNull()
        if (stats == null) {
            card.setStats(null)
            card.setPlaceholder("暂无记录", "这个周期还没有阅读数据")
        } else {
            card.setStats(stats)
        }
    }

    /** 绑定本书进度。 */
    private fun bindBookCard() {
        val book = data.currentBook()
        if (book == null) {
            card.setBook(null)
            return
        }
        card.setBook(toBookStats(book))
        val bitmap = data.cover(context, book.bookId, book.coverUrl) { card.invalidate() }
        card.setCoverBitmap(bitmap, book.bookId)
    }

    /** 绑定本记条目。 */
    private fun bindNoteCard() {
        val all = runCatching { data.notes(0) }.getOrDefault(emptyList())
        notePool =
            all.filter { note ->
                val matchBook = bookFilter.isEmpty() || note.bookId == bookFilter
                val matchIdea = !ideasOnly || note.ideaText.isNotEmpty()
                matchBook && matchIdea
            }
        if (noteIndex >= notePool.size) noteIndex = 0
        val slot = if (ideasOnly) 1 else 0
        val item = notePool.getOrNull(noteIndex)
        if (item == null) {
            // 池为空也写入 0/0，避免卡片继续显示上一次的「第 X / 共 N 条」。
            WereadMicroBridge.recordNoteProgress(context, slot, 0, 0)
            card.setNote(null)
            card.setPlaceholder(
                "暂无划线",
                if (ideasOnly) "这本书还没有想法" else "这本书还没有划线",
            )
            return
        }
        // 复制进来的内核绘制在「本记」页读取该进度，池由本页按筛选条件维护，需同步落盘。
        WereadMicroBridge.recordNoteProgress(context, slot, noteIndex + 1, notePool.size)
        val stats = NoteStats()
        copyNote(item, stats)
        card.setNote(stats)
        val bitmap = data.cover(context, item.bookId, item.coverUrl) { card.invalidate() }
        card.setCoverBitmap(bitmap, item.bookId)
    }

    /** 绑定待办清单。 */
    private fun bindTodoCard() {
        val entries = runCatching { data.todos() }.getOrDefault(emptyList())
        val items =
            entries.map {
                TodoItem(it.id, it.content).apply {
                    dateSec = it.dateSec
                    minuteOfDay = it.minuteOfDay
                    done = it.done
                    order = it.order
                }
            }
        card.setTodo(items)
    }

    /** 绑定洞察页数据。 */
    private fun bindInsight() {
        val nowSec = PeriodCalendar.nowSec()
        val year = PeriodCalendar.yearOf(nowSec)
        val annual = parseStored("stats_annual_$year", PeriodCalendar.ANNUALLY, 0L)
        val overall = parseStored("overall", PeriodCalendar.OVERALL, 0L)
        insight.setAnnual(annual)
        val chars = runCatching { data.notes(0).sumOf { it.ideaText.length } }.getOrDefault(0)
        insight.setOverall(overall, chars)
        val profile = overall ?: annual
        if (profile != null) {
            insight.setProfile(
                profile.preferCategory,
                profile.preferAuthor,
                profile.preferTime,
                "累计",
                chars,
                yearsSince(profile.registTimeMs),
            )
        }
        insight.resetScroll()
    }

    /** 读取并解析指定偏好键的统计快照。 */
    private fun parseStored(
        key: String,
        mode: String,
        base: Long,
    ): PeriodStats? {
        val raw = runCatching { data.prefs().getString(key, null) }.getOrNull()
        if (raw.isNullOrEmpty()) return null
        return runCatching { PeriodStats.parse(JSONObject(raw), 0L, mode, base) }.getOrNull()
    }

    /** 已陪伴的年数。 */
    private fun yearsSince(registTimeMs: Long): Int {
        if (registTimeMs <= 0L) return 1
        return ((System.currentTimeMillis() - registTimeMs) / YEAR_MILLIS).toInt().coerceAtLeast(1)
    }

    /** 本记正文全屏档字号，按导出字号档位映射。 */
    private fun noteFullSize(tier: Int): Float =
        when (tier) {
            0 -> 15f
            2 -> 19f
            else -> 17f
        }

    /** 把内核书目进度转换为微读模型。 */
    private fun toBookStats(progress: BookProgress): BookStats =
        BookStats().apply {
            bookId = progress.bookId
            title = progress.title
            author = progress.author
            deepLink = progress.deepLink
            coverUrl = progress.coverUrl
            this.progress = progress.progress
            readingSec = progress.readingSec
            chapterUid = progress.chapterUid
            chapterIdx = progress.chapterIdx
            chapterTitle = progress.chapterTitle
            readUpdateTime = progress.readUpdateTime
            fetchedAt = progress.fetchedAt
            finishTime = progress.finishTime
            finished = progress.finished
        }

    /** 把内核笔记条目复制为微读模型。 */
    private fun copyNote(
        item: NoteItem,
        target: NoteStats,
    ) {
        target.bookmarkId = item.bookmarkId
        target.bookId = item.bookId
        target.title = item.title
        target.author = item.author
        target.coverUrl = item.coverUrl
        target.markText = item.markText
        target.ideaText = item.ideaText
        target.kind = item.kind
        target.range = item.range
        target.star = item.star
        target.chapterUid = item.chapterUid
        target.chapterIdx = item.chapterIdx
        target.chapterTitle = item.chapterTitle
        target.createTime = item.createTime
    }

    /** 卡片「打开 / 选书 / 打开墨台」回调。 */
    private fun cardOpenListener(): WeekCardView.OpenListener =
        object : WeekCardView.OpenListener {
            override fun onOpen() {
                if (form == WereadCardForm.BOOK) {
                    val id = data.currentBook()?.bookId
                    if (!id.isNullOrEmpty()) listener?.onOpenBook(id) else listener?.onOpenReading()
                } else {
                    listener?.onOpenReading()
                }
            }

            override fun onPickBook() {
                showBookPickSheet()
            }

            override fun onOpenDesk() {
                listener?.onOpenDesk()
            }
        }

    /** 本记页内按钮回调。 */
    private fun cardNoteListener(): WeekCardView.NoteListener =
        object : WeekCardView.NoteListener {
            override fun onNextNote() {
                moveNote(1)
            }

            override fun onPrevNote() {
                moveNote(-1)
            }

            override fun onExportNote() {
                exportNote()
            }

            override fun onToggleIdeas() {
                ideasOnly = !ideasOnly
                noteIndex = 0
                bindNoteCard()
            }

            override fun onPickNote() {
                showNotePickSheet()
            }
        }

    /** 本记池前后翻页。 */
    private fun moveNote(delta: Int) {
        if (notePool.isEmpty()) return
        noteIndex = (noteIndex + delta).mod(notePool.size)
        bindNoteCard()
    }

    /**
     * 把当前本记条目导出为 PNG 长图。
     *
     * 复用微读 [NoteExport] 的排版与保存链路：按设置页的纸张底纹、字号档位、署名与
     * 章节日期开关生成 480 宽长图，写入系统相册 `Pictures/微读墨记/`，失败时回退应用
     * 外部目录。位图分配与 PNG 压缩在后台线程执行，完成后回到主线程提示并呼起分享。
     */
    private fun exportNote() {
        val item = notePool.getOrNull(noteIndex) ?: return
        val stats = noteStatsOf(item)
        listener?.onToast("正在导出…")
        Thread {
            val result = NoteExport.renderAndSave(context, stats)
            post {
                val activity = context as? Activity
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    NoteExport.presentResult(activity, result)
                } else if (!result.err.isNullOrEmpty()) {
                    listener?.onToast(result.err)
                }
            }
        }.start()
    }

    /**
     * 把内核笔记条目转换成导出排版使用的 [NoteStats]。
     *
     * @param item 内核装载的单条笔记
     * @return 字段复制后的导出对象
     */
    private fun noteStatsOf(item: NoteItem): NoteStats =
        NoteStats().also { stats ->
            stats.bookmarkId = item.bookmarkId
            stats.bookId = item.bookId
            stats.title = item.title
            stats.author = item.author
            stats.coverUrl = item.coverUrl
            stats.markText = item.markText
            stats.ideaText = item.ideaText
            stats.kind = item.kind
            stats.range = item.range
            stats.star = item.star
            stats.chapterUid = item.chapterUid
            stats.chapterIdx = item.chapterIdx
            stats.chapterTitle = item.chapterTitle
            stats.createTime = item.createTime
        }

    /** 选记半屏：按书目筛选本记池。 */
    private fun showNotePickSheet() {
        val books = shelfBooks()
        WereadNotePickSheet.show(context, books, bookFilter) { bookId ->
            bookFilter = bookId
            noteIndex = 0
            bindNoteCard()
        }
    }

    /** 选书半屏：切换「本书」页展示的书目。 */
    private fun showBookPickSheet() {
        val books = shelfBooks()
        val selected = data.currentBook()?.bookId.orEmpty()
        WereadBookPickSheet.show(context, books, selected) { bookId ->
            applyBookSelection(books, bookId)
        }
    }

    /** 读取书架书目。 */
    private fun shelfBooks(): List<ShelfBook> = runCatching { data.shelf()?.books.orEmpty() }.getOrDefault(emptyList())

    /**
     * 应用选书结果。
     *
     * @param books 当前书架书目
     * @param bookId 选定书目编号，空串表示自动选择最近在读
     */
    private fun applyBookSelection(
        books: List<ShelfBook>,
        bookId: String,
    ) {
        val target = if (bookId.isEmpty()) books.firstOrNull() else books.firstOrNull { it.bookId == bookId }
        if (target == null) {
            listener?.onToast("书架暂无书目")
            return
        }
        writeCurrentBook(target)
        data.reload()
        bindCard()
    }

    /**
     * 把选定书目写入 `files/weread/book.json`，键名与内核读取保持一致。
     *
     * @param book 选定的书架书目
     */
    private fun writeCurrentBook(book: ShelfBook) {
        val payload =
            JSONObject().apply {
                put("bookId", book.bookId)
                put("title", book.title)
                put("author", book.author)
                put("coverUrl", book.coverUrl)
                put("readingSec", book.readingSec)
                put("progress", book.progress)
                put("finished", book.finished)
                put("finishTime", book.finishTime)
            }
        runCatching {
            val directory = File(context.filesDir, WereadAppPrefs.DATA_DIRECTORY)
            if (!directory.exists()) directory.mkdirs()
            File(directory, BOOK_FILE_NAME).writeText(payload.toString())
        }.onFailure {
            listener?.onToast("书目保存失败")
        }
    }

    /** 待办勾选框点击。 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (stage == Stage.CARD && form == WereadCardForm.TODO && ev.action == MotionEvent.ACTION_UP) {
            val hit = card.hitTest(ev.x - card.left, ev.y - card.top)
            if (hit >= HitRegions.TODO_BASE) {
                val index = hit - HitRegions.TODO_BASE
                val entries = runCatching { data.todos() }.getOrDefault(emptyList())
                val entry = entries.getOrNull(index)
                if (entry != null) {
                    AppServices.wereadTodos?.toggle(entry.id)
                    data.reload()
                    bindTodoCard()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** 供外层切回阅读页时复位主导航选中项。 */
    internal fun selectReadingTab() {
        emitFormLabel()
    }

    companion object {
        /** 形态列表，顺序与下拉前四项一致。 */
        private val FORMS =
            listOf(
                WereadCardForm.WEEKLY,
                WereadCardForm.MONTHLY,
                WereadCardForm.BOOK,
                WereadCardForm.NOTE,
            )

        /** 下拉末项固定文案，对应洞察页。 */
        private const val NAV_INSIGHT = "足迹"

        /** 形态池位掩码，低五位依次对应本周、本月、本书、本记、待办。 */
        private val POOL_BITS: IntArray = intArrayOf(1, 2, 4, 8, 16)

        /** 卡片横向内边距比例，与微读 App 内一致。 */
        private const val PAD_X_RATIO = 0.05f

        /** 轮询间隔。 */
        private const val POLL_INTERVAL_MS = 1500L

        /** 单轮同步等待上限。 */
        private const val SYNC_TIMEOUT_MS = 120_000L

        /** 周期行高度，单位 dp，与微读 `PeriodPickerView` 挂载高度一致。 */
        private const val PICKER_HEIGHT_DP = 44f

        /** 当前书目文件名。 */
        private const val BOOK_FILE_NAME = "book.json"

        /** 一年毫秒数。 */
        private const val YEAR_MILLIS = 365L * 24L * 60L * 60L * 1000L
    }
}
