package com.shuiyu.octoberrain.feature.wereadapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.inkread.weekread.kernel.CardState
import com.inkread.weekread.kernel.CardSurface
import com.inkread.weekread.kernel.HitRegions
import com.inkread.weekread.kernel.NoteItem
import com.inkread.weekread.kernel.TodoEntry
import com.inkread.weekread.kernel.render.CardRenderer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 阅读页的整卡绘制视图。
 *
 * 使用内核全屏档渲染五种卡片形态：视图尺寸变化时把画布宽高与内边距写入
 * [CardState]，绘制由 `CardRenderer.draw` 完成；点按经 `CardState.hitTest` 分派，
 * 纵向拖动由视图自身处理，避免依赖内核按固定画布高度编写的滚动命中区。
 *
 * @param context 创建视图的上下文
 */
@Suppress("TooManyFunctions", "ReturnCount")
internal class WereadCardView(
    context: Context,
) : View(context) {
    /** 卡片视图对外事件回调，由阅读页实现。 */
    internal interface Listener {
        /** 请求打开墨台。 */
        fun onDesk()

        /** 请求打开微信读书中的当前书目。 */
        fun onOpenBook(bookId: String)

        /** 请求打开微信读书的阅读统计。 */
        fun onOpenReading()

        /** 请求弹出选书浮层。 */
        fun onPickBook()

        /** 请求弹出本记筛选菜单。 */
        fun onFilter()

        /** 请求导出当前划线。 */
        fun onExportNote(item: NoteItem)

        /** 待办勾选回调，参数为待办下标。 */
        fun onToggleTodo(index: Int)

        /** 当前划线序号变化回调，用于刷新页面上的进度文字。 */
        fun onNoteIndexChanged(
            index: Int,
            total: Int,
        )
    }

    /** 事件回调。 */
    var listener: Listener? = null

    /** 数据入口，页面与卡片共用一个实例。 */
    private val data = WereadAppData(context)

    /** 内核宿主适配，重绘回调直接刷新视图。 */
    private val host = WereadAppHost(context) { invalidate() }

    /** 内核卡片状态，构造后需要安装宿主。 */
    private val state: CardState

    /** 卡片渲染器，逐帧把状态画到画布。 */
    private val renderer: CardRenderer

    /** 绘制目标适配器，把视图尺寸与命中转发给内核状态。 */
    private val surface: Surface

    /** 内核要求宿主提供画布实例，实际绘制使用 onDraw 传入的画布。 */
    private val holderCanvas = Canvas()

    /** 当前形态。 */
    private var form: WereadCardForm = WereadCardForm.WEEKLY

    /** 当前周期起点秒数，仅周与月形态使用。 */
    private var baseTimeSec: Long = 0L

    /** 本记筛选：false 显示全部，true 只看想法。 */
    private var ideasOnly: Boolean = false

    /** 选书筛选的书目标识，空串表示全部书。 */
    private var bookFilter: String = ""

    /** 当前本记候选池。 */
    private var notePool: List<NoteItem> = emptyList()

    /** 当前本记下标。 */
    private var noteIndex: Int = 0

    /** 当前展示的划线，供翻页与导出使用。 */
    private var currentNote: NoteItem? = null

    /** 手指按下的屏幕纵坐标。 */
    private var downY: Float = 0f

    /** 上一次移动事件的纵坐标。 */
    private var lastY: Float = 0f

    /** 是否已进入拖动滚动状态。 */
    private var scrolling: Boolean = false

    /** 判定拖动的最小位移，取系统阈值与 12 像素的较大者。 */
    private val dragSlop: Int = maxOf(ViewConfiguration.get(context).scaledTouchSlop, DRAG_SLOP_PX)

    init {
        surface = Surface()
        state = CardState(surface)
        state.setHost(host)
        renderer = CardRenderer(state)
        setBackgroundColor(0xFFFFFFFF.toInt())
        isClickable = true
        isFocusable = true
    }

    /** 读取当前形态。 */
    fun form(): WereadCardForm = form

    /** 读取当前划线序号与池规模，供页面显示进度。 */
    fun noteProgress(): Pair<Int, Int> = noteIndex to notePool.size

    /** 读取当前划线，没有选中时返回 null。 */
    fun currentNote(): NoteItem? = currentNote

    /** 读取当前是否只看想法。 */
    fun ideasOnly(): Boolean = ideasOnly

    /** 读取当前选书筛选的书目标识，空串表示全部。 */
    fun selectedBookId(): String = bookFilter

    /** 丢弃数据缓存，下一次绑定重新读取磁盘。 */
    fun reload() {
        data.reload()
    }

    /**
     * 绑定形态与周期数据并重绘。
     *
     * @param newForm 目标形态
     * @param newBaseTimeSec 周期起点秒数，周与月形态使用
     * @param refreshing 是否绘制刷新中的抬头
     */
    fun bind(
        newForm: WereadCardForm,
        newBaseTimeSec: Long,
        refreshing: Boolean = false,
    ) {
        val formChanged = form != newForm || baseTimeSec != newBaseTimeSec
        form = newForm
        baseTimeSec = newBaseTimeSec
        applyCommon(refreshing, formChanged)
        when (newForm) {
            WereadCardForm.WEEKLY, WereadCardForm.MONTHLY -> fillPeriod(newForm.mode)
            WereadCardForm.BOOK -> fillBook()
            WereadCardForm.NOTE -> fillNote()
            WereadCardForm.TODO -> fillTodo()
        }
        invalidate()
    }

    /** 重新读取当前形态的数据并重绘。 */
    fun refresh() {
        data.reload()
        bind(form, baseTimeSec)
    }

    /** 切换到下一条划线，超过池尾时回到开头。 */
    fun nextNote() {
        if (notePool.isEmpty()) return
        noteIndex = (noteIndex + 1) % notePool.size
        bind(WereadCardForm.NOTE, baseTimeSec)
    }

    /** 切换到上一条划线，超过池头时回到末尾。 */
    fun previousNote() {
        if (notePool.isEmpty()) return
        noteIndex = if (noteIndex == 0) notePool.size - 1 else noteIndex - 1
        bind(WereadCardForm.NOTE, baseTimeSec)
    }

    /** 在全部与只看想法之间切换本记筛选。 */
    fun toggleFilter() {
        ideasOnly = !ideasOnly
        noteIndex = 0
        bind(WereadCardForm.NOTE, baseTimeSec)
    }

    /**
     * 直接设置本记筛选状态，供筛选菜单选择后调用。
     *
     * @param value 为真时只显示带有想法的划线，为假时显示全部划线
     */
    fun setIdeasOnly(value: Boolean) {
        if (ideasOnly == value) return
        ideasOnly = value
        noteIndex = 0
        bind(WereadCardForm.NOTE, baseTimeSec)
    }

    /**
     * 选定书目后重新构建本记候选池。
     *
     * @param bookId 书目标识，空串表示全部书的划线
     */
    fun selectBook(bookId: String) {
        bookFilter = bookId
        noteIndex = 0
        bind(WereadCardForm.NOTE, baseTimeSec)
    }

    /** 释放内核状态持有的封面等资源。 */
    fun release() {
        state.setCoverBitmap(null, "")
    }

    /** 视图尺寸确定后同步内核画布尺寸与号值。 */
    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyMetrics(w, h)
    }

    /** 绘制当前卡片。 */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        applyMetrics(width, height)
        renderer.draw(canvas)
    }

    /** 处理点按与纵向拖动。 */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                lastY = event.y
                scrolling = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastY
                lastY = event.y
                if (!scrolling && abs(event.y - downY) > dragSlop.toFloat()) scrolling = true
                if (scrolling) consumeScroll(dy)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (scrolling) {
                    settleScroll()
                } else {
                    handleTap(event.x, event.y)
                    performClick()
                }
                scrolling = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                scrolling = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** 满足可访问性要求，点按在 ACTION_UP 内统一处理。 */
    override fun performClick(): Boolean = super.performClick()

    /** 写入全屏画布尺寸、内边距与号值。 */
    private fun applyMetrics(
        w: Int,
        h: Int,
    ) {
        if (w <= 0 || h <= 0) return
        state.setFullScreenMetrics(w, h, PAD_X_RATIO)
        state.setUnit(unitPx())
    }

    /** 号对应的像素值，取屏长边的千分之一点五。 */
    private fun unitPx(): Float {
        val metrics = resources.displayMetrics
        return maxOf(metrics.widthPixels, metrics.heightPixels) * UNIT_PER_LONG_EDGE
    }

    /** 写入与形态无关的公共状态，形态或周期变化时清空滚动位置。 */
    private fun applyCommon(
        refreshing: Boolean,
        formChanged: Boolean,
    ) {
        state.setApiKey(LOCAL_API_KEY)
        state.setRefreshing(refreshing)
        state.setDeskEnabled(true)
        state.setDeskOpenLabel("打开墨台")
        state.setMonthHeatmap(WereadAppPrefs.monthHeatmap(context))
        state.setNoteFullSize(WereadAppPrefs.noteFullSize(context))
        state.setErrorText(null)
        state.setPhMain(null)
        state.setPhSub(null)
        state.setNoteHint(null)
        state.setEmptyNote(null)
        state.setMode(form.mode)
        if (formChanged) {
            state.setNoteExpanded(false)
            state.resetScroll()
        }
    }

    /** 填充周与月形态的周期快照。 */
    private fun fillPeriod(mode: String) {
        val snapshot = data.period(mode, baseTimeSec)
        val empty = snapshot == null || (snapshot.totalSec <= 0 && snapshot.daySec?.none { it > 0 } != false)
        if (empty) {
            state.setStats(null)
            state.setPhMain("暂无记录")
            state.setPhSub("这个周期还没有阅读数据")
            return
        }
        state.setStats(snapshot)
    }

    /** 填充本书形态的书目进度与封面。 */
    private fun fillBook() {
        val book = data.currentBook()
        if (book == null) {
            state.setBook(null)
            state.setPhMain("暂无在读的书")
            state.setPhSub("同步书架后显示")
            return
        }
        state.setBook(book)
        state.setCoverBitmap(coverOf(book.bookId, book.coverUrl), book.bookId)
    }

    /** 填充本记形态的划线内容，并按筛选条件重建候选池。 */
    private fun fillNote() {
        val all = data.notes(NOTE_LIMIT)
        notePool =
            all
                .asSequence()
                .filter { bookFilter.isEmpty() || it.bookId == bookFilter }
                .filter { !ideasOnly || it.hasIdea() }
                .toList()
        if (notePool.isEmpty()) {
            currentNote = null
            state.setNote(null)
            state.setEmptyNote(if (ideasOnly) "这本书还没有想法" else "这本书还没有划线")
            listener?.onNoteIndexChanged(0, 0)
            return
        }
        noteIndex = noteIndex.coerceIn(0, notePool.lastIndex)
        val item = notePool[noteIndex]
        currentNote = item
        state.setNote(data.contentOf(item))
        state.setNoteSlot(if (ideasOnly) SLOT_IDEAS else SLOT_ALL)
        listener?.onNoteIndexChanged(noteIndex, notePool.size)
    }

    /** 填充待办形态的清单。 */
    private fun fillTodo() {
        val todos: List<TodoEntry> = data.todos()
        if (todos.isEmpty()) {
            state.setTodo(emptyList<TodoEntry>())
            state.setPhMain("暂无待办")
            state.setPhSub("在待办页添加一条")
            return
        }
        state.setTodo(todos)
    }

    /** 读取封面位图，缺失时异步下载并在完成后重绘。 */
    private fun coverOf(
        bookId: String,
        coverUrl: String?,
    ): Bitmap? = data.cover(context, bookId, coverUrl) { invalidate() }

    /** 处理一次点按，按内核命中编号分派。 */
    private fun handleTap(
        x: Float,
        y: Float,
    ) {
        when (val hit = state.hitTest(x, y)) {
            HitRegions.DESK -> listener?.onDesk()
            HitRegions.OPEN -> handleOpen()
            HitRegions.PICK_BOOK, HitRegions.NOTE_PICK -> listener?.onPickBook()
            HitRegions.PREV -> previousNote()
            HitRegions.EXPAND -> {
                state.toggleNoteExpanded()
                invalidate()
            }

            HitRegions.EXPORT -> currentNote?.let { listener?.onExportNote(it) }
            HitRegions.FILTER -> listener?.onFilter()

            HitRegions.NOTE_SCROLL, HitRegions.MONTH_SCROLL -> Unit
            else -> handleTodoHit(hit)
        }
    }

    /** 空命中判定为点按卡片主体，按形态打开微信读书对应位置。 */
    private fun handleOpen() {
        if (form == WereadCardForm.BOOK) {
            val bookId = data.currentBook()?.bookId.orEmpty()
            if (bookId.isNotEmpty()) listener?.onOpenBook(bookId)
        } else {
            listener?.onOpenReading()
        }
    }

    /** 处理待办条目命中，编号为固定基数加下标。 */
    private fun handleTodoHit(hit: Int) {
        val index =
            when {
                hit >= HitRegions.TODO_ITEM_BASE && hit < HitRegions.TODO_ITEM_BASE + HitRegions.TODO_MAX ->
                    hit - HitRegions.TODO_ITEM_BASE
                hit >= HitRegions.TODO_BASE && hit < HitRegions.TODO_BASE + HitRegions.TODO_MAX ->
                    hit - HitRegions.TODO_BASE
                else -> -1
            }
        if (index >= 0) listener?.onToggleTodo(index)
    }

    /** 消耗一次纵向拖动，本记逐行滚动，本月整页滚动。 */
    private fun consumeScroll(dy: Float) {
        if (dy == 0f) return
        when (form) {
            WereadCardForm.NOTE -> {
                val max = state.noteScrollMax()
                if (max <= 0f) return
                state.setNoteScrollY((state.noteScrollY() - dy).coerceIn(0f, max))
            }

            WereadCardForm.MONTHLY -> {
                val max = state.monthScrollMax()
                if (max <= 0f) return
                state.setMonthScrollY((state.monthScrollY() - dy).coerceIn(0f, max))
            }

            else -> return
        }
        invalidate()
    }

    /** 松手后把本记滚动位置吸附到整行，本月保持连续位置。 */
    private fun settleScroll() {
        if (form != WereadCardForm.NOTE) return
        val lineH = state.noteLineH()
        if (lineH <= 0f) return
        val snapped = (state.noteScrollY() / lineH).roundToInt() * lineH
        state.setNoteScrollY(snapped.coerceIn(0f, state.noteScrollMax()))
        invalidate()
    }

    /** 绘制目标适配器，向内核提供视图尺寸、命中与动作入口。 */
    private inner class Surface : CardSurface {
        /** 内核要求的画布，实际绘制走视图画布。 */
        override fun canvas(): Canvas = holderCanvas

        /** 视图宽度，即全屏画布宽度。 */
        override fun width(): Int = this@WereadCardView.width

        /** 视图高度，即全屏画布高度。 */
        override fun height(): Int = this@WereadCardView.height

        /** 命中判定转发给内核状态。 */
        override fun hitTest(
            x: Float,
            y: Float,
        ): Int = state.hitTest(x, y)

        /** 动作回调转发给内核状态并请求重绘。 */
        override fun onAction(action: Int) {
            state.onAction(action)
            invalidate()
        }
    }

    private companion object {
        /** 卡片全屏档的横向内边距比例。 */
        const val PAD_X_RATIO: Float = 0.05f

        /** 号相对屏长边的比例。 */
        const val UNIT_PER_LONG_EDGE: Float = 0.0015f

        /** 判定拖动的最小位移，文档要求 12 像素。 */
        const val DRAG_SLOP_PX: Int = 12

        /** 笔记池最多读取的条数。 */
        const val NOTE_LIMIT: Int = 0

        /** 本记「全部」槽位编号。 */
        const val SLOT_ALL: Int = 0

        /** 本记「只看想法」槽位编号。 */
        const val SLOT_IDEAS: Int = 1

        /** 本地渲染标记，避免内核绘制未配置密钥提示。 */
        const val LOCAL_API_KEY: String = "local-page"
    }
}
