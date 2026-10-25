package com.shuiyu.octoberrain.feature.files.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.feature.files.data.FileItem
import com.shuiyu.octoberrain.feature.files.data.FilePrefs
import com.shuiyu.octoberrain.feature.files.data.FileSortKey
import kotlinx.coroutines.CoroutineScope

/**
 * 浏览页公共基类。
 *
 * 承担操作面板、长按面板、空态与错误态、翻页与缩略图预取的公共逻辑；
 * 网格与列表子类只负责条目布局、绘制与命中换算。页面数据由 [setData] 一次性写入，
 * 绘制阶段只读内存，不访问文件系统。
 *
 * 参数 context 为宿主上下文；tokens 为尺寸令牌；prefs 提供排序与显示偏好；
 * scope 为页面级协程作用域；callbacks 接收目录进入、打开条目与视图切换等回调。
 *
 * 本类集中承载面板、空态、翻页与预取四组职责，方法数量高于 detekt 默认阈值；
 * 拆分后会引入跨类共享状态，因此保留单类实现并在此处抑制函数数量检查。
 */
@Suppress("TooManyFunctions")
internal abstract class BrowsePane(
    context: Context,
    tokens: FileTokens,
    protected val prefs: FilePrefs,
    private val scope: CoroutineScope,
    private val callbacks: Callbacks,
) : FilePager(context, tokens) {
    /** 页面动作回调。 */
    interface Callbacks {
        /** 进入子目录。 */
        fun onEnterDir(item: FileItem)

        /** 打开图片或普通文件。 */
        fun onOpenItem(item: FileItem)

        /** 查看条目详情。 */
        fun onDetailRequested(item: FileItem)

        /** 删除普通文件，确认与IO由浏览页处理。 */
        fun onDeleteRequested(item: FileItem)

        /** 切换网格与列表视图。 */
        fun onToggleView()

        /** 排序或显示偏好变化，需要重新列举。 */
        fun onPrefsChanged()

        /** 打开自选文件夹。 */
        fun onManageFolders()

        /** 请求返回上级目录或关闭页面。 */
        fun onBackRequested()

        /** 页码变化，供页面请求局部刷新与调整预取。 */
        fun onPageChanged(page: Int)
    }

    /** 当前目录条目。 */
    protected var items: List<FileItem> = emptyList()
        private set

    /**
     * 取当前目录条目快照。
     *
     * 返回值按当前排序排列，供查看器构造同目录图片路径与起始下标；
     * 列表在列举完成或刷新后随 [items] 更新。
     */
    fun itemSnapshot(): List<FileItem> = items

    /** 目录读取失败说明，空串表示无错误。 */
    protected var error: String = ""
        private set

    /** 长按选中的条目，仅在长按面板打开期间非空。 */
    protected val pressedItemOrNull: FileItem?
        get() = if (pressedPanelOpen) pressedItem else null

    private val bodyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.bodyText
            color = InkColors.INK
        }
    private val smallPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.smallText
            color = InkColors.INK
        }
    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
            color = InkColors.INK
        }
    private val paperPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.PAPER
        }
    private val highlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK_4
        }

    private var directoryTitle = ""
    private var directoryPath = ""
    private var panelOpen = false
    private var pressedPanelOpen = false
    private var pressedItem: FileItem? = null
    private var contentArea = Rect()
    private var prefetcher: ThumbPrefetcher? = null

    init {
        setPageListener { page ->
            prefetcher?.request(items, page)
            callbacks.onPageChanged(page)
        }
    }

    /** 顶栏标题取目录名称。 */
    override val pageTitle: String get() = directoryTitle

    /** 顶栏副标题取当前路径。 */
    override val pageSubtitle: String get() = directoryPath

    /** 顶栏动作按钮用于切换视图，文案表示将要切换到的形态。 */
    override val topActionLabel: String
        get() = if (prefs.grid) BrowseStrings.LIST else BrowseStrings.GRID

    /** 底栏动作按钮在面板打开时显示关闭。 */
    override fun actionLabel(): String = if (panelOpen) BrowseStrings.CLOSE else BrowseStrings.MENU

    /**
     * 写入当前目录数据。
     *
     * 参数 items 为已排序条目；title 为目录名称；path 为目录绝对路径。
     * 写入后关闭操作面板、更新条目总数并重绘。
     */
    fun setData(
        items: List<FileItem>,
        title: String,
        path: String,
    ) {
        this.items = items
        error = ""
        directoryTitle = title
        directoryPath = path
        panelOpen = false
        pressedPanelOpen = false
        pressedItem = null
        submitItemCount(items.size)
        invalidate()
    }

    /**
     * 写入读取失败状态。
     *
     * 参数 message 为失败说明，写入后清空条目并关闭操作面板。
     */
    fun setError(message: String) {
        items = emptyList()
        error = message
        panelOpen = false
        pressedPanelOpen = false
        pressedItem = null
        submitItemCount(0)
        invalidate()
    }

    /** 判断操作面板或长按面板是否打开。 */
    fun isPanelOpen(): Boolean = panelOpen || pressedPanelOpen

    /** 关闭全部面板，返回此前是否有面板打开。 */
    fun closePanel(): Boolean {
        if (!isPanelOpen()) return false
        panelOpen = false
        pressedPanelOpen = false
        pressedItem = null
        invalidate()
        return true
    }

    /** 处理返回：先关闭面板，面板未打开时交给页面返回上级目录。 */
    fun handleBack(): Boolean {
        if (closePanel()) return true
        callbacks.onBackRequested()
        return true
    }

    /** 请求预取当前页缩略图。 */
    protected fun requestPrefetch() {
        prefetcher?.request(items, pagerState.page)
    }

    /** 丢弃某路径的缩略图，供文件写入或删除后调用。 */
    fun invalidateThumb(path: String) {
        prefetcher?.invalidate(path)
    }

    /** 取条目已就绪的缩略图。 */
    protected fun bitmapOf(item: FileItem): android.graphics.Bitmap? = prefetcher?.bitmapOf(item)

    /**
     * 绘制空态或错误态。
     *
     * 参数 canvas 为画布；area 为内容区。存在条目时返回 false，由子类继续绘制条目；
     * 无条目时绘制居中提示并返回 true。
     */
    protected fun drawNoticeIfEmpty(
        canvas: Canvas,
        area: Rect,
    ): Boolean {
        if (items.isNotEmpty()) return false
        val message = error.ifEmpty { BrowseStrings.EMPTY }
        val left = area.left + tokens.padH
        val right = area.right - tokens.padH
        val lines = wrap(message, bodyPaint, right - left)
        val lineHeight = (bodyPaint.descent() - bodyPaint.ascent()).toInt() + tokens.gap
        val startY = area.centerY() - (lines.size * lineHeight) / 2
        for (index in lines.indices) {
            val baseline = startY + index * lineHeight - bodyPaint.ascent()
            canvas.drawText(lines[index], left.toFloat(), baseline, bodyPaint)
        }
        return true
    }

    /** 子类返回条目在坐标处的下标，未命中返回 -1。 */
    protected abstract fun indexAt(
        x: Int,
        y: Int,
        area: Rect,
    ): Int

    /** 子类按几何返回缩略图目标长边。 */
    protected open val includeEpubThumbnails: Boolean get() = false

    protected abstract fun thumbEdge(metrics: FileMetrics): Int

    override fun onBackAction() {
        handleBack()
    }

    override fun onTopAction() {
        closePanel()
        callbacks.onToggleView()
    }

    override fun onAction() {
        if (pressedPanelOpen) {
            closePanel()
            return
        }
        panelOpen = !panelOpen
        invalidate()
    }

    override fun onContentGesture(
        x: Int,
        y: Int,
        longPress: Boolean,
    ) {
        when {
            longPress -> handleLongPress(x, y)
            pressedPanelOpen -> handlePressedPanelTap(y)
            panelOpen -> handlePanelTap(y)
            else -> openAt(x, y)
        }
    }

    override fun onDrawOverlay(canvas: Canvas) {
        if (contentArea.isEmpty) return
        when {
            panelOpen -> drawPanel(canvas)
            pressedPanelOpen -> drawPressedPanel(canvas)
        }
    }

    override fun onGeometryChanged(rebuilt: FileMetrics) {
        super.onGeometryChanged(rebuilt)
        contentArea.set(0, rebuilt.contentTop, rebuilt.widthPx, rebuilt.contentBottom)
        prefetcher?.release()
        prefetcher =
            ThumbPrefetcher(
                context = getContext(),
                scope = scope,
                targetEdge = thumbEdge(rebuilt),
                pageSize = pageSize,
                onReady = { invalidate() },
                includeEpub = includeEpubThumbnails,
            )
        requestPrefetch()
    }

    /** 长按：关闭已有面板，或在条目上打开长按面板。 */
    private fun handleLongPress(
        x: Int,
        y: Int,
    ) {
        if (closePanel()) return
        val index = indexAt(x, y, contentArea)
        val item = items.getOrNull(index) ?: return
        pressedItem = item
        pressedPanelOpen = true
        invalidate()
    }

    /** 命中条目后按类型进入目录或打开文件。 */
    private fun openAt(
        x: Int,
        y: Int,
    ) {
        val item = items.getOrNull(indexAt(x, y, contentArea)) ?: return
        if (item.dir) callbacks.onEnterDir(item) else callbacks.onOpenItem(item)
    }

    /** 长按面板点击：命中行执行动作，空白处关闭面板。 */
    private fun handlePressedPanelTap(y: Int) {
        val item = pressedItem
        val rows = BrowsePanel.pressedRows(item?.dir != false)
        val row = BrowsePanel.rowAt(y, contentArea.top, tokens.rowH, rows.size)
        if (row < 0 || item == null) {
            closePanel()
            return
        }
        panelOpen = false
        pressedPanelOpen = false
        pressedItem = null
        invalidate()
        when (rows[row]) {
            PressedRow.OPEN -> if (item.dir) callbacks.onEnterDir(item) else callbacks.onOpenItem(item)
            PressedRow.DETAIL -> callbacks.onDetailRequested(item)
            PressedRow.DELETE -> callbacks.onDeleteRequested(item)
        }
    }

    /** 操作面板点击：命中行执行动作，空白处关闭面板。 */
    private fun handlePanelTap(y: Int) {
        val row = BrowsePanel.rowAt(y, contentArea.top, tokens.rowH, BrowsePanel.rows.size)
        if (row < 0) {
            closePanel()
            return
        }
        val panelRow = BrowsePanel.rows[row]
        when (BrowsePanel.actionOf(panelRow)) {
            PanelAction.SORT_NAME -> applySort(FileSortKey.NAME)
            PanelAction.SORT_TIME -> applySort(FileSortKey.TIME)
            PanelAction.SORT_SIZE -> applySort(FileSortKey.SIZE)
            PanelAction.SORT_TYPE -> applySort(FileSortKey.TYPE)
            PanelAction.TOGGLE_ORDER -> {
                prefs.sortAscending = !prefs.sortAscending
                finishPanelChange()
            }
            PanelAction.TOGGLE_HIDDEN -> {
                prefs.showHidden = !prefs.showHidden
                finishPanelChange()
            }
            PanelAction.TOGGLE_VIEW -> {
                closePanel()
                callbacks.onToggleView()
            }
            PanelAction.MANAGE_FOLDERS -> {
                closePanel()
                callbacks.onManageFolders()
            }
        }
    }

    /** 写入排序依据并刷新列表。 */
    private fun applySort(key: FileSortKey) {
        prefs.sortKey = key
        finishPanelChange()
    }

    /** 关闭面板并通知页面重新列举。 */
    private fun finishPanelChange() {
        closePanel()
        callbacks.onPrefsChanged()
    }

    /** 绘制操作面板：纸白底、行分隔线、行标签与右侧状态。 */
    private fun drawPanel(canvas: Canvas) {
        val area = contentArea
        val bottom = area.top + tokens.rowH * BrowsePanel.rows.size
        canvas.drawRect(area.left.toFloat(), area.top.toFloat(), area.right.toFloat(), bottom.toFloat(), paperPaint)
        canvas.drawLine(
            area.left.toFloat(),
            area.top.toFloat(),
            area.right.toFloat(),
            area.top.toFloat(),
            linePaint,
        )
        val state =
            BrowsePanelState(
                sortKey = prefs.sortKey,
                ascending = prefs.sortAscending,
                showHidden = prefs.showHidden,
                grid = prefs.grid,
            )
        for (index in BrowsePanel.rows.indices) {
            val row = BrowsePanel.rows[index]
            val top = area.top + index * tokens.rowH
            val rowRect = Rect(area.left, top, area.right, top + tokens.rowH)
            if (BrowsePanel.isSelected(row, state)) canvas.drawRect(rowRect, highlightPaint)
            val baseline = rowRect.centerY() + (bodyPaint.descent() - bodyPaint.ascent()) / 2f - bodyPaint.descent()
            canvas.drawText(row.label, (area.left + tokens.padH).toFloat(), baseline, bodyPaint)
            val value = BrowsePanel.stateText(row, state)
            if (value.isNotEmpty()) {
                val width = smallPaint.measureText(value)
                canvas.drawText(
                    value,
                    (area.right - tokens.padH).toFloat() - width,
                    baseline,
                    smallPaint,
                )
            }
            canvas.drawLine(
                (area.left + tokens.padH).toFloat(),
                rowRect.bottom.toFloat(),
                (area.right - tokens.padH).toFloat(),
                rowRect.bottom.toFloat(),
                linePaint,
            )
        }
        canvas.drawRect(
            area.left.toFloat(),
            bottom.toFloat(),
            area.right.toFloat(),
            (bottom + tokens.stroke).toFloat(),
            linePaint,
        )
    }

    /** 绘制长按面板：按文件类型给出动作，位置与操作面板一致。 */
    private fun drawPressedPanel(canvas: Canvas) {
        val area = contentArea
        val rows = BrowsePanel.pressedRows(pressedItem?.dir != false)
        val bottom = area.top + tokens.rowH * rows.size
        canvas.drawRect(area.left.toFloat(), area.top.toFloat(), area.right.toFloat(), bottom.toFloat(), paperPaint)
        canvas.drawLine(
            area.left.toFloat(),
            area.top.toFloat(),
            area.right.toFloat(),
            area.top.toFloat(),
            linePaint,
        )
        for (index in rows.indices) {
            val top = area.top + index * tokens.rowH
            val baseline =
                top + tokens.rowH / 2f + (bodyPaint.descent() - bodyPaint.ascent()) / 2f - bodyPaint.descent()
            canvas.drawText(
                rows[index].label,
                (area.left + tokens.padH).toFloat(),
                baseline,
                bodyPaint,
            )
            canvas.drawLine(
                (area.left + tokens.padH).toFloat(),
                (top + tokens.rowH).toFloat(),
                (area.right - tokens.padH).toFloat(),
                (top + tokens.rowH).toFloat(),
                linePaint,
            )
        }
        canvas.drawRect(
            area.left.toFloat(),
            bottom.toFloat(),
            area.right.toFloat(),
            (bottom + tokens.stroke).toFloat(),
            linePaint,
        )
    }

    /**
     * 按像素宽度折行。
     *
     * 参数 text 为原文；paint 提供字号；maxWidth 为可用宽度。中文按字符切分，
     * 每行不超过可用宽度，最多返回三行，超出部分以省略号结束。
     */
    private fun wrap(
        text: String,
        paint: Paint,
        maxWidth: Int,
    ): List<String> {
        if (maxWidth <= 0 || text.isEmpty()) return listOf("")
        val lines = ArrayList<String>()
        var cursor = 0
        while (cursor < text.length && lines.size < MAX_NOTICE_LINES) {
            var end = cursor + 1
            while (end <= text.length && paint.measureText(text, cursor, end) <= maxWidth) {
                end += 1
            }
            val safeEnd = (end - 1).coerceAtLeast(cursor + 1).coerceAtMost(text.length)
            lines.add(text.substring(cursor, safeEnd))
            cursor = safeEnd
        }
        if (cursor < text.length && lines.isNotEmpty()) {
            val last = lines.removeAt(lines.size - 1)
            lines.add(if (paint.measureText(last + "…") <= maxWidth) last + "…" else last)
        }
        return lines
    }

    private companion object {
        /** 空态与错误态最多显示三行。 */
        const val MAX_NOTICE_LINES = 3
    }
}
