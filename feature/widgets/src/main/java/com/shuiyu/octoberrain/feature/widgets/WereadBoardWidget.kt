package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.inkread.weekread.kernel.pick.BookPicker
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.WEREAD_BOARD_WIDGET_ID
import com.shuiyu.octoberrain.core.common.WereadAppearance
import com.shuiyu.octoberrain.core.common.WereadSection
import com.shuiyu.octoberrain.core.common.effectiveWereadBoardPoolMask
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.widget.WidgetHitRegion
import com.shuiyu.octoberrain.core.widget.WidgetTouchAction
import com.shuiyu.octoberrain.core.widget.hitRegionAt
import com.shuiyu.octoberrain.feature.widgets.kernel.KernelBridgeInstaller
import com.shuiyu.octoberrain.feature.widgets.kernel.WereadAppearanceCache
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 整体卡片形态：与微读内核的五个形态一一对应，标识与原版 card_period 配置值一致。
 *
 * @param id 形态标识，写入桌面负一屏配置
 * @param label 形态名称，用于组件选择器与设置页展示
 */
internal enum class WereadBoardForm(
    val id: String,
    val label: String,
) {
    WEEK("week", "本周"),
    MONTH("month", "本月"),
    BOOK("book", "本书"),
    NOTE("note", "本记"),
    TODO("todo", "待办"),
    ;

    /**
     * 取下一形态，待办之后回到本周。
     *
     * @return 下一形态
     */
    fun next(): WereadBoardForm = entries[(ordinal + 1) % entries.size]

    companion object {
        /**
         * 按序号取形态，越界与空值回到本周。
         *
         * @param ordinal 形态序号，取值来自桌面负一屏配置
         * @return 对应形态
         */
        fun of(ordinal: Int?): WereadBoardForm = entries.getOrElse(ordinal ?: 0) { WEEK }
    }
}

/**
 * 整体卡片组件：把微读墨记卡片一比一显示在桌面，绘制、命中与排版由内核完成。
 *
 * 组件承担五件事：按槽位宽度缩放画布、把内核回写的命中区域换算为组件内像素、按动作标识
 * 分发交互、维护形态配置与形态池、绘制内核之外的浮层（隐藏菜单、选书菜单、刷新标记）。
 *
 * 触点在按下、移动、抬起三个阶段接收；本记与月形态的纵向拖动在移动阶段转交内核滚动，
 * 其余形态的移动不消费，交由桌面翻页。
 *
 * @param context 组件上下文
 */
@Suppress("TooManyFunctions") // 视图生命周期、命中分发、浮层绘制与内核管理同属组件职责，拆分后共享状态成本更高。
class WereadBoardWidget(
    context: Context,
) : WereadCardWidget(context) {
    /** 当前形态，初值为本周。 */
    private var form: WereadBoardForm = WereadBoardForm.WEEK

    /** 内核实例，绑定后复用。 */
    private var kernel: WereadBoardKernel? = null

    /** 是否已完成一次数据预取。 */
    private var preloaded: Boolean = false

    /** 隐藏菜单是否可见。 */
    private var menuVisible: Boolean = false

    /** 选书菜单是否可见。 */
    private var pickerVisible: Boolean = false

    /** 选书弹层，绘制、命中与滚动由内核提供。 */
    private val bookPicker: BookPicker = BookPicker()

    /** 隐藏截止时刻，取值自单调时钟；为零表示当前不隐藏。 */
    private var hiddenUntil: Long = 0L

    /** 刷新标记是否可见。 */
    private var refreshMarkVisible: Boolean = false

    /** 触摸序列是否已按下且尚未抬起。 */
    private var touchActive: Boolean = false

    /** 是否已越过拖动阈值进入滚动状态。 */
    private var touchScrolling: Boolean = false

    /** 按下点的组件内纵坐标。 */
    private var touchDownY: Float = 0f

    /** 最近一次移动点的组件内纵坐标。 */
    private var touchLastY: Float = 0f

    /** 主线程消息队列，用于定时关闭菜单与恢复显示。 */
    private val handler: Handler = Handler(Looper.getMainLooper())

    /** 纸色绘制时复用的同尺寸位图，尺寸变化时重建。 */
    private var paperBitmap: Bitmap? = null

    /** 纸色叠加画笔，以正片叠底把内核白底映射为纸色。 */
    private val paperPaint: Paint =
        Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }

    /** 隐藏菜单超时关闭任务。 */
    private val menuTimeout: Runnable = Runnable { dismissOverlays() }

    /** 隐藏到期恢复任务。 */
    private val revealTask: Runnable =
        Runnable {
            hiddenUntil = 0L
            requestDraw()
        }

    /** 刷新标记清除任务。 */
    private val refreshMarkTask: Runnable =
        Runnable {
            refreshMarkVisible = false
            requestDraw()
        }

    /** 内核宿主实现：文件目录与重绘请求转交组件。 */
    private val kernelHost: WereadBoardKernelHost =
        object : WereadBoardKernelHost {
            override val context: Context get() = this@WereadBoardWidget.context

            override fun requestDraw() {
                handler.post { this@WereadBoardWidget.requestDraw() }
            }
        }

    /** 内核数据源实现：内容服务未装配时返回空。 */
    private val kernelDataSource: WereadBoardDataSource =
        object : WereadBoardDataSource {
            override val content get() = AppServices.content.takeIf { AppServices.hasContent() }
        }

    init {
        KernelBridgeInstaller.install()
    }

    /**
     * 从桌面配置恢复形态。
     *
     * @param config 当前桌面配置
     */
    override fun onWidgetConfig(config: LauncherConfig) {
        form = WereadBoardForm.of(config.desktop.negativePage.forms[WEREAD_BOARD_WIDGET_ID])
    }

    /**
     * 预取数据：首次绘制前让内核读取一次原盘数据。
     *
     * @param force 是否强制重新读取
     * @return 是否完成预取
     */
    override suspend fun loadData(force: Boolean): Boolean {
        val active = ensureKernel() ?: return false
        if (force || !preloaded) {
            active.preload()
            preloaded = true
        }
        return true
    }

    /**
     * 绘制卡片：内核绘制正文，组件叠加浮层。
     *
     * @param canvas 画布
     * @param width 组件宽度
     * @param height 组件高度
     */
    override fun drawWidget(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        if (SystemClock.elapsedRealtime() < hiddenUntil) return
        val active = kernel
        val scale = scaleOf(width)
        val paperArgb = appearance().paperArgb
        val rendered =
            active != null && scale > 0f && renderKernel(active, canvas, width, height, scale, paperArgb)
        if (!rendered) {
            if (paperArgb != PAPER_WHITE) canvas.drawColor(paperArgb)
            drawPlaceholder(canvas, width, height)
        }
    }

    /**
     * 读取组件外观偏好。
     *
     * 每次绘制读取原盘偏好文件，使内置页面改动设置后下一次刷新即生效；
     * 尚未导入数据时取默认外观。
     *
     * @return 外观偏好快照
     */
    private fun appearance(): WereadAppearance = WereadAppearanceCache.read(File(context.filesDir, WEREAD_ROOT_DIR))

    /**
     * 按缩放绘制内核内容，成功时叠加浮层。
     *
     * 纸色为素白时直接绘制到组件画布；其余档位先绘制到同尺寸位图，再以正片叠底色
     * 叠加到纸色底上，使内核绘制的白底呈现纸色而不影响墨色文字。
     *
     * @param active 已绑定内核
     * @param canvas 画布
     * @param width 组件宽度
     * @param height 组件高度
     * @param scale 画布缩放系数
     * @param paperArgb 纸色
     * @return 是否绘制成功
     */
    private fun renderKernel(
        active: WereadBoardKernel,
        canvas: Canvas,
        width: Int,
        height: Int,
        scale: Float,
        paperArgb: Int,
    ): Boolean {
        val bitmap = if (paperArgb == PAPER_WHITE) null else obtainPaperBitmap(width, height)
        if (bitmap != null) bitmap.eraseColor(PAPER_WHITE)
        val rendered = renderInto(bitmap?.let(::Canvas) ?: canvas, active, width, height, scale)
        if (rendered && bitmap != null) {
            canvas.drawColor(paperArgb)
            canvas.drawBitmap(bitmap, 0f, 0f, paperPaint)
        }
        return rendered
    }

    /**
     * 取得与组件同尺寸的复用位图。
     *
     * @param width 组件宽度
     * @param height 组件高度
     * @return 位图，尺寸非法时为空
     */
    private fun obtainPaperBitmap(
        width: Int,
        height: Int,
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val current = paperBitmap
        return if (current != null && current.width == width && current.height == height) {
            current
        } else {
            current?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { paperBitmap = it }
        }
    }

    /**
     * 在给定画布上按缩放绘制内核与浮层。
     *
     * @param canvas 目标画布
     * @param active 已绑定内核
     * @param width 组件宽度
     * @param height 组件高度
     * @param scale 画布缩放系数
     * @return 是否绘制成功
     */
    private fun renderInto(
        canvas: Canvas,
        active: WereadBoardKernel,
        width: Int,
        height: Int,
        scale: Float,
    ): Boolean {
        val logicalWidth = (width / scale).roundToInt().coerceAtLeast(1)
        val logicalHeight = (height / scale).roundToInt().coerceAtLeast(1)
        canvas.save()
        canvas.scale(scale, scale)
        val rendered = runCatching { active.render(canvas, logicalWidth, logicalHeight, form.id) }.isSuccess
        if (rendered) {
            drawOverlays(canvas, width, height, scale)
            if (pickerVisible) drawBookPicker(canvas, logicalWidth, logicalHeight, 1f)
        }
        canvas.restore()
        return rendered
    }

    /**
     * 返回当前可点区域，浮层打开时只返回浮层行。
     *
     * @return 组件内像素坐标系下的命中区域
     */
    override fun hitRegions(): List<WidgetHitRegion> {
        val scale = scaleOf(widthPx)
        return when {
            menuVisible -> hideMenuLayout(scale).map { it.toRegion() }
            pickerVisible -> {
                val width = widthPx.coerceAtLeast(1)
                val height = heightPx.coerceAtLeast(1)
                listOf(WidgetHitRegion(0, 0, width, height, ACTION_OVERLAY_PANEL))
            }
            else -> kernelRegions(scale)
        }
    }

    /**
     * 处理点击，按命中区域动作分发。
     *
     * @param x 组件内横坐标
     * @param y 组件内纵坐标
     * @return 是否消费点击
     */
    override fun onClick(
        x: Float,
        y: Float,
    ): Boolean =
        when {
            SystemClock.elapsedRealtime() < hiddenUntil -> false
            menuVisible -> handleOverlayClick(hideMenuLayout(scaleOf(widthPx)), x, y)
            pickerVisible -> handlePickerClick(x, y)
            else -> hitRegionAt(x, y)?.action?.let(::dispatch) ?: false
        }

    /**
     * 处理触摸：按下记录起点，移动转交滚动，抬起复位。
     *
     * @param action 触摸阶段
     * @param x 组件内横坐标
     * @param y 组件内纵坐标
     * @return 是否消费本次触摸
     */
    override fun onTouch(
        action: WidgetTouchAction,
        x: Float,
        y: Float,
    ): Boolean =
        when (action) {
            WidgetTouchAction.DOWN -> handleDown(y)
            WidgetTouchAction.MOVE -> handleMove(y)
            WidgetTouchAction.UP, WidgetTouchAction.CANCEL -> handleEnd()
        }

    /**
     * 长按：抬头命中时打开隐藏菜单，其余位置交回默认处理。
     *
     * @param x 组件内横坐标
     * @param y 组件内纵坐标
     * @return 是否已处理
     */
    override fun handleLongPress(
        x: Float,
        y: Float,
    ): Boolean {
        val scale = scaleOf(widthPx)
        val title = titleZone(scale)
        if (title != null && title.contains(x, y)) {
            showHideMenu()
            return true
        }
        return false
    }

    /** 释放内核与定时任务。 */
    override fun onWidgetDestroy() {
        handler.removeCallbacksAndMessages(null)
        kernel?.release()
        kernel = null
        preloaded = false
        paperBitmap?.recycle()
        paperBitmap = null
    }

    /**
     * 按下：记录起点并消费。
     *
     * @param y 组件内纵坐标
     * @return 是否消费
     */
    private fun handleDown(y: Float): Boolean {
        touchActive = true
        touchScrolling = false
        touchDownY = y
        touchLastY = y
        return true
    }

    /**
     * 移动：越过阈值后按纵向位移滚动，未进入滚动时不消费。
     *
     * @param y 组件内纵坐标
     * @return 是否消费
     */
    private fun handleMove(y: Float): Boolean {
        if (pickerVisible) return handlePickerScroll(y)
        val scale = scaleOf(widthPx)
        val threshold = tokens.dpF(SCROLL_START_DP)
        val active = touchActive && scale > 0f && (touchScrolling || abs(y - touchDownY) >= threshold)
        val delta =
            if (active) {
                touchScrolling = true
                val step = ((y - touchLastY) / scale).roundToInt()
                touchLastY = y
                step
            } else {
                0
            }
        return active && delta != 0 && kernel?.scrollBy(delta) == true
    }

    /**
     * 抬起或取消：复位状态并返回是否滚动过。
     *
     * @return 滚动过时返回 true，供宿主抑制点击
     */
    private fun handleEnd(): Boolean {
        val scrolled = touchScrolling
        touchActive = false
        touchScrolling = false
        touchLastY = 0f
        touchDownY = 0f
        return scrolled
    }

    /**
     * 打开隐藏菜单，八秒无操作自动关闭。
     */
    private fun showHideMenu() {
        pickerVisible = false
        menuVisible = true
        handler.removeCallbacks(menuTimeout)
        handler.postDelayed(menuTimeout, OVERLAY_TIMEOUT_MS)
        requestDraw()
    }

    /**
     * 打开选书弹层：候选书目绑定到内核弹层，首行固定为自动档。
     *
     * 弹层的标题、关闭文案、遮罩提示、行高与分隔线均由内核按原版样式绘制，
     * 组件只负责提供候选与把交互结果写回内核。
     */
    private fun showBookPicker() {
        val options = kernel?.bookOptions().orEmpty()
        val items =
            buildList {
                add(BookPicker.Item(PICKER_AUTO_ID, PICKER_AUTO_LABEL, PICKER_AUTO_META))
                options.forEach { option -> add(BookPicker.Item(option.bookId, option.title, "")) }
            }
        bookPicker.setTitle(PICKER_TITLE)
        bookPicker.setCloseLabel(PICKER_CLOSE_LABEL)
        bookPicker.setScrimLabel(PICKER_SCRIM_LABEL)
        bookPicker.bind(items, kernel?.selectedBookId().orEmpty())
        bookPicker.resetScroll()
        menuVisible = false
        pickerVisible = true
        handler.removeCallbacks(menuTimeout)
        handler.postDelayed(menuTimeout, OVERLAY_TIMEOUT_MS)
        requestDraw()
    }

    /** 关闭全部浮层。 */
    private fun dismissOverlays() {
        menuVisible = false
        pickerVisible = false
        handler.removeCallbacks(menuTimeout)
        requestDraw()
    }

    /**
     * 处理隐藏菜单点击：命中行执行隐藏动作，空白处关闭菜单。
     *
     * @param layout 浮层行与动作
     * @param x 组件内横坐标
     * @param y 组件内纵坐标
     * @return 是否消费点击
     */
    private fun handleOverlayClick(
        layout: List<OverlayRow>,
        x: Float,
        y: Float,
    ): Boolean {
        val row = layout.firstOrNull { it.rect.contains(x, y) }
        if (row == null) {
            dismissOverlays()
        } else {
            hideFor(row.action.removePrefix(ACTION_HIDE_PREFIX).toIntOrNull() ?: 0)
        }
        return true
    }

    /**
     * 按动作分发交互。
     *
     * @param action 动作标识
     * @return 是否已处理
     */
    private fun dispatch(action: String): Boolean =
        when {
            action == ACTION_FORM_NEXT -> applyNextForm()
            action == ACTION_REFRESH -> refreshData()
            action == ACTION_BOOK_OPEN -> openBook()
            action == ACTION_BOOK_PICK -> {
                showBookPicker()
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
     * 切到形态池中的下一形态。
     *
     * @return 是否完成切换
     */
    private fun applyNextForm(): Boolean {
        switchForm(nextForm())
        return true
    }

    /**
     * 切换形态并持久化。
     *
     * @param next 目标形态
     */
    private fun switchForm(next: WereadBoardForm) {
        form = next
        kernel?.setForm(next.id)
        persistForm(next)
        requestDraw()
    }

    /**
     * 读取形态池：位掩码决定可用形态，按枚举顺序排列。
     *
     * @return 可用形态列表，至少包含本周
     */
    private fun poolForms(): List<WereadBoardForm> {
        val mask = config.desktop.effectiveWereadBoardPoolMask
        val forms = WereadBoardForm.entries.filter { (mask and (1 shl it.ordinal)) != 0 }
        return forms.ifEmpty { listOf(WereadBoardForm.WEEK) }
    }

    /**
     * 取形态池中的下一形态，当前形态不在池内时从池首开始。
     *
     * @return 目标形态
     */
    private fun nextForm(): WereadBoardForm {
        val pool = poolForms()
        val index = pool.indexOf(form)
        return pool[(index + 1) % pool.size]
    }

    /**
     * 把形态写入桌面负一屏配置。
     *
     * @param value 目标形态
     */
    private fun persistForm(value: WereadBoardForm) {
        if (!AppServices.isReady()) return
        AppServices.config.edit { config ->
            val negative = config.desktop.negativePage
            val forms = negative.forms + (WEREAD_BOARD_WIDGET_ID to value.ordinal)
            config.copy(desktop = config.desktop.copy(negativePage = negative.copy(forms = forms)))
        }
    }

    /**
     * 刷新数据：清空缓存、重新绑定内核并短暂显示刷新标记。
     *
     * @return 是否完成刷新
     */
    private fun refreshData(): Boolean {
        val active = ensureKernel() ?: return false
        active.refreshData()
        refreshMarkVisible = true
        handler.removeCallbacks(refreshMarkTask)
        handler.postDelayed(refreshMarkTask, REFRESH_MARK_MS)
        reload(force = true)
        requestDraw()
        return true
    }

    /**
     * 打开当前在读书目的微信读书阅读页，未取得书目时打开本书形态页面。
     *
     * @return 是否完成跳转
     */
    private fun openBook(): Boolean {
        val bookId = kernel?.currentBookId()
        if (bookId.isNullOrEmpty()) return navigate(Destination.Book)
        val opened =
            runCatching {
                val uri = Uri.parse("$WEREAD_READING_SCHEME$bookId")
                val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.isSuccess
        return opened || navigate(Destination.Book)
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
        return id != null &&
            host != null &&
            run {
                host.toggle(id)
                kernel?.refreshData()
                requestDraw()
                true
            }
    }

    /**
     * 打开周与月形态的正文页面。
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
     * 隐藏卡片指定时长，到时恢复。
     *
     * @param seconds 隐藏秒数，零表示取消
     */
    private fun hideFor(seconds: Int) {
        dismissOverlays()
        if (seconds <= 0) return
        hiddenUntil = SystemClock.elapsedRealtime() + seconds * MILLIS_PER_SECOND
        handler.removeCallbacks(revealTask)
        handler.postDelayed(revealTask, seconds * MILLIS_PER_SECOND)
        requestDraw()
    }

    /**
     * 处理选书弹层点击：关闭区关闭弹层，空处忽略，行点击切换书目。
     *
     * @param x 组件内横坐标
     * @param y 组件内纵坐标
     * @return 是否消费点击
     */
    private fun handlePickerClick(
        x: Float,
        y: Float,
    ): Boolean {
        val scale = scaleOf(widthPx).takeIf { it > 0f } ?: return false
        val logicalWidth = (widthPx / scale).roundToInt().coerceAtLeast(1)
        val logicalHeight = (heightPx / scale).roundToInt().coerceAtLeast(1)
        return when (val hit = bookPicker.hitTest(x / scale, y / scale, logicalWidth, logicalHeight, 1f)) {
            BookPicker.HIT_DISMISS -> {
                dismissOverlays()
                true
            }

            BookPicker.HIT_NONE -> true
            else -> {
                applyPickedBook(bookPicker.bookIdAt(hit).orEmpty())
                true
            }
        }
    }

    /**
     * 处理选书弹层拖动：位移换算为逻辑像素后交内核滚动。
     *
     * @param y 组件内纵坐标
     * @return 是否消费本次移动
     */
    private fun handlePickerScroll(y: Float): Boolean {
        val scale = scaleOf(widthPx).takeIf { it > 0f } ?: return true
        val logicalWidth = (widthPx / scale).roundToInt().coerceAtLeast(1)
        val logicalHeight = (heightPx / scale).roundToInt().coerceAtLeast(1)
        val delta = y - touchDownY
        touchActive = true
        touchScrolling = true
        bookPicker.scrollBy(delta / scale, logicalWidth, logicalHeight, 1f)
        touchDownY = y
        requestDraw()
        return true
    }

    /**
     * 构造内核命中区域并换算为组件内像素，周与月形态补正文区。
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
            val headerBottom = (HEADER_H * scale).roundToInt()
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
     * 隐藏菜单版面：四行选项右对齐。
     *
     * @param scale 画布缩放系数
     * @return 行矩形与动作
     */
    private fun hideMenuLayout(scale: Float): List<OverlayRow> {
        val rows = HIDE_OPTIONS.map { (seconds, label) -> label to "$ACTION_HIDE_PREFIX$seconds" }
        return overlayRows(rows, scale)
    }

    /**
     * 把选书结果写回内核并关闭弹层。
     *
     * @param bookId 书目编号，空串表示自动档
     */
    private fun applyPickedBook(bookId: String) {
        kernel?.selectBook(bookId.takeIf { it.isNotEmpty() })
        preloaded = true
        dismissOverlays()
        requestDraw()
    }

    /**
     * 计算浮层行矩形，浮层贴右上角排列。
     *
     * @param rows 行文案与动作
     * @param scale 画布缩放系数
     * @return 行矩形与动作
     */
    private fun overlayRows(
        rows: List<Pair<String, String>>,
        scale: Float,
    ): List<OverlayRow> {
        if (scale <= 0f || rows.isEmpty()) return emptyList()
        val logicalWidth = widthPx / scale
        val width = min(OVERLAY_WIDTH, logicalWidth - OVERLAY_MARGIN * 2f).coerceAtLeast(OVERLAY_MIN_WIDTH)
        val left = logicalWidth - width - OVERLAY_MARGIN
        val top = OVERLAY_MARGIN
        return rows.mapIndexed { index, row ->
            val y = top + index * OVERLAY_ROW_HEIGHT
            OverlayRow(
                RectF(
                    left * scale,
                    y * scale,
                    (left + width) * scale,
                    (y + OVERLAY_ROW_HEIGHT) * scale,
                ),
                row.second,
                row.first,
            )
        }
    }

    /**
     * 绘制浮层：隐藏菜单、选书菜单与刷新标记。
     *
     * @param canvas 已按逻辑坐标缩放的画布
     * @param width 组件宽度
     * @param height 组件高度
     * @param scale 画布缩放系数
     */
    private fun drawOverlays(
        canvas: Canvas,
        width: Int,
        height: Int,
        scale: Float,
    ) {
        if (menuVisible) {
            drawOverlayList(canvas, hideMenuLayout(scale))
        }
        if (refreshMarkVisible) {
            val textPaint = textPaint(REFRESH_MARK_SIZE, InkColors.INK)
            val right = width / scale - OVERLAY_MARGIN
            val top = height / scale - REFRESH_MARK_SIZE - OVERLAY_MARGIN
            InkDraw.textRight(canvas, REFRESH_MARK_LABEL, right, top, textPaint)
        }
    }

    /**
     * 绘制选书弹层：在组件像素坐标系内由内核按原版弹层样式绘制。
     *
     * @param canvas 未缩放的组件画布
     * @param width 组件宽度
     * @param height 组件高度
     * @param scale 画布缩放系数
     */
    private fun drawBookPicker(
        canvas: Canvas,
        width: Int,
        height: Int,
        scale: Float,
    ) {
        if (scale <= 0f) return
        bookPicker.render(canvas, width, height, scale)
    }

    /**
     * 绘制隐藏菜单：白底、墨框与行分隔线，行文字按行高中线落字。
     *
     * @param canvas 已按逻辑坐标缩放的画布
     * @param rows 行矩形与文案
     */
    private fun drawOverlayList(
        canvas: Canvas,
        rows: List<OverlayRow>,
    ) {
        if (rows.isEmpty()) return
        val fill = fillPaint(InkColors.PAPER)
        val stroke = strokePaint(InkColors.INK)
        val text = textPaint(OVERLAY_TEXT_SIZE, InkColors.INK)
        val outer =
            RectF(
                rows.first().rect.left,
                rows.first().rect.top,
                rows.last().rect.right,
                rows.last().rect.bottom,
            )
        canvas.drawRect(outer, fill)
        canvas.drawRect(outer, stroke)
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                val dividerY = row.rect.top
                canvas.drawLine(outer.left, dividerY, outer.right, dividerY, stroke)
            }
            val rowLeft = row.rect.left
            val rowTop = row.rect.top
            val title = InkDraw.ellipsize(row.title, text, row.rect.width() - OVERLAY_TEXT_PADDING * 2f)
            val textTop = rowTop + (row.rect.height() - text.textSize) / 2f
            InkDraw.text(canvas, title, rowLeft + OVERLAY_TEXT_PADDING, textTop, text)
        }
    }

    /**
     * 取抬头形态切换区域，供长按判定。
     *
     * @param scale 画布缩放系数
     * @return 矩形，缩放无效时为空
     */
    private fun titleZone(scale: Float): RectF? {
        if (scale <= 0f) return null
        return RectF(0f, 0f, HEADER_TITLE_W * scale, HEADER_H * scale)
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
     * 创建并绑定内核，失败时返回空，下次绘制重试。
     *
     * @return 已绑定的内核
     */
    private fun ensureKernel(): WereadBoardKernel? {
        val existing = kernel
        val active = existing ?: createKernel()
        kernel = active
        return active
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

    /**
     * 浮层行：组件内像素矩形、动作与文案。
     *
     * @param rect 行矩形
     * @param action 行动作
     * @param title 行文案
     */
    private data class OverlayRow(
        val rect: RectF,
        val action: String,
        val title: String,
    ) {
        /**
         * 把浮层行换算为命中区域。
         *
         * @return 组件内像素坐标系的命中区域
         */
        fun toRegion(): WidgetHitRegion =
            WidgetHitRegion(
                rect.left.roundToInt(),
                rect.top.roundToInt(),
                rect.width().roundToInt().coerceAtLeast(1),
                rect.height().roundToInt().coerceAtLeast(1),
                action,
            )
    }

    /** 组件常量。 */
    private companion object {
        /** 逻辑卡片宽度基准，与内核一致。 */
        const val BASE_WIDTH: Float = 368f

        /** 画布缩放下限。 */
        const val MIN_SCALE: Float = 0.6f

        /** 画布缩放上限。 */
        const val MAX_SCALE: Float = 2.0f

        /** 抬头高度，单位为内核逻辑像素。 */
        const val HEADER_H: Float = 34f

        /** 抬头形态切换热区宽度。 */
        const val HEADER_TITLE_W: Float = 88f

        /** 进入滚动所需的最小纵向位移，单位 dp。 */
        const val SCROLL_START_DP: Float = 12f

        /** 浮层超时关闭时间，单位毫秒。 */
        const val OVERLAY_TIMEOUT_MS: Long = 8000L

        /** 刷新标记显示时长，单位毫秒。 */
        const val REFRESH_MARK_MS: Long = 1200L

        /** 秒与毫秒的换算。 */
        const val MILLIS_PER_SECOND: Long = 1000L

        /** 浮层宽度上限，单位逻辑像素。 */
        const val OVERLAY_WIDTH: Float = 220f

        /** 浮层宽度下限。 */
        const val OVERLAY_MIN_WIDTH: Float = 140f

        /** 浮层与卡片边缘的间距。 */
        const val OVERLAY_MARGIN: Float = 8f

        /** 隐藏菜单行高。 */
        const val OVERLAY_ROW_HEIGHT: Float = 32f

        /** 隐藏菜单文字尺寸。 */
        const val OVERLAY_TEXT_SIZE: Float = 14f

        /** 隐藏菜单文字左内边距。 */
        const val OVERLAY_TEXT_PADDING: Float = 10f

        /** 选书弹层标题。 */
        const val PICKER_TITLE: String = "选一本书"

        /** 选书弹层关闭文案。 */
        const val PICKER_CLOSE_LABEL: String = "关闭"

        /** 选书弹层遮罩提示。 */
        const val PICKER_SCRIM_LABEL: String = "点空白处关闭"

        /** 选书弹层自动档编号，空串表示不指定书目。 */
        const val PICKER_AUTO_ID: String = ""

        /** 选书弹层自动档副文案。 */
        const val PICKER_AUTO_META: String = "最近在读"

        /** 刷新标记文字尺寸。 */
        const val REFRESH_MARK_SIZE: Float = 12f

        /** 刷新标记文案。 */
        const val REFRESH_MARK_LABEL: String = "刷新中"

        /** 不可用占位文案。 */
        const val PLACEHOLDER_UNAVAILABLE: String = "微读组件不可用"

        /** 选书菜单首行文案。 */
        const val PICKER_AUTO_LABEL: String = "自动（最近在读）"

        /** 微信读书阅读页地址前缀。 */
        const val WEREAD_READING_SCHEME: String = "weread://reading?bId="

        /** 动作：切换形态。 */
        const val ACTION_FORM_NEXT: String = "form:next"

        /** 动作：刷新数据。 */
        const val ACTION_REFRESH: String = "data:refresh"

        /** 动作：打开书目。 */
        const val ACTION_BOOK_OPEN: String = "book:open"

        /** 动作：弹出选书菜单。 */
        const val ACTION_BOOK_PICK: String = "book:pick"

        /** 动作：上一条本记。 */
        const val ACTION_NOTE_PREV: String = "note:prev"

        /** 动作：换一条本记。 */
        const val ACTION_NOTE_NEXT: String = "note:next"

        /** 动作：展开或收起本记正文。 */
        const val ACTION_NOTE_EXPAND: String = "note:expand"

        /** 动作：筛选本记。 */
        const val ACTION_NOTE_FILTER: String = "note:filter"

        /** 动作前缀：待办勾选。 */
        const val ACTION_TODO_PREFIX: String = "todo:"

        /** 动作：周与月正文。 */
        const val ACTION_CARD_OPEN: String = "card:open"

        /** 素白纸色，直接绘制内核内容，不做叠加。 */
        const val PAPER_WHITE: Int = 0xFFFFFFFF.toInt()

        /** 微读原盘数据目录名，位于应用私有 filesDir 之下。 */
        const val WEREAD_ROOT_DIR: String = "weread"

        /** 动作前缀：隐藏。 */
        const val ACTION_HIDE_PREFIX: String = "hide:"

        /** 动作：浮层整块面板，用于宿主点击分流。 */
        const val ACTION_OVERLAY_PANEL: String = "overlay:panel"

        /** 隐藏菜单选项：秒数与文案。 */
        val HIDE_OPTIONS: List<Pair<Int, String>> =
            listOf(
                15 to "隐藏 15 秒",
                60 to "隐藏 1 分钟",
                300 to "隐藏 5 分钟",
                0 to "取消",
            )

        /**
         * 按槽位宽度计算画布缩放系数，与内核基准宽度 368 对齐。
         *
         * @param width 组件宽度
         * @return 缩放系数，宽度无效时为零
         */
        fun scaleOf(width: Int): Float {
            if (width <= 0) return 0f
            return (width / BASE_WIDTH).coerceIn(MIN_SCALE, MAX_SCALE)
        }
    }
}
