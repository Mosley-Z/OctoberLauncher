package com.shuiyu.octoberrain.feature.files.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.InkColors

/**
 * 分页容器视图。
 *
 * 页面铺纸白，顶栏绘制返回按钮、目录标题、路径与视图切换按钮，底栏绘制翻页按钮、
 * 页码与动作按钮；内容区由子类在 [onDrawContent] 中绘制，浮层由子类在 [onDrawOverlay]
 * 中绘制。几何在 [onSizeChanged] 中按实测内容区宽高重建，避免在构造阶段读取整屏尺寸。
 *
 * 本类同时暴露分页、绘制、手势与几何四组扩展点，方法数量高于 detekt 默认阈值；
 * 拆分会割裂视图生命周期，因此保留单类实现并在此处抑制函数数量检查。
 *
 * 参数 context 为宿主上下文；tokens 为文件页尺寸令牌。
 */
@Suppress("TooManyFunctions")
open class FilePager(
    context: Context,
    protected val tokens: FileTokens,
) : View(context) {
    /** 页码变化监听。 */
    fun interface PageListener {
        /** 当前页编号变化时触发，从 0 开始。 */
        fun onPageChanged(page: Int)
    }

    /** 分页状态，页码变更后通知监听并重绘。 */
    val pagerState: FilePagerState = FilePagerState(1)

    /** 顶栏标题，子类覆盖为当前目录名称。 */
    protected open val pageTitle: String get() = BrowseStrings.TITLE

    /** 顶栏副标题，子类覆盖为当前路径。 */
    protected open val pageSubtitle: String get() = ""

    /** 顶栏右侧动作按钮文案，空串表示不绘制。 */
    protected open val topActionLabel: String get() = ""

    /** 每页条目数，子类按视图形态覆盖。 */
    open val pageSize: Int get() = geometry?.pageSize ?: 1

    /** 最近一次布局得到的几何，尚未完成布局时为 null。 */
    protected val geometry: FileMetrics? get() = metrics

    private val chrome = FilePagerChrome(tokens)
    private var metrics: FileMetrics? = null
    private var listener: PageListener? = null

    private val touch =
        FilePagerTouch(
            view = this,
            tokens = tokens,
            chrome = chrome,
            handler =
                object : FilePagerTouch.Handler {
                    override fun onBack() {
                        onBackAction()
                    }

                    override fun onTopAction() {
                        this@FilePager.onTopAction()
                    }

                    override fun onTap(
                        x: Int,
                        y: Int,
                    ) {
                        onContentGesture(x, y, longPress = false)
                    }

                    override fun onLongPress(
                        x: Int,
                        y: Int,
                    ) {
                        onContentGesture(x, y, longPress = true)
                    }

                    override fun onPrevious() {
                        changePage(forward = false)
                    }

                    override fun onNext() {
                        changePage(forward = true)
                    }

                    override fun onAction() {
                        this@FilePager.onAction()
                    }
                },
        )

    init {
        isClickable = true
        setOnTouchListener { _, event -> touch.onTouchEvent(event) }
    }

    /** 绑定页码变化监听。 */
    fun setPageListener(value: PageListener?) {
        listener = value
    }

    /** 更新条目总数并重绘，页码越界时回退到末页。 */
    fun submitItemCount(count: Int) {
        pagerState.updateItemCount(count)
        listener?.onPageChanged(pagerState.page)
        invalidate()
    }

    /** 设置当前页，越界自动收敛。 */
    fun setPage(target: Int) {
        pagerState.setPage(target)
        listener?.onPageChanged(pagerState.page)
        invalidate()
    }

    /** 按方向翻页，供音量键与外部调用。 */
    fun turnPage(forward: Boolean) {
        changePage(forward)
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val anchor = metrics?.let { pagerState.firstIndex }
        val rebuilt = FileMetrics(width, height, tokens)
        metrics = rebuilt
        pagerState.pageSize = pageSize
        if (anchor != null) pagerState.setPage(anchor / pageSize)
        chrome.layout(width, rebuilt)
        onGeometryChanged(rebuilt)
    }

    override fun onDraw(canvas: Canvas) {
        // 每次整屏先铺纸白，避免上一页内容残留。
        canvas.drawColor(InkColors.PAPER)
        if (metrics == null) return
        chrome.drawTitle(canvas, pageTitle, pageSubtitle, topActionLabel)
        onDrawContent(canvas, chrome.contentRect, pagerState.firstIndex, pagerState.lastIndex)
        chrome.drawBottomBar(canvas, pagerState, actionLabel())
        onDrawOverlay(canvas)
    }

    /**
     * 绘制当前页内容。
     *
     * 参数 canvas 为画布；area 为内容区；start 为页首条目下标，end 为页末下标，不含。
     */
    protected open fun onDrawContent(
        canvas: Canvas,
        area: Rect,
        start: Int,
        end: Int,
    ) {}

    /** 在底栏之上绘制浮层，操作面板与长按面板由子类实现。 */
    protected open fun onDrawOverlay(canvas: Canvas) {}

    /**
     * 内容区手势回调，坐标以视图为准。
     *
     * 参数 longPress 为 true 表示长按，false 表示点击；
     * 子类据此打开条目或弹出操作面板。
     */
    protected open fun onContentGesture(
        x: Int,
        y: Int,
        longPress: Boolean,
    ) {}

    /** 底栏右侧动作按钮文案，空串表示不显示。 */
    protected open fun actionLabel(): String = ""

    /** 底栏右侧动作按钮被点击。 */
    protected open fun onAction() {}

    /** 顶栏返回按钮被点击。 */
    protected open fun onBackAction() {}

    /** 顶栏右侧动作按钮被点击。 */
    protected open fun onTopAction() {}

    /**
     * 几何重建回调，用于按单元格尺寸创建缩略图预取。
     *
     * 参数 rebuilt 为本次布局得到的几何。
     */
    protected open fun onGeometryChanged(rebuilt: FileMetrics) {}

    /** 按方向翻页并通知监听，页码由分页状态自行夹取；整页内容变化后请求一次局部刷新。 */
    private fun changePage(forward: Boolean) {
        if (forward) pagerState.nextPage() else pagerState.prevPage()
        listener?.onPageChanged(pagerState.page)
        invalidate()
        AppServices.refresh.requestPartial()
    }
}
