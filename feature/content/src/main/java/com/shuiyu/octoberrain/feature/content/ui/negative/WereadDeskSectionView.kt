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
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.NegativeHost
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlin.math.ceil

/**
 * 墨台单个分区的负一屏视图。
 *
 * 视图持有一份只包含当前分区的墨台页面，按负一屏宽度完成排版并把分区正文
 * 绘制到卡片内。绘制时跳过墨台顶栏，只呈现分区标题与正文，使负一屏卡片
 * 不出现重复的页面框架；分区内的点击、长按、内滚与价格键盘交由内核处理，
 * 页面跳转与重绘请求经 [WereadDeskHost] 转给负一屏宿主。
 *
 * 微读原盘数据尚未导入时，分区没有可绘制的条目，此时隐藏绘制区域并显示
 * 「导入微读墨记数据」引导按钮，供用户发起一次迁移；导入结束后重新排版，
 * 由内核按新的数据绘制分区。
 *
 * @param context 视图上下文
 * @param screenHost 负一屏宿主，用于请求重绘与页面跳转
 * @param moduleId 当前分区标识，取值为 bill、rank、fp
 * @param onSettings 内核请求打开墨台设置态的回调
 */
@Suppress("TooManyFunctions")
internal class WereadDeskSectionView(
    context: Context,
    screenHost: NegativeHost,
    moduleId: String,
    onSettings: () -> Unit = {},
) : LinearLayout(context),
    DeskTouchDispatcher.Host {
    private val deskHost: WereadDeskHost =
        WereadDeskHost(
            context = context,
            screenHost = screenHost,
            moduleId = moduleId,
            onWallpaper = { generateWallpaper() },
            onFingerprint = { openFingerprintPicker() },
            onSettings = onSettings,
        )

    private val moduleId: String = moduleId

    private val renderer: DeskRenderer = DeskRenderer(deskHost.host)

    private var page: DeskPage? = null

    private var headerHeight: Float = 0f

    private var sectionHeight: Float = 0f

    private var measuredWidth: Int = 0

    private var empty: Boolean = false

    /** 本次导入请求是否已由通道回调过结果，用于避免重复提示。 */
    private var importCallbackFired: Boolean = false

    /** 是否已有后台加载在途，避免重复排队。 */
    private var loading: Boolean = false

    /** 后台加载失败标记，用于显示引导区域而不是无限占位。 */
    private var loadFailed: Boolean = false

    /** 已核算排版结果对应的卡片宽度，为零表示需要重新核算。 */
    private var measuredSectionWidth: Int = 0

    /** 是否已有后台排版计算在途，避免测量阶段重复排队。 */
    private var measuring: Boolean = false

    /** 是否强制重新核算排版，键盘开合与长按移除书目后置位。 */
    private var forceMeasure: Boolean = false

    /** 长按提示文案，为空时不绘制提示条。 */
    private var tipText: String? = null

    private val tipHandler = Handler(Looper.getMainLooper())

    private val tipPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

    private val tipClearRunnable =
        Runnable {
            tipText = null
            canvasView.invalidate()
        }

    /** 分区绘制区域，宽度跟随卡片，高度由内核排版结果决定。 */
    private val canvasView: SectionCanvas = SectionCanvas(context)

    /** 触摸分派器，负责点按、长按、内滚与键盘按键。 */
    private val dispatcher: DeskTouchDispatcher = DeskTouchDispatcher(context, canvasView, this)

    /** 数据为空时显示的引导区域，含导入按钮与结果文案。 */
    private val guideRow: LinearLayout = LinearLayout(context)

    private val importButton: TextView

    private val importMessage: TextView

    init {
        orientation = VERTICAL
        importButton = InkWidgets.button(context, IMPORT_LABEL, filled = false) { requestImport() }
        importMessage = InkWidgets.caption(context, "")
        importMessage.visibility = GONE
        guideRow.orientation = VERTICAL
        guideRow.visibility = GONE
        // 内容为空时显示的导入按钮随分区宽度留白，与抬头行保持同一内边距。
        val guideInset = DeskSectionStyle.paddingXPx(context.resources.displayMetrics.widthPixels)
        guideRow.setPadding(guideInset, 0, guideInset, 0)
        guideRow.addView(importButton)
        guideRow.addView(importMessage)
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(guideRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /**
     * 丢弃当前页面并重新排版。
     *
     * 墨单外观配置变化或数据导入完成后调用，使内核按新的选项与数据重新构造分区；
     * 下次测量会重建页面并重新读取配置。
     */
    fun reload() {
        page?.release()
        page = null
        measuredWidth = 0
        measuredSectionWidth = 0
        loadFailed = false
        forceMeasure = true
        requestLayout()
        invalidate()
    }

    /**
     * 释放内核页面持有的资源。
     *
     * 模块销毁时调用，避免内核页面的数据缓存与图像资源长期驻留。
     */
    fun release() {
        dispatcher.cancel()
        page?.release()
        page = null
        canvasView.releaseCache()
    }

    override fun onDetachedFromWindow() {
        dispatcher.cancel()
        tipHandler.removeCallbacks(tipClearRunnable)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width =
            MeasureSpec
                .getSize(widthMeasureSpec)
                .takeIf { it > 0 }
                ?: (FALLBACK_WIDTH_DP * resources.displayMetrics.density).toInt()
        val current = page
        if ((width != measuredSectionWidth || forceMeasure) && !measuring) {
            measuredWidth = width
            forceMeasure = false
            if (current != null) {
                // 内核排版要遍历分区数据，放到后台线程计算，先以占位高度完成本次测量
                startMeasure(current, width)
            } else {
                // 首次绑定需要读取原盘数据，放到后台线程，先以占位高度完成测量
                ensurePage()
                sectionHeight = MIN_SECTION_HEIGHT_DP * deskHost.host.scale()
                canvasView.visibility = GONE
                guideRow.visibility = if (loadFailed) VISIBLE else GONE
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * 在后台线程核算分区排版。
     *
     * 内核的 height 与数据判空会遍历账单、资产与书目集合，数据量随书库规模增长，
     * 直接在主线程执行会造成输入事件超时；计算完成后回到主线程写入尺寸并请求重排。
     *
     * @param current 已绑定的墨台页面
     * @param width 卡片可用宽度
     */
    private fun startMeasure(
        current: DeskPage,
        width: Int,
    ) {
        measuring = true
        val unit = deskHost.host.scale()
        val header = renderer.headerHeight(unit)
        Thread(
            {
                val result =
                    runCatching {
                        val total = current.height(width).toFloat()
                        val blank = isEmptyData(current.data()) || total <= header
                        SectionMetrics(header, total, blank)
                    }.getOrNull()
                post {
                    measuring = false
                    // 无论核算成功与否都记录本次宽度：失败时保留占位高度并不再重试，
                    // 否则每帧测量都会重新起线程，消息队列被占满导致输入事件超时。
                    measuredSectionWidth = width
                    if (result != null) {
                        applyMetrics(result, unit)
                    } else {
                        loadFailed = true
                    }
                    requestLayout()
                    invalidate()
                }
            },
            LOAD_THREAD_NAME,
        ).apply { isDaemon = true }.start()
    }

    /**
     * 把后台核算结果写入视图状态。
     *
     * @param metrics 后台线程得到的排版结果
     * @param unit 当前排版单位，用于空态最小高度
     */
    private fun applyMetrics(
        metrics: SectionMetrics,
        unit: Float,
    ) {
        headerHeight = metrics.header
        empty = metrics.empty
        val content = (metrics.total - metrics.header).coerceAtLeast(0f)
        val cap = MAX_SECTION_HEIGHT_DP * resources.displayMetrics.density
        sectionHeight = content.coerceAtMost(cap)
        if (!empty) {
            sectionHeight = sectionHeight.coerceAtLeast(MIN_SECTION_HEIGHT_DP * unit)
        }
        canvasView.visibility = if (empty) GONE else VISIBLE
        guideRow.visibility = if (empty) VISIBLE else GONE
        canvasView.invalidateCache()
    }

    /** 后台排版核算结果，字段含义与 [applyMetrics] 的参数一致。 */
    private data class SectionMetrics(
        val header: Float,
        val total: Float,
        val empty: Boolean,
    )

    /**
     * 判断当前分区的数据是否为空。
     *
     * 墨单按账单条目判断，阅读资产按资产条目判断，阅读指纹按可绘制的书目判断；
     * 其他标识按非空处理，由内核自行呈现。
     *
     * @param data 已绑定页面对应的墨台数据
     * @return 数据为空时返回 true
     */
    private fun isEmptyData(data: DeskData): Boolean =
        when (moduleId) {
            MODULE_BILL -> data.bill()?.items?.isEmpty() != false
            MODULE_RANK -> data.assets().isEmpty()
            MODULE_FP -> data.fpBooks().isEmpty()
            else -> false
        }

    /**
     * 发起一次微读墨记数据导入。
     *
     * 导入通道缺失时直接显示说明文案；发起请求后由通道回调展示结果，
     * 回调结束重新排版并请求重绘，使分区在数据就绪后立即绘制。
     * 通道返回未发起且未回调时，按已存在迁移数据处理并给出说明。
     */
    private fun requestImport() {
        val importer = AppServices.wereadImport
        if (importer == null) {
            showImportMessage(IMPORT_UNAVAILABLE)
            return
        }
        importCallbackFired = false
        val started =
            importer.requestImport { message ->
                importCallbackFired = true
                post {
                    showImportMessage(message)
                    reload()
                    deskHost.host.requestRedraw()
                    invalidate()
                }
            }
        if (!started && !importCallbackFired) {
            showImportMessage(IMPORT_ALREADY_PRESENT)
        }
    }

    /**
     * 在引导区域显示导入结果文案。
     *
     * @param message 需要展示的说明文字
     */
    private fun showImportMessage(message: String) {
        importMessage.text = message
        importMessage.visibility = VISIBLE
        requestLayout()
    }

    /**
     * 返回已绑定的墨台页面，缺失时发起后台加载。
     *
     * 绑定过程读取原盘文件，数据量可达数百千字节，直接在主线程执行会造成输入超时，
     * 因此在独立线程完成构造与绑定，再回到主线程触发重新测量。
     *
     * @return 已绑定的墨台页面，尚在加载时返回 null
     */
    private fun ensurePage(): DeskPage? {
        val existing = page
        if (existing == null && !loading) {
            loading = true
            loadFailed = false
            Thread({ loadPage() }, LOAD_THREAD_NAME).apply { isDaemon = true }.start()
        }
        return existing
    }

    /** 后台线程内构造并绑定墨台页面，完成后回到主线程刷新布局。 */
    private fun loadPage() {
        val created =
            runCatching {
                DeskPage(deskHost.host).also { it.bind() }
            }.getOrNull()
        post {
            loading = false
            if (created == null) {
                loadFailed = true
            } else {
                page = created
                measuredSectionWidth = 0
                forceMeasure = true
            }
            requestLayout()
            invalidate()
        }
    }

    override fun kernelPage(): DeskPage? = page

    override fun headerOffset(): Float = headerHeight

    override fun onTapAction(action: Int) {
        val current = page ?: return
        current.onAction(action)
        if (current.padOpen()) forceMeasure = true
        deskHost.host.requestRedraw()
        canvasView.invalidateCache()
        invalidate()
    }

    override fun onLongPressAction(action: Int) {
        val current = page ?: return
        current.data().invalidateAll()
        forceMeasure = true
        when (action) {
            DeskActions.LONG_ASSET_TITLE -> {
                val title = current.pendingTitle()
                showTip(
                    if (title.isNullOrBlank()) {
                        TIP_TITLE_REMOVED
                    } else {
                        TIP_TITLE_REMOVED_PREFIX + title + TIP_TITLE_REMOVED_SUFFIX
                    },
                )
            }
            else -> Unit
        }
        requestLayout()
        canvasView.invalidateCache()
        invalidate()
    }

    override fun onPadChanged() {
        page?.data()?.invalidateAll()
        forceMeasure = true
        requestLayout()
        canvasView.invalidateCache()
        invalidate()
    }

    override fun invalidateSection() {
        canvasView.invalidate()
    }

    override fun onScrollFinished() {
        canvasView.invalidateCache()
        invalidate()
    }

    /**
     * 生成当前分区的竖版壁纸并打开预览对话框。
     *
     * 内核渲染壁纸需要遍历分区数据与位图资源，放在后台线程执行；渲染完成后
     * 回到主线程打开预览，失败时以提示说明。
     */
    fun generateWallpaper() {
        val current = page ?: return
        val section = wallpaperSection()
        Thread(
            {
                val bitmap =
                    runCatching {
                        current.renderWallpaper(section, WALLPAPER_WIDTH_PX, WALLPAPER_HEIGHT_PX)
                    }.getOrNull()
                post {
                    if (bitmap == null) {
                        runCatching { AppServices.router.toast(WALLPAPER_FAILED) }
                    } else {
                        WereadWallpaperDialog.show(context, bitmap)
                    }
                }
            },
            LOAD_THREAD_NAME,
        ).apply { isDaemon = true }.start()
    }

    /**
     * 打开阅读指纹的书目选择对话框。
     *
     * 候选书目来源于内核汇总的笔记书目；选定后写入内核配置并重新排版，
     * 使分区按新的书目重新绘制指纹图。
     */
    fun openFingerprintPicker() {
        val current = page ?: return
        val candidates = runCatching { current.fingerprintCandidates() }.getOrNull().orEmpty()
        FingerprintBookDialog.show(context, candidates) { bookId ->
            current.setFingerprintBook(bookId)
            current.data().invalidateAll()
            reload()
            deskHost.host.requestRedraw()
        }
    }

    /**
     * 返回当前模块对应的内核分区序号。
     *
     * @return 账单为 0、阅读资产为 1、阅读指纹为 2
     */
    private fun wallpaperSection(): Int =
        when (moduleId) {
            MODULE_BILL -> SECTION_BILL
            MODULE_RANK -> SECTION_ASSET
            MODULE_FP -> SECTION_FP
            else -> SECTION_BILL
        }

    /**
     * 显示长按提示条文案，四秒后自动消失。
     *
     * @param message 需要展示的文案
     */
    private fun showTip(message: String) {
        tipText = message
        tipHandler.removeCallbacks(tipClearRunnable)
        tipHandler.postDelayed(tipClearRunnable, TIP_DURATION_MS)
        canvasView.invalidate()
    }

    /** 分区绘制区域，把视图尺寸与触摸坐标换算到墨台页面坐标系。 */
    private inner class SectionCanvas(
        context: Context,
    ) : View(context) {
        /** 已渲染的分区位图，避免每次重绘都遍历内核分区数据。 */
        private var cache: Bitmap? = null

        /** 缓存位图对应的宽度，尺寸变化时重建。 */
        private var cacheWidth: Int = 0

        /** 是否有后台渲染在途，避免重绘阶段重复排队。 */
        private var rendering: Boolean = false

        init {
            isClickable = true
            isFocusable = true
        }

        /**
         * 丢弃已渲染位图并请求一次后台渲染。
         *
         * 分区尺寸、数据或内核状态变化后调用，下一次绘制使用新的位图。
         */
        fun invalidateCache() {
            cache = null
            rendering = false
            requestRender()
        }

        /** 回收已渲染位图，页面释放时调用。 */
        fun releaseCache() {
            cache?.recycle()
            cache = null
            rendering = false
        }

        /**
         * 判断尺寸与数据是否具备渲染条件。
         *
         * 宽度、高度缺失、数据为空或高度超出缓存上限时都不渲染。
         *
         * @param width 视图宽度
         * @param height 分区高度
         * @return 可以渲染时返回 true
         */
        private fun canRender(
            width: Int,
            height: Int,
        ): Boolean = width > 0 && height in 1..MAX_CACHE_HEIGHT_PX && !empty

        /**
         * 判断当前是否应当发起一次后台渲染。
         *
         * 已有渲染在途或缓存有效时不再重复渲染。
         *
         * @param width 视图宽度
         * @param height 分区高度
         * @return 需要发起渲染时返回 true
         */
        private fun canStartRender(
            width: Int,
            height: Int,
        ): Boolean = !rendering && cache == null && canRender(width, height)

        /**
         * 在后台线程渲染分区位图。
         *
         * 内核渲染会遍历账单、资产与书目集合，在主线程执行会造成输入事件超时；
         * 渲染完成后回到主线程替换缓存并重绘。
         */
        private fun requestRender() {
            val width = measuredWidth
            val height = ceil(sectionHeight).toInt()
            val current = page ?: return
            if (!canStartRender(width, height)) return
            rendering = true
            cacheWidth = width
            Thread(
                {
                    val rendered =
                        runCatching {
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val bitmapCanvas = Canvas(bitmap)
                            // 上移一个顶栏高度，使分区正文落在卡片可见区域内
                            bitmapCanvas.translate(0f, -headerHeight)
                            current.render(bitmapCanvas, width, renderHeightPx(), 0)
                            bitmap
                        }.getOrNull()
                    post {
                        rendering = false
                        if (rendered != null && !empty && width == measuredWidth) {
                            cache = rendered
                        } else {
                            rendered?.recycle()
                        }
                        invalidate()
                    }
                },
                LOAD_THREAD_NAME,
            ).apply { isDaemon = true }.start()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
            setMeasuredDimension(width, ceil(sectionHeight).toInt().coerceAtLeast(1))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (empty || sectionHeight <= 0f) return
            if (dispatcher.isScrolling() || page?.padOpen() == true) {
                // 拖动与键盘输入期间直接渲染，避免位图缓存的异步刷新造成画面滞后
                drawDirect(canvas)
            } else {
                val cached = cache
                if (cached != null && !cached.isRecycled && cacheWidth == measuredWidth) {
                    canvas.drawBitmap(cached, 0f, 0f, null)
                } else {
                    requestRender()
                }
            }
            drawTip(canvas)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val handled = dispatcher.onTouchEvent(event)
            return handled || super.onTouchEvent(event)
        }
    }

    /**
     * 直接把分区正文绘制到视图画布。
     *
     * 绘制前上移一个顶栏高度，使分区正文落在可视区域内；渲染高度按分区高度
     * 与顶栏高度之和计算，保证内核按完整页面完成排版。
     *
     * @param canvas 视图画布
     */
    private fun drawDirect(canvas: Canvas) {
        val current = page ?: return
        canvas.save()
        canvas.translate(0f, -headerHeight)
        current.render(canvas, measuredWidth, renderHeightPx(), 0)
        canvas.restore()
    }

    /** 返回内核渲染使用的页面高度，取分区高度加顶栏高度。 */
    private fun renderHeightPx(): Int = ceil(sectionHeight + headerHeight).toInt().coerceAtLeast(1)

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

        /** 账单分区序号，与内核分区常量一致。 */
        const val SECTION_BILL: Int = 0

        /** 阅读资产分区序号，与内核分区常量一致。 */
        const val SECTION_ASSET: Int = 1

        /** 阅读指纹分区序号，与内核分区常量一致。 */
        const val SECTION_FP: Int = 2

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

        /** 数据为空时的导入引导按钮文案。 */
        const val IMPORT_LABEL: String = "导入微读墨记数据"

        /** 导入通道尚未装配时的说明文案。 */
        const val IMPORT_UNAVAILABLE: String = "当前无法发起导入"

        /** 迁移数据已存在时的说明文案。 */
        const val IMPORT_ALREADY_PRESENT: String = "已存在迁移数据，无需重复导入"

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
