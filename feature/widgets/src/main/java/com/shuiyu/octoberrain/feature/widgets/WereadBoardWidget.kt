package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.WEREAD_BOARD_WIDGET_ID
import com.shuiyu.octoberrain.core.common.WereadAppearance
import com.shuiyu.octoberrain.core.common.WereadRefreshRequest
import com.shuiyu.octoberrain.core.common.WereadSection
import com.shuiyu.octoberrain.core.common.effectiveWereadBoardPoolMask
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.widget.WidgetHitRegion
import com.shuiyu.octoberrain.core.widget.WidgetTouchAction
import com.shuiyu.octoberrain.core.widget.hitRegionAt
import com.shuiyu.octoberrain.feature.widgets.kernel.KernelBridgeInstaller
import com.shuiyu.octoberrain.feature.widgets.kernel.WereadAppearanceCache
import java.io.File
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 桌面微信读书卡片：五种形态与右下角的形态切换、刷新按钮。
 *
 * 本书形态采用白底黑字的杂志版式：左上时间与日期、右上当前书目的划线笔记、
 * 中部最多五层叠放封面、封面下方书名、底部进度条与阅读信息，左右滑动切换书目。
 * 其余四个形态把整张卡片交给内核渲染，组件负责缩放画布、换算命中区域与分发动作。
 *
 * 书目沿用现有备选书籍列表与顺序，选中状态以书目编号关联；切换书目时笔记、进度、
 * 时长与打开按钮同步更新。绘制集中在 [WereadBoardPainter]，几何集中在 [WereadBoardFrame]，
 * 封面缓存、内核整卡绘制与隐藏菜单分别由独立类承担。
 */
@Suppress("TooManyFunctions")
class WereadBoardWidget(
    context: Context,
) : WereadCardWidget(context) {
    /** 当前形态，初值为本周，随桌面配置变化。 */
    private var form: WereadBoardForm = WereadBoardForm.WEEK

    /** 内核实例，首次加载或绘制时创建。 */
    private var kernel: WereadBoardKernel? = null

    /** 是否已完成一次数据加载。 */
    private var preloaded: Boolean = false

    /** 轮播状态，包含书目总数、选中位置与拖动偏移。 */
    private val carousel = WereadBoardCarousel()

    /** 绘制实现。 */
    private val painter = WereadBoardPainter()

    /** 当前书目列表，顺序与书架快照一致。 */
    private var books: List<WereadBookCard> = emptyList()

    /** 封面缓存与按需解码。 */
    private val covers =
        WereadBoardCoverLoader(context, { widthPx }) {
            if (!destroyed) {
                requestCovers()
                requestDraw()
            }
        }

    /** 内核整卡绘制的位图与画笔。 */
    private val surface = WereadBoardKernelSurface()

    /** 长按抬头的隐藏菜单与隐藏状态。 */
    private val overlay = WereadBoardOverlay { if (!destroyed) requestDraw() }

    /** 当前书目的划线正文，空串表示无笔记。 */
    private var noteText: String = ""

    /** 笔记请求序号，用于丢弃切换书目后返回的旧结果。 */
    private var noteToken: Int = 0

    /** 最近一帧的版式参数，供命中判定复用。 */
    private var frame: WereadBoardFrame? = null

    /** 最近一帧的底部按钮几何。 */
    private var chrome: WereadBoardChrome? = null

    /** 笔记读取的后台线程，单线程串行执行。 */
    private val noteLoader = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, NOTE_THREAD_NAME) }

    /** 主线程消息队列，用于动画与跨线程回投。 */
    private val handler = Handler(Looper.getMainLooper())

    /** 日历实例，复用以免每帧分配。 */
    private val clockCalendar: Calendar = Calendar.getInstance()

    /** 组件是否已销毁，销毁后到达的后台结果直接丢弃。 */
    private var destroyed: Boolean = false

    /** 落位动画的起始位置。 */
    private var settleFrom: Float = 0f

    /** 落位动画的目标位置。 */
    private var settleTarget: Int = 0

    /** 落位动画的起始时刻。 */
    private var settleStart: Long = 0L

    /** 刷新标记是否可见。 */
    private var refreshMarkVisible: Boolean = false

    /** 手势端口：手势对象通过它读取状态并回投动作。 */
    private val gestureHost =
        object : WereadBoardGestureHost() {
            override val form: WereadBoardForm
                get() = this@WereadBoardWidget.form

            override val carousel: WereadBoardCarousel
                get() = this@WereadBoardWidget.carousel

            override val frame: WereadBoardFrame?
                get() = this@WereadBoardWidget.frame

            override val scale: Float
                get() = boardScaleOf(widthPx)

            override val chrome: WereadBoardChrome?
                get() = this@WereadBoardWidget.chrome

            override fun requestDraw() = this@WereadBoardWidget.requestDraw()

            override fun settle(target: Int) = startSettle(target)

            override fun switchForm() = switchToNextForm()

            override fun refresh(): Boolean = refreshBoard()

            override fun open() = openCurrent()

            override fun dispatch(action: String): Boolean = this@WereadBoardWidget.dispatch(action)

            override val menuVisible: Boolean
                get() = overlay.visible

            override fun menuClick(
                x: Float,
                y: Float,
            ) = overlay.click(overlay.rows(widthPx, heightPx, scale), x, y)

            override fun hitRegion(
                x: Float,
                y: Float,
            ): WidgetHitRegion? = hitRegionAt(x, y)

            override fun showMenu(): Boolean = showHideMenu()

            override fun kernelScroll(step: Int): Boolean = kernel?.scrollBy(step) == true
        }

    /** 触摸判定。 */
    private val gestures = WereadBoardGestures(gestureHost, tokens.dpF(SLOP_DP))

    /** 点击分发。 */
    private val taps = WereadBoardTaps(gestureHost)

    /** 内核宿主适配，转发上下文与重绘请求。 */
    private val kernelHost =
        object : WereadBoardKernelHost {
            override val context: Context
                get() = this@WereadBoardWidget.context

            override fun requestDraw() {
                handler.post { this@WereadBoardWidget.requestDraw() }
            }
        }

    /** 内核数据源适配，复用应用内容服务。 */
    private val kernelDataSource =
        object : WereadBoardDataSource {
            override val content = AppServices.content.takeIf { AppServices.hasContent() }
        }

    /** 落位动画的单帧任务。 */
    private val settleRunnable =
        object : Runnable {
            override fun run() {
                val elapsed = (SystemClock.uptimeMillis() - settleStart).toFloat()
                val progress = (elapsed / SETTLE_DURATION_MS).coerceIn(0f, 1f)
                val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
                carousel.setPosition(settleFrom + (settleTarget - settleFrom) * eased)
                if (progress >= 1f) {
                    finishSettle()
                    return
                }
                requestDraw()
                handler.postDelayed(this, FRAME_INTERVAL_MS)
            }
        }

    /** 刷新标记清除任务。 */
    private val refreshMarkTask: Runnable =
        Runnable {
            refreshMarkVisible = false
            requestDraw()
        }

    /** 是否已启动整分计时。 */
    private var ticking: Boolean = false

    /** 整分重绘任务：只刷新画面，不重新读取书目与笔记。 */
    private val tick =
        object : Runnable {
            override fun run() {
                requestDraw()
                handler.postDelayed(this, millisToNextMinute())
            }
        }

    init {
        KernelBridgeInstaller.install()
    }

    /**
     * 尺寸变化后启动整分计时，并按新宽度重新准备封面。
     *
     * @param width 组件宽度，单位像素
     * @param height 组件高度，单位像素
     */
    override fun onWidgetSizeChanged(
        width: Int,
        height: Int,
    ) {
        super.onWidgetSizeChanged(width, height)
        startTicker()
        requestCovers()
    }

    /**
     * 配置变化后恢复形态并启动整分计时。
     *
     * @param config 最新配置快照
     */
    override fun onWidgetConfig(config: LauncherConfig) {
        val restored = WereadBoardForm.of(config.desktop.negativePage.forms[WEREAD_BOARD_WIDGET_ID])
        if (restored != form) {
            form = restored
            kernel?.setForm(form.id)
            if (form == WereadBoardForm.BOOK && books.isEmpty()) reload()
            requestCovers()
            requestDraw()
        }
        startTicker()
    }

    /** 启动整分计时，已在运行时直接返回。 */
    private fun startTicker() {
        if (ticking) return
        ticking = true
        handler.postDelayed(tick, millisToNextMinute())
    }

    /** 距离下一分钟开始还有多少毫秒。 */
    private fun millisToNextMinute(): Long = MINUTE_MS - System.currentTimeMillis() % MINUTE_MS

    /**
     * 读取书目、当前书目的划线，并让内核预取原盘数据。
     *
     * 后台线程只读取数据，结果统一投递到主线程写入状态，避免与绘制竞争；
     * 书目为空时同样返回成功，由绘制阶段给出空态文案。
     *
     * @param force 是否忽略既有缓存重新读取
     * @return 是否读取成功
     */
    override suspend fun loadData(force: Boolean): Boolean {
        val active = ensureKernel() ?: return false
        if (!preloaded || force) active.preload()
        val cards = active.bookCards()
        val note = cards.getOrNull(BOARD_FIRST_BOOK_INDEX)?.let { markTextOf(active.latestMark(it.bookId)) }.orEmpty()
        noteToken++
        preloaded = true
        handler.post {
            if (destroyed) return@post
            books = cards
            carousel.reset(cards.size, BOARD_FIRST_BOOK_INDEX)
            noteText = note
            requestCovers()
            requestDraw()
        }
        return true
    }

    /**
     * 绘制整个组件：本书形态自绘，其余形态交给内核整卡渲染。
     *
     * @param canvas 目标画布
     * @param width 组件宽度，单位像素
     * @param height 组件高度，单位像素
     */
    override fun drawWidget(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        if (overlay.isHidden()) return
        if (form == WereadBoardForm.BOOK) {
            drawBookForm(canvas, width, height)
        } else {
            drawKernelForm(canvas, width, height)
        }
    }

    /**
     * 绘制本书形态：杂志版式与层叠封面轮播。
     *
     * @param canvas 目标画布
     * @param width 组件宽度，单位像素
     * @param height 组件高度，单位像素
     */
    private fun drawBookForm(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        val layout =
            WereadBoardFrame.of(
                width.toFloat(),
                height.toFloat(),
                padX,
                WereadBoardFrame.spanFor(books.size),
                if (noteText.isBlank()) 0 else WereadBoardFrame.AUTO_NOTE_LINES,
            )
        frame = layout
        when {
            resolveState(books.isNotEmpty()) != WereadWidgetState.READY -> {
                chrome = null
                drawUnavailableIfNeeded(canvas, width, height)
            }

            books.isEmpty() -> {
                chrome = null
                drawEmptyState(canvas, contentArea(width, height), BOARD_EMPTY_TEXT)
            }

            else -> {
                val scene =
                    WereadBoardScene(
                        books = books,
                        slots = carousel.slots(),
                        selectedIndex = carousel.selectedIndex,
                        note = noteText,
                        clock = clockText(),
                        date = dateText(),
                        cover = { bookId -> covers.bitmap(bookId) },
                    )
                val current = painter.draw(canvas, scene, layout)
                chrome = current
                drawRefreshMark(canvas, layout, current)
            }
        }
    }

    /**
     * 绘制内核整卡形态，并在右下角叠加动作按钮与隐藏菜单。
     *
     * 内核渲染失败时按内核对纸色的要求先铺底色，再绘制不可用占位。
     *
     * @param canvas 目标画布
     * @param width 组件宽度，单位像素
     * @param height 组件高度，单位像素
     */
    private fun drawKernelForm(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        val active = ensureKernel()
        val scale = boardScaleOf(width)
        val paperArgb = appearance().paperArgb
        val rendered =
            active != null &&
                surface.draw(canvas, active, form.id, width, height, scale, paperArgb)
        if (!rendered) {
            if (paperArgb != WereadBoardKernelSurface.PAPER_WHITE) canvas.drawColor(paperArgb)
            drawPlaceholder(canvas, width, height)
        } else if (overlay.visible) {
            overlay.draw(canvas, overlay.rows(width, height, scale), scale)
        }
        // 内核形态自带左上角的切换与刷新热区，右下角不再绘制按钮。
        frame = WereadBoardFrame.of(width.toFloat(), height.toFloat(), padX)
        chrome = null
    }

    /**
     * 绘制刷新标记，位于右下角按钮左侧，避免与按钮重叠。
     *
     * @param canvas 目标画布
     * @param layout 版式参数
     * @param current 底部按钮几何
     */
    private fun drawRefreshMark(
        canvas: Canvas,
        layout: WereadBoardFrame,
        current: WereadBoardChrome,
    ) {
        if (!refreshMarkVisible) return
        val markPaint = textPaint(REFRESH_MARK_SIZE, InkColors.INK_3)
        val top = layout.infoBottom - layout.infoSize * REFRESH_MARK_TOP_RATIO
        val right = current.layoutButton.left - layout.infoSize * REFRESH_MARK_GAP_RATIO
        InkDraw.textRight(canvas, REFRESH_MARK_LABEL, right, top, markPaint)
    }

    /**
     * 声明组件内的命中区域：右下角两个按钮、打开入口、封面带或内核命中区。
     *
     * 声明之后宿主只把落在区域内的抬起转发给点击回调，组件外区域仍由桌面处理。
     *
     * @return 命中区域列表，未绘制时为空
     */
    override fun hitRegions(): List<WidgetHitRegion> {
        val scale = boardScaleOf(widthPx)
        val current = chrome
        return when {
            overlay.visible -> overlay.rows(widthPx, heightPx, scale).map { it.toRegion() }
            form == WereadBoardForm.BOOK -> current?.let(::boardRegions).orEmpty()
            else -> kernelRegions(scale)
        }
    }

    /**
     * 组装底部按钮、打开入口与形态命中区域。
     *
     * @param current 底部按钮几何
     * @param scale 画布缩放系数
     * @return 命中区域列表
     */
    private fun boardRegions(current: WereadBoardChrome): List<WidgetHitRegion> =
        buildList {
            regionOf(current.layoutButton, ACTION_LAYOUT)?.let { add(it) }
            regionOf(current.refreshButton, ACTION_REFRESH_BUTTON)?.let { add(it) }
            current.openButton?.let { button -> regionOf(button, ACTION_OPEN)?.let { add(it) } }
            frame?.coverArea?.let { area -> regionOf(area, ACTION_COVER)?.let { add(it) } }
        }

    /**
     * 把绘制的矩形换算为整型命中区域，越界部分裁剪到组件范围内。
     *
     * @param bounds 绘制区域
     * @param action 动作标识
     * @return 命中区域；宽或高为零时为空
     */
    private fun regionOf(
        bounds: BoardBounds,
        action: String,
    ): WidgetHitRegion? {
        val left = floor(bounds.left).toInt().coerceIn(0, widthPx)
        val top = floor(bounds.top).toInt().coerceIn(0, heightPx)
        val right = ceil(bounds.right).toInt().coerceIn(left, widthPx)
        val bottom = ceil(bounds.bottom).toInt().coerceIn(top, heightPx)
        if (right <= left || bottom <= top) return null
        return WidgetHitRegion(left, top, right - left, bottom - top, action)
    }

    /**
     * 构造内核命中区域并换算为组件内像素，本周与本月形态补正文区。
     *
     * @param scale 画布缩放系数
     * @return 命中区域列表
     */
    private fun kernelRegions(scale: Float): List<WidgetHitRegion> {
        if (scale <= 0f) return emptyList()
        val regions = mutableListOf<WidgetHitRegion>()
        kernel?.zones().orEmpty().forEach { zone ->
            regions +=
                WidgetHitRegion(
                    (zone.x * scale).roundToInt(),
                    (zone.y * scale).roundToInt(),
                    (zone.width * scale).roundToInt().coerceAtLeast(1),
                    (zone.height * scale).roundToInt().coerceAtLeast(1),
                    zone.action,
                )
        }
        if (form == WereadBoardForm.WEEK || form == WereadBoardForm.MONTH) {
            val headerBottom = (BOARD_HEADER_H * scale).roundToInt()
            regions +=
                WidgetHitRegion(
                    0,
                    headerBottom,
                    widthPx.coerceAtLeast(1),
                    (heightPx - headerBottom).coerceAtLeast(1),
                    ACTION_CARD_OPEN,
                )
        }
        return regions
    }

    /**
     * 处理组件内触摸动作。
     *
     * 本书形态只在起点位于封面带内且横向位移明显时消费移动；内核形态把纵向拖动
     * 转交内核滚动，横向移动不消费，交由桌面翻页。
     *
     * @param action 触摸动作
     * @param x 组件局部横坐标
     * @param y 组件局部纵坐标
     * @return 是否消费
     */
    override fun onTouch(
        action: WidgetTouchAction,
        x: Float,
        y: Float,
    ): Boolean =
        when (action) {
            WidgetTouchAction.DOWN -> gestures.down(x, y)
            WidgetTouchAction.MOVE -> gestures.move(x, y)
            WidgetTouchAction.UP -> gestures.up()
            WidgetTouchAction.CANCEL -> gestures.cancel()
        }

    /**
     * 处理点击，按当前形态分发。
     *
     * @param x 组件局部横坐标
     * @param y 组件局部纵坐标
     * @return 是否消费
     */
    override fun onClick(
        x: Float,
        y: Float,
    ): Boolean = taps.click(x, y)

    /**
     * 长按：内核形态下抬头命中时打开隐藏菜单，其余位置交回默认处理。
     *
     * @param x 组件内横坐标
     * @param y 组件内纵坐标
     * @return 是否已处理
     */
    override fun handleLongPress(
        x: Float,
        y: Float,
    ): Boolean = gestures.longPress(x, y)

    /** 组件销毁时释放线程、位图与内核。 */
    override fun onWidgetDestroy() {
        destroyed = true
        ticking = false
        handler.removeCallbacksAndMessages(null)
        noteLoader.shutdownNow()
        overlay.release()
        covers.release()
        surface.release()
        kernel?.release()
        kernel = null
        preloaded = false
    }

    /** 切到形态池中的下一个形态。 */
    private fun switchToNextForm() {
        switchForm(nextForm())
    }

    /**
     * 切换形态并持久化，切到本书形态时按需补充数据。
     *
     * @param next 目标形态
     */
    private fun switchForm(next: WereadBoardForm) {
        form = next
        kernel?.setForm(next.id)
        persistForm(next)
        if (form == WereadBoardForm.BOOK && books.isEmpty()) reload()
        requestCovers()
        requestDraw()
    }

    /**
     * 读取形态池：位掩码决定可用形态，按枚举顺序排列。
     *
     * @return 可用形态列表，至少包含本周
     */
    private fun poolForms(): List<WereadBoardForm> = WereadBoardForm.pool(config.desktop.effectiveWereadBoardPoolMask)

    /**
     * 取形态池中的下一形态，当前形态不在池内时从池首开始。
     *
     * @return 目标形态
     */
    private fun nextForm(): WereadBoardForm = form.nextIn(poolForms())

    /**
     * 把形态写入桌面负一屏配置，并请求外观层重绘。
     *
     * @param value 目标形态
     */
    private fun persistForm(value: WereadBoardForm) {
        if (!AppServices.isReady()) return
        val ordinal = value.ordinal
        AppServices.config.edit { current ->
            val page = current.desktop.negativePage
            val forms = page.forms + (WEREAD_BOARD_WIDGET_ID to ordinal)
            current.copy(desktop = current.desktop.copy(negativePage = page.copy(forms = forms)))
        }
        AppServices.refresh.requestPartial()
    }

    /**
     * 按动作分发内核命中区域的交互。
     *
     * @param action 动作标识
     * @return 是否已处理
     */
    private fun dispatch(action: String): Boolean =
        when {
            action == ACTION_FORM_NEXT -> {
                switchToNextForm()
                true
            }

            action == ACTION_REFRESH -> refreshBoard()
            action == ACTION_BOOK_OPEN -> {
                openCurrent()
                true
            }

            action == ACTION_NOTE_PREV -> moveNote(previous = true)
            action == ACTION_NOTE_NEXT -> moveNote(previous = false)
            action == ACTION_NOTE_EXPAND -> toggleNoteExpanded()
            action == ACTION_NOTE_FILTER -> navigate(Destination.Note)
            action.startsWith(ACTION_TODO_PREFIX) -> toggleTodo(action.removePrefix(ACTION_TODO_PREFIX))
            action == ACTION_CARD_OPEN -> openBody()
            else -> false
        }

    /**
     * 刷新数据：重建内核状态，重新读取本地缓存，并按当前书目请求远端更新。
     *
     * 远端回调只在结束后触发一次本地重读，不额外轮询；刷新期间在右下角显示标记。
     *
     * @return 是否已发起刷新
     */
    private fun refreshBoard(): Boolean {
        val active = ensureKernel() ?: return false
        active.refreshData()
        refreshMarkVisible = true
        handler.removeCallbacks(refreshMarkTask)
        handler.postDelayed(refreshMarkTask, REFRESH_MARK_MS)
        reload(force = true)
        requestDraw()
        AppServices.wereadSync?.refresh(WereadRefreshRequest(REFRESH_MODE_BOOK, bookId = kernelBookId())) { _, _ ->
            handler.post { onRemoteRefresh(active) }
        }
        return true
    }

    /**
     * 远端刷新结束后重建内核并重读本地缓存。
     *
     * @param active 已绑定的内核实例
     */
    private fun onRemoteRefresh(active: WereadBoardKernel) {
        if (destroyed) return
        active.refreshData()
        reload(force = true)
    }

    /**
     * 刷新请求使用的书目编号：本书形态取中央书目，其余形态取内核当前书目。
     *
     * @return 书目编号，未知时为空串
     */
    private fun kernelBookId(): String =
        if (form == WereadBoardForm.BOOK) {
            currentBook()?.bookId.orEmpty()
        } else {
            kernel?.currentBookId().orEmpty()
        }

    /**
     * 启动落位动画，结束时提交选中位置。
     *
     * @param target 目标位置
     */
    private fun startSettle(target: Int) {
        if (carousel.count == 0) {
            requestDraw()
            return
        }
        settleTarget = target.coerceIn(0, carousel.count - 1)
        settleFrom = carousel.position
        if (abs(settleFrom - settleTarget) < SETTLE_EPS) {
            finishSettle()
            return
        }
        settleStart = SystemClock.uptimeMillis()
        handler.removeCallbacks(settleRunnable)
        handler.post(settleRunnable)
    }

    /** 结束落位动画，提交选中位置并按变化同步数据。 */
    private fun finishSettle() {
        handler.removeCallbacks(settleRunnable)
        if (carousel.commit(settleTarget)) {
            applySelection()
        } else {
            requestDraw()
        }
    }

    /** 选中位置变化后刷新笔记、封面与内核持久化状态。 */
    private fun applySelection() {
        noteText = ""
        noteToken++
        requestDraw()
        val book = currentBook() ?: return
        kernel?.selectBook(book.bookId)
        requestCovers()
        requestNote(book)
    }

    /**
     * 异步读取指定书目的划线，返回时校验序号，旧请求不覆盖新书目。
     *
     * @param book 书目
     */
    private fun requestNote(book: WereadBookCard) {
        val token = ++noteToken
        val active = kernel ?: return
        noteLoader.execute {
            val text = markTextOf(active.latestMark(book.bookId))
            handler.post {
                if (destroyed || token != noteToken) return@post
                noteText = text
                requestDraw()
            }
        }
    }

    /** 为当前可见的封面层准备位图，不可见书目不解码。 */
    private fun requestCovers() {
        covers.requestVisible(books, carousel.slots())
    }

    /** 打开当前中央书目；书目缺失时回落到阅读页。 */
    private fun openCurrent() {
        val bookId = currentBook()?.bookId ?: kernel?.currentBookId()
        if (bookId.isNullOrEmpty()) {
            navigateTo(Destination.Book)
            return
        }
        val opened =
            runCatching {
                val intent =
                    Intent(Intent.ACTION_VIEW, Uri.parse(WEREAD_READING_SCHEME + bookId))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.isSuccess
        if (!opened) navigateTo(Destination.Book)
    }

    /** 当前选中的书目。 */
    private fun currentBook(): WereadBookCard? = books.getOrNull(carousel.selectedIndex)

    /**
     * 打开隐藏菜单。
     *
     * @return 固定返回 true
     */
    private fun showHideMenu(): Boolean {
        overlay.show()
        return true
    }

    /**
     * 上一条或换一条本记。
     *
     * @param previous 为真时前移，为假时后移
     * @return 是否发生切换
     */
    private fun moveNote(previous: Boolean): Boolean {
        val active = kernel ?: return false
        val moved = if (previous) active.previousNote() else active.nextNote()
        if (moved) requestDraw()
        return moved
    }

    /**
     * 翻转本记正文展开状态。
     *
     * @return 是否完成翻转
     */
    private fun toggleNoteExpanded(): Boolean {
        val active = kernel ?: return false
        val toggled = active.toggleNoteExpanded()
        if (toggled) requestDraw()
        return toggled
    }

    /**
     * 勾选或取消待办条目。
     *
     * @param rawIndex 待办条目序号
     * @return 是否完成切换
     */
    private fun toggleTodo(rawIndex: String): Boolean {
        val index = rawIndex.toIntOrNull()
        val id = index?.let { kernel?.todoIds()?.getOrNull(it) }?.toLongOrNull()
        val host = AppServices.wereadTodos
        if (id == null || host == null) return false
        host.toggle(id)
        kernel?.refreshData()
        requestDraw()
        return true
    }

    /**
     * 打开本周与本月形态的正文页面。
     *
     * @return 是否完成跳转
     */
    private fun openBody(): Boolean =
        when (form) {
            WereadBoardForm.WEEK -> navigate(Destination.WereadTab(WereadSection.STATS))
            WereadBoardForm.MONTH -> navigate(Destination.WereadCalendar)
            else -> false
        }

    /**
     * 跳转目标页，未装配路由时返回失败。
     *
     * @param destination 目标页
     * @return 是否完成跳转
     */
    private fun navigate(destination: Destination): Boolean {
        if (!AppServices.hasRouter()) return false
        navigateTo(destination)
        return true
    }

    /**
     * 绘制统一占位：内核缺失或渲染异常时提示组件不可用。
     *
     * @param canvas 画布
     * @param width 组件宽度
     * @param height 组件高度
     */
    private fun drawPlaceholder(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        drawEmptyState(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()), PLACEHOLDER_UNAVAILABLE)
    }

    /**
     * 读取组件外观偏好。
     *
     * @return 外观偏好快照
     */
    private fun appearance(): WereadAppearance = WereadAppearanceCache.read(File(context.filesDir, WEREAD_ROOT_DIR))

    /**
     * 取内核实例，首次调用时创建并绑定。
     *
     * @return 内核实例，工厂未注册时为空
     */
    private fun ensureKernel(): WereadBoardKernel? {
        val current = kernel ?: createKernel()
        kernel = current
        return current
    }

    /**
     * 创建并绑定内核，工厂缺失或绑定失败时返回空。
     *
     * @return 已绑定的内核
     */
    private fun createKernel(): WereadBoardKernel? {
        val factory = WereadBoardKernelBridge.factory
        val created = factory?.let { runCatching { it(kernelHost, kernelDataSource) }.getOrNull() }
        val bound = created != null && runCatching { created.bind(kernelHost, kernelDataSource) }.isSuccess
        if (!bound) created?.release()
        return created?.takeIf { bound }?.also { it.setForm(form.id) }
    }

    /** 生成当前时间文案。 */
    private fun clockText(): String {
        clockCalendar.timeInMillis = System.currentTimeMillis()
        return clockTextOf(clockCalendar.get(Calendar.HOUR_OF_DAY), clockCalendar.get(Calendar.MINUTE))
    }

    /** 生成当前日期文案。 */
    private fun dateText(): String {
        clockCalendar.timeInMillis = System.currentTimeMillis()
        return dateTextOf(
            clockCalendar.get(Calendar.MONTH) + 1,
            clockCalendar.get(Calendar.DAY_OF_MONTH),
            clockCalendar.get(Calendar.DAY_OF_WEEK),
        )
    }

    /** 本组件的固定常量。 */
    companion object {
        /** 远端刷新使用的模式，按书目拉取。 */
        const val REFRESH_MODE_BOOK: String = "book"

        /** 笔记读取线程名称。 */
        const val NOTE_THREAD_NAME: String = "weread-board-note"
    }
}
