package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.inkread.weekread.kernel.desk.DeskActions
import com.inkread.weekread.kernel.desk.DeskData
import com.inkread.weekread.kernel.desk.DeskPage
import com.inkread.weekread.kernel.desk.DeskRenderer
import com.inkread.weekread.kernel.desk.DeskStyle
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.NegativeHost
import com.shuiyu.octoberrain.core.common.WereadRefreshRequest
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.wereadsync.WereadAssetRefreshState
import com.shuiyu.octoberrain.core.wereadsync.WereadDeskEditsStore
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil

/**
 * 墨台分区视图。每个分区用一个串行工作队列拥有内核页面，UI 只展示已完成的位图与状态快照。
 * 构造、命中、编辑、排版与绘制不接触主线程；重载和销毁以代次隔离旧任务结果。
 */
@Suppress("TooManyFunctions")
internal class WereadDeskSectionView(
    context: Context,
    screenHost: NegativeHost,
    moduleId: String,
    onSettings: () -> Unit = {},
    private val onDataEdited: () -> Unit = {},
) : LinearLayout(context),
    DeskTouchDispatcher.Host {
    private val deskHost =
        WereadDeskHost(
            context,
            screenHost,
            moduleId,
            onWallpaper = { generateWallpaper() },
            onFingerprint = { openFingerprintPicker() },
            onSettings = onSettings,
        )
    private val moduleId = moduleId
    private val renderer = DeskRenderer(deskHost.host)
    private val editBridge = WereadDeskEditBridge(WereadDeskEditsStore(File(context.filesDir, "weread")))
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var generation = 0

    @Volatile private var released = false
    private var worker = WorkerState()

    @Volatile private var requestedWidth = 0
    private var measuredWidth = 0
    private var sectionHeight = MIN_SECTION_HEIGHT_DP * deskHost.host.scale()
    private var frame: SectionFrame? = null
    private var tipText: String? = null
    private val tipPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val canvasView = SectionCanvas(context)
    private val dispatcher = DeskTouchDispatcher(context, canvasView, this)
    private val guideRow = LinearLayout(context)
    private val fingerprintEmptyHeader =
        if (moduleId == MODULE_FP) DeskSectionStyle.buildHeader(context, "阅读指纹") else null
    private val emptyButton: TextView
    private var assetControls: AssetSectionControls? = null

    @Volatile private var assetSort = 0
    private val emptyMessage: TextView
    private val tipClearRunnable =
        Runnable {
            tipText = null
            canvasView.invalidate()
        }

    init {
        orientation = VERTICAL
        if (moduleId == MODULE_RANK) {
            assetControls =
                AssetSectionControls(
                    context,
                    refresh = { refreshEmptySection() },
                    reloadAll = { confirmAssetReload() },
                    sort = { selected -> selectAssetSort(selected) },
                    wallpaper = { generateWallpaper() },
                )
            addView(assetControls)
        }
        emptyButton =
            InkWidgets.button(context, if (moduleId == MODULE_FP) "选择指纹书目" else "同步微信读书", filled = false) {
                refreshEmptySection()
            }
        emptyMessage = InkWidgets.caption(context, "暂无可展示数据，请先完成微信读书同步")
        guideRow.orientation = VERTICAL
        guideRow.visibility = GONE
        val inset = DeskSectionStyle.paddingXPx(context.resources.displayMetrics.widthPixels)
        guideRow.setPadding(inset, 0, inset, 0)
        if (moduleId != MODULE_RANK) guideRow.addView(emptyButton)
        guideRow.addView(emptyMessage)
        emptyMessage.setOnClickListener { AppServices.router.navigate(Destination.WereadSettings) }
        guideRow.addView(
            InkWidgets.button(context, "微信读书连接设置", filled = false) {
                AppServices.router.navigate(Destination.WereadSettings)
            },
        )
        fingerprintEmptyHeader?.let { header ->
            header.visibility = GONE
            addView(header)
        }
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(guideRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (released) {
            released = false
            worker = WorkerState()
            requestedWidth = 0
            requestLayout()
        }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    /** 旧任务允许结束但不再发布；页面释放与新页面构造仍在同一个队列内顺序执行。 */
    fun reload() {
        if (released) return
        updateEmptyGuide()
        generation++
        dispatcher.cancel()
        frame = null
        val state = worker
        val token = generation
        synchronized(state) {
            state.scrollDelta = 0f
            state.scrollQueued = false
        }
        state.executor.execute {
            state.page?.release()
            state.page = null
            state.frameQueued = false
            if (valid(state, token)) requestFrame(state, token)
        }
        canvasView.invalidate()
        requestLayout()
    }

    /** 不在 UI 等待内核；清理排在旧工作后，线程随后退出。 */
    fun release() {
        if (released) return
        released = true
        generation++
        dispatcher.cancel()
        mainHandler.removeCallbacks(tipClearRunnable)
        frame = null
        val state = worker
        state.executor.execute {
            state.page?.release()
            state.page = null
            state.spare?.recycle()
            state.spare = null
        }
        state.executor.shutdown()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width =
            MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 }
                ?: (FALLBACK_WIDTH_DP * resources.displayMetrics.density).toInt()
        measuredWidth = width
        if (width != requestedWidth && !released) {
            requestedWidth = width
            submit { _, state, token -> requestFrame(state, token) }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun valid(
        state: WorkerState,
        token: Int,
    ): Boolean = !released && state === worker && token == generation

    /** 所有 DeskPage 访问只允许通过此队列，提交时不读任何内核字段。 */
    private fun submit(action: (DeskPage, WorkerState, Int) -> Unit) {
        if (released) return
        val state = worker
        val token = generation
        state.executor.execute {
            if (!valid(state, token)) return@execute
            runCatching {
                val page =
                    pageFor(state)
                if (valid(state, token)) action(page, state, token)
            }.onFailure {
                mainHandler.post {
                    if (valid(state, token)) {
                        emptyMessage.text = "墨台加载失败，请重试同步"
                        guideRow.visibility = VISIBLE
                    }
                }
            }
        }
    }

    private fun pageFor(state: WorkerState): DeskPage =
        state.page ?: DeskPage(deskHost.host).also { page ->
            page.bind()
            if (moduleId == MODULE_RANK) {
                page.onAction(DeskActions.ASSET_SORT_BASE + assetSort)
                state.assetNotice = WereadAssetRefreshState.notice(File(context.filesDir, "weread"))
            }
            state.page = page
        }

    /** 多个输入共享一次待渲染任务；内核页面只在所属队列内读写。 */
    private fun requestFrame(
        state: WorkerState,
        token: Int,
    ) {
        if (state.frameQueued || !valid(state, token)) return
        state.frameQueued = true
        runCatching {
            state.executor.execute {
                state.frameQueued = false
                if (!valid(state, token)) return@execute
                val page = runCatching { pageFor(state) }.getOrNull()
                if (page == null) return@execute
                val rendered = runCatching { renderFrame(page, requestedWidth, state) }.getOrNull()
                mainHandler.post {
                    if (!valid(state, token) || rendered?.width != requestedWidth) {
                        rendered?.bitmap?.recycle()
                    } else if (rendered != null) {
                        val previous = frame?.bitmap
                        applyFrame(rendered)
                        runCatching {
                            state.executor.execute {
                                if (valid(state, token)) {
                                    state.spare?.takeUnless { it === previous }?.recycle()
                                    state.spare = previous
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure { state.frameQueued = false }
    }

    /** 核算尺寸、命中几何与位图都在同一个工作任务中完成。 */
    private fun renderFrame(
        page: DeskPage,
        width: Int,
        state: WorkerState,
    ): SectionFrame? {
        if (width <= 0) return null
        val unit = deskHost.host.scale()
        val header = contentOffset(unit)
        val total = page.height(width).toFloat()
        val blank = (moduleId != MODULE_BILL && isEmptyData(page.data())) || total <= header
        // 指纹交给外层滚动；墨单使用宿主位移，资产使用内核滚动。
        val cap =
            if (moduleId == MODULE_FP) {
                MAX_CACHE_HEIGHT_PX.toFloat()
            } else {
                MAX_SECTION_HEIGHT_DP * resources.displayMetrics.density
            }
        val height = if (blank) MIN_SECTION_HEIGHT_DP * unit else (total - header).coerceIn(1f, cap)
        state.billScrollMax = if (moduleId == MODULE_BILL) (total - header - height).coerceAtLeast(0f) else 0f
        state.billScroll = state.billScroll.coerceIn(0f, state.billScrollMax)
        val pixels = ceil(height).toInt().coerceIn(1, MAX_CACHE_HEIGHT_PX)
        val spare = state.spare?.takeUnless { it.isRecycled }
        state.spare = null
        val bitmap =
            if (blank) {
                state.spare = spare
                null
            } else if (spare != null && spare.width == width && spare.height == pixels) {
                spare.apply { eraseColor(android.graphics.Color.WHITE) }
            } else {
                spare?.recycle()
                Bitmap.createBitmap(width, pixels, Bitmap.Config.ARGB_8888)
            }
        return runCatching {
            if (bitmap != null) {
                val canvas = Canvas(bitmap)
                canvas.translate(0f, -header)
                page.render(canvas, width, ceil(height + header).toInt().coerceAtLeast(1), state.billScroll.toInt())
            }
            SectionFrame(
                bitmap,
                width,
                height,
                header,
                blank,
                page.padOpen(),
                page.canScroll() || state.billScrollMax > 0f,
                state.assetNotice,
            )
        }.onFailure { bitmap?.recycle() }.getOrThrow()
    }

    private fun applyFrame(next: SectionFrame) {
        frame = next
        assetControls?.notice?.apply {
            text = next.assetNotice.orEmpty()
            visibility = if (next.assetNotice == null) GONE else VISIBLE
        }
        sectionHeight = next.height
        canvasView.visibility = if (next.empty) GONE else VISIBLE
        fingerprintEmptyHeader?.visibility = if (next.empty) VISIBLE else GONE
        guideRow.visibility = if (next.empty) VISIBLE else GONE
        if (next.empty) updateEmptyGuide()
        canvasView.requestLayout()
        requestLayout()
        canvasView.invalidate()
    }

    private fun isEmptyData(data: DeskData): Boolean =
        when (moduleId) {
            MODULE_BILL -> data.bill()?.items?.isEmpty() != false
            MODULE_RANK -> data.assets().isEmpty()
            MODULE_FP -> data.fpBooks().isEmpty()
            else -> false
        }

    override fun padIsOpen(): Boolean = frame?.padOpen == true

    override fun sectionCanScroll(): Boolean = frame?.canScroll == true

    override fun tapAt(
        x: Float,
        y: Float,
        keypad: Boolean,
    ) {
        submit { page, state, token ->
            val offset = contentOffset(deskHost.host.scale())
            if (keypad || page.padOpen()) {
                val key = page.hitPad(x, y + offset)
                if (key >= 0) handlePricePress(page, key, state, token)
            } else {
                val action = page.hitTest(x, y + offset)
                if (action != DeskPage.ACTION_NONE) page.onAction(action)
            }
            requestFrame(state, token)
        }
    }

    private fun handlePricePress(
        page: DeskPage,
        key: Int,
        state: WorkerState,
        token: Int,
    ) {
        when (editBridge.press(page, key)) {
            WereadDeskEditBridge.Result.FAILED ->
                mainHandler.post {
                    if (valid(state, token)) {
                        AppServices.router.toast("价格保存失败，请重试")
                        reload()
                    }
                }
            WereadDeskEditBridge.Result.SAVED -> mainHandler.post { if (valid(state, token)) onDataEdited() }
            WereadDeskEditBridge.Result.APPLIED -> Unit
        }
    }

    override fun longPressAt(
        x: Float,
        y: Float,
    ) {
        submit { page, state, token ->
            val action = page.longActionAt(x, y + contentOffset(deskHost.host.scale()))
            if (action == DeskActions.LONG_ASSET_TITLE) {
                val saved = editBridge.saveHidden(page)
                val title = page.pendingTitle()
                mainHandler.post {
                    if (valid(state, token)) {
                        if (saved) {
                            showTip(
                                if (title.isNullOrBlank()) {
                                    TIP_TITLE_REMOVED
                                } else {
                                    TIP_TITLE_REMOVED_PREFIX + title +
                                        TIP_TITLE_REMOVED_SUFFIX
                                },
                            )
                            onDataEdited()
                        } else {
                            AppServices.router.toast("移出书目保存失败，请重试")
                            reload()
                        }
                    }
                }
            }
            requestFrame(state, token)
        }
    }

    /** 合并拖动位移，避免快速滑动把工作队列塞满。 */
    override fun scrollBy(delta: Float) {
        if (released) return
        val state = worker
        synchronized(state) {
            state.scrollDelta += delta
            if (state.scrollQueued) return
            state.scrollQueued = true
        }
        submit { page, queuedState, token ->
            val amount =
                synchronized(queuedState) {
                    queuedState.scrollDelta.also {
                        queuedState.scrollDelta = 0f
                        queuedState.scrollQueued = false
                    }
                }
            if (moduleId == MODULE_BILL) {
                queuedState.billScroll = (queuedState.billScroll + amount).coerceIn(0f, queuedState.billScrollMax)
            } else {
                page.scrollBy(amount)
            }
            requestFrame(queuedState, token)
        }
    }

    private fun updateEmptyGuide() {
        val hasKey =
            AppServices.config.state.value.integrations.wereadApiKey
                .isNotBlank()
        val sync = AppServices.wereadSync
        emptyButton.text =
            when {
                !hasKey -> "填写微信读书 API Key"
                sync?.isRunning() == true -> "正在同步…"
                moduleId == MODULE_FP -> "选择指纹书目"
                moduleId == MODULE_RANK -> "刷新阅读资产"
                else -> "同步微信读书"
            }
        emptyButton.isEnabled = sync?.isRunning() != true || !hasKey
        emptyMessage.text = if (!hasKey) "尚未配置微信读书，点击上方按钮填写" else "暂无可展示数据 · ${sync?.statusText() ?: "同步服务未就绪"}"
    }

    private fun refreshEmptySection(forceAll: Boolean = false) {
        if (AppServices.config.state.value.integrations.wereadApiKey
                .isBlank()
        ) {
            AppServices.router.navigate(Destination.WereadSettings)
            return
        }
        if (moduleId == MODULE_FP) {
            openFingerprintPicker()
            return
        }
        val sync = AppServices.wereadSync
        if (sync == null) {
            AppServices.router.toast("请先在设置中配置微信读书")
        } else {
            val onFinished: (Boolean, String) -> Unit = { _, message ->
                mainHandler.post {
                    if (!released) {
                        AppServices.router.toast(message)
                        emptyMessage.text = message
                        reload()
                    }
                }
            }
            val accepted =
                if (moduleId == MODULE_RANK) {
                    sync.refresh(WereadRefreshRequest(if (forceAll) "assets_full" else "assets"), onFinished)
                } else {
                    sync.syncNow(onFinished)
                }
            if (accepted) {
                if (moduleId == MODULE_RANK) AppServices.router.toast("正在刷新阅读资产…")
                updateEmptyGuide()
            } else {
                emptyMessage.text = sync.statusText()
                AppServices.router.toast(sync.statusText())
            }
        }
    }

    private fun confirmAssetReload() {
        InkDialogs.confirm(
            context,
            "重新拉取阅读资产",
            "将对当前书架电子书和本地历史资产书目全部重新取数，跳过增量缓存，可能耗时较长。" +
                "手动录价、隐藏书目等设置保留；失败书目保留旧值并提示未完成。",
            confirmLabel = "重新拉取",
            onConfirm = { refreshEmptySection(forceAll = true) },
        )
    }

    private fun selectAssetSort(selected: Int) {
        if (selected == assetSort) return
        assetSort = selected
        submit { page, state, token ->
            page.onAction(DeskActions.ASSET_SORT_BASE + selected)
            requestFrame(state, token)
        }
    }

    /** 资产原绘制头由原位置的原生标题与下拉框替代，命中与绘制共享同一偏移。 */
    private fun contentOffset(unit: Float): Float =
        renderer.headerHeight(unit) +
            if (moduleId == MODULE_RANK) DeskStyle.secHeadH(unit) else 0f

    /** 壁纸同样使用页面所属队列，避免与帧渲染、释放并发。 */
    fun generateWallpaper() {
        submit { page, state, token ->
            val section = wallpaperSection()
            val width = if (section == DeskPage.SEC_FP) FINGERPRINT_WIDTH_PX else WALLPAPER_WIDTH_PX
            val height = if (section == DeskPage.SEC_FP) FINGERPRINT_HEIGHT_PX else WALLPAPER_HEIGHT_PX
            val bitmap = runCatching { page.renderWallpaper(section, width, height) }.getOrNull()
            mainHandler.post {
                if (!valid(state, token)) {
                    bitmap?.recycle()
                } else if (bitmap == null) {
                    AppServices.router.toast(WALLPAPER_FAILED)
                } else {
                    WereadWallpaperDialog.show(context, bitmap)
                }
            }
        }
    }

    fun openFingerprintPicker() {
        if (released) return
        val state = worker
        val token = generation
        state.executor.execute {
            val candidates = WereadFingerprintBooks.candidates(context)
            val selected = WereadFingerprintBooks.ids(context)
            mainHandler.post {
                if (valid(state, token)) {
                    FingerprintBookDialog.show(context, candidates, selected) { ids ->
                        WereadFingerprintBooks.save(ids)
                        reload()
                        deskHost.host.requestRedraw()
                    }
                }
            }
        }
    }

    private fun wallpaperSection(): Int =
        when (moduleId) {
            MODULE_BILL -> DeskPage.SEC_BILL
            MODULE_RANK -> DeskPage.SEC_ASSET
            MODULE_FP -> DeskPage.SEC_FP
            else -> DeskPage.SEC_BILL
        }

    private fun showTip(message: String) {
        tipText = message
        mainHandler.removeCallbacks(tipClearRunnable)
        mainHandler.postDelayed(tipClearRunnable, TIP_DURATION_MS)
        canvasView.invalidate()
    }

    /** UI 帧只画已完成位图，键盘和拖动时也不调用内核。 */
    private inner class SectionCanvas(
        context: Context,
    ) : View(context) {
        init {
            isClickable = true
            isFocusable = true
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1),
                ceil(sectionHeight).toInt().coerceAtLeast(1),
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            frame?.bitmap?.takeUnless { it.isRecycled }?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            drawTip(canvas)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean =
            dispatcher.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private class WorkerState {
        var billScroll = 0f
        var billScrollMax = 0f
        var assetNotice: String? = null
        val executor: ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, LOAD_THREAD_NAME).apply { isDaemon = true }
            }
        var page: DeskPage? = null
        var spare: Bitmap? = null
        var frameQueued = false
        var scrollQueued = false
        var scrollDelta = 0f
    }

    private data class SectionFrame(
        val bitmap: Bitmap?,
        val width: Int,
        val height: Float,
        val header: Float,
        val empty: Boolean,
        val padOpen: Boolean,
        val canScroll: Boolean,
        val assetNotice: String?,
    )

    /**
     * 在分区底部绘制长按提示条。
     *
     * 提示条覆盖正文与价格键盘，四秒后自动消失；文案单行显示，
     * 超出可用宽度时先缩小字号，缩到下限仍放不下时以省略号截断。
     *
     * @param canvas 视图画布
     */
    private fun drawTip(canvas: Canvas) {
        val text = tipText
        val width = measuredWidth
        if (text != null && width > 0 && sectionHeight > 0f) {
            val unit = deskHost.host.scale().takeIf { it > 0f } ?: DEFAULT_UNIT
            val padding = width * TIP_HORIZONTAL_PADDING_RATIO
            val boxHeight = unit * TIP_BOX_HEIGHT_UNITS
            val bottom = sectionHeight - unit * TIP_BOTTOM_OFFSET_UNITS
            val rect = RectF(padding, bottom - boxHeight, width - padding, bottom)
            tipPaint.style = Paint.Style.FILL
            tipPaint.color = TIP_BACKGROUND
            canvas.drawRect(rect, tipPaint)
            val inset = padding + unit * TIP_TEXT_INSET_UNITS
            val available = width - inset * 2f
            if (available > 0f) {
                drawTipText(canvas, text, rect, inset, available, unit)
            }
        }
    }

    /**
     * 绘制提示条文字。
     *
     * @param canvas 视图画布
     * @param text 提示文案
     * @param rect 提示条区域
     * @param inset 文字左边距
     * @param available 文字可用宽度
     * @param unit 当前排版单位
     */
    private fun drawTipText(
        canvas: Canvas,
        text: String,
        rect: RectF,
        inset: Float,
        available: Float,
        unit: Float,
    ) {
        tipPaint.color = TIP_TEXT
        tipPaint.textSize = unit * TIP_TEXT_UNITS
        var size = tipPaint.textSize
        while (size > unit * TIP_MIN_TEXT_UNITS && tipPaint.measureText(text) > available) {
            size -= unit * TIP_TEXT_SIZE_STEP
            tipPaint.textSize = size
        }
        val display =
            if (tipPaint.measureText(text) > available) {
                TextUtils.ellipsize(text, tipPaint, available, TextUtils.TruncateAt.END).toString()
            } else {
                text
            }
        val metrics = tipPaint.fontMetrics
        val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(display, inset, baseline, tipPaint)
    }

    private companion object {
        /** 宽度未知时的回退值，单位为 dp。 */
        const val FALLBACK_WIDTH_DP: Int = 320

        /** 分区最小高度，单位为 dp。 */
        const val MIN_SECTION_HEIGHT_DP: Float = 48f

        /** 分区最大可视高度，超出部分在分区内滚动，单位为 dp。 */
        const val MAX_SECTION_HEIGHT_DP: Float = 320f

        /** 原版指纹纸张尺寸，避免非等比拉伸指纹字形。 */
        const val FINGERPRINT_WIDTH_PX: Int = 480

        /** 原版指纹纸张高度。 */
        const val FINGERPRINT_HEIGHT_PX: Int = 800

        /** 壁纸位图宽度，单位为像素。 */
        const val WALLPAPER_WIDTH_PX: Int = 1080

        /** 壁纸位图高度，单位为像素。 */
        const val WALLPAPER_HEIGHT_PX: Int = 1920

        /** 壁纸生成失败时的提示文案。 */
        const val WALLPAPER_FAILED: String = "壁纸生成失败，请稍后重试"

        /** 排版单位缺失时的回退值，与微读墨记 480×800 面板的默认取值一致。 */
        const val DEFAULT_UNIT: Float = 1.2f

        /** 墨单分区标识。 */
        const val MODULE_BILL: String = "bill"

        /** 阅读资产分区标识。 */
        const val MODULE_RANK: String = "rank"

        /** 阅读指纹分区标识。 */
        const val MODULE_FP: String = "fp"

        /** 后台加载墨台页面使用的线程名。 */
        const val LOAD_THREAD_NAME: String = "weread-desk-section"

        /** 分区位图缓存的最高像素高度，超出时退化为占用前先渲染的路径以控制内存。 */
        const val MAX_CACHE_HEIGHT_PX: Int = 8192

        /** 长按提示条的停留时长，单位为毫秒。 */
        const val TIP_DURATION_MS: Long = 4_000L

        /** 提示条左右外边距占分区宽度的比例。 */
        const val TIP_HORIZONTAL_PADDING_RATIO: Float = 0.05f

        /** 提示条相对分区底部的上移量，单位为排版单位的倍数。 */
        const val TIP_BOTTOM_OFFSET_UNITS: Float = 74f

        /** 提示条高度，单位为排版单位的倍数。 */
        const val TIP_BOX_HEIGHT_UNITS: Float = 22f

        /** 提示条内文字左边距，单位为排版单位的倍数。 */
        const val TIP_TEXT_INSET_UNITS: Float = 4f

        /** 提示条初始字号，单位为排版单位的倍数。 */
        const val TIP_TEXT_UNITS: Float = 13f

        /** 提示条字号下限，单位为排版单位的倍数。 */
        const val TIP_MIN_TEXT_UNITS: Float = 9f

        /** 提示条字号每次收缩的步长，单位为排版单位的倍数。 */
        const val TIP_TEXT_SIZE_STEP: Float = 0.3f

        /** 提示条底色，与微读墨记的黑底提示一致。 */
        const val TIP_BACKGROUND: Int = 0xFF000000.toInt()

        /** 提示条文字颜色，与微读墨记的白字提示一致。 */
        const val TIP_TEXT: Int = 0xFFFFFFFF.toInt()

        /** 书目移出统计但缺少书名时的提示文案。 */
        const val TIP_TITLE_REMOVED: String = "已将该书移出统计"

        /** 书目移出统计提示文案的前缀。 */
        const val TIP_TITLE_REMOVED_PREFIX: String = "已将「"

        /** 书目移出统计提示文案的后缀。 */
        const val TIP_TITLE_REMOVED_SUFFIX: String = "」移出统计"
    }
}
