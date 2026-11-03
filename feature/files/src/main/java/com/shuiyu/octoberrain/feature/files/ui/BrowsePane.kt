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

/** 多选态批量动作，供动作行与浏览页共用。 */
internal enum class BatchAction {
    /** 复制到目标目录。 */
    COPY,

    /** 移动到目标目录。 */
    MOVE,

    /** 删除选中条目。 */
    DELETE,
}

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

        /** 进入多选，页面据此记录多选来源并刷新状态。 */
        fun onEnterMultiSelect()

        /** 退出多选。 */
        fun onExitMultiSelect()

        /** 已选数量变化。 */
        fun onSelectionChanged(count: Int)

        /** 请求批量动作，目标目录与IO由浏览页处理。 */
        fun onBatchAction(
            action: BatchAction,
            targets: List<FileItem>,
        )

        /** 确认把当前目录作为批量动作的目标目录。 */
        fun onPickDirConfirm()

        /** 取消目标目录选择，放弃当前批量动作。 */
        fun onCancelPick()

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

    /** 选中底色：中灰，与纸白拉开明显层次，同时仍能看清条目文字。 */
    private val selectionFillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = InkColors.INK_3
        }

    /** 选中外框：纯黑，线宽为常规描边的两倍，在灰度屏上边界清晰。 */
    private val selectionStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (tokens.stroke * 2).toFloat()
            color = InkColors.INK
        }

    /** 选中项左缘标记条：纯黑实心竖条，用于在缩略图区域快速定位选中行。 */
    private val selectionMarkPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = InkColors.INK
        }

    /** 左缘标记条宽度，取 4dp，保证墨水屏上可见。 */
    private val selectionMarkWidth = tokens.dp(4f)

    private var directoryTitle = ""
    private var directoryPath = ""
    private var panelOpen = false
    private var pressedPanelOpen = false
    private var pressedItem: FileItem? = null
    private var contentArea = Rect()
    private var prefetcher: ThumbPrefetcher? = null

    /** 是否处于多选态。 */
    private var selecting = false

    /** 多选选中的条目路径，仅对当前目录生效，翻页保留、换目录按现存条目裁剪。 */
    private val selectedPaths = linkedSetOf<String>()

    /** 是否处于目标目录选择态，由批量复制与移动进入。 */
    private var pickingDir = false

    /** 目标目录选择态的顶栏标题。 */
    private var pickDirTitle = ""

    init {
        setPageListener { page ->
            prefetcher?.request(items, page)
            callbacks.onPageChanged(page)
        }
    }

    /** 顶栏标题：目标目录选择态显示选择用途，多选态在选中后显示已选数量，其余显示目录名称。 */
    override val pageTitle: String
        get() =
            when {
                pickingDir -> pickDirTitle
                selecting ->
                    if (selectedPaths.isEmpty()) {
                        BrowseStrings.MULTI
                    } else {
                        BrowseStrings.selectedCount(selectedPaths.size)
                    }
                else -> directoryTitle
            }

    /** 顶栏副标题取当前路径。 */
    override val pageSubtitle: String get() = directoryPath

    /** 顶栏动作按钮用于切换视图；目标目录选择态改为确认选择。 */
    override val topActionLabel: String
        get() =
            when {
                pickingDir -> BrowseStrings.PICK_HERE
                prefs.grid -> BrowseStrings.LIST
                else -> BrowseStrings.GRID
            }

    /** 底栏动作按钮：多选态显示完成，目标目录选择态留空。 */
    override fun actionLabel(): String =
        when {
            pickingDir -> ""
            selecting -> BrowseStrings.DONE
            panelOpen -> BrowseStrings.CLOSE
            else -> BrowseStrings.MENU
        }

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
        // 多选只对当前目录有效：换目录或刷新后裁剪已不存在的路径，其余保持选中。
        if (selecting) {
            selectedPaths.retainAll(items.mapTo(HashSet()) { it.path })
            callbacks.onSelectionChanged(selectedPaths.size)
        }
        submitItemCount(items.size)
        invalidate()
    }

    /**
     * 写入读取失败状态。
     *
     * 参数 message 为失败说明，写入后清空条目并关闭操作面板。
     */
    fun setError(message: String) {
        exitMultiSelect()
        items = emptyList()
        error = message
        panelOpen = false
        pressedPanelOpen = false
        pressedItem = null
        submitItemCount(0)
        invalidate()
    }

    /** 是否处于多选态。 */
    fun isSelecting(): Boolean = selecting

    /** 是否处于目标目录选择态。 */
    fun isPickingDir(): Boolean = pickingDir

    /** 目标目录选择态在顶栏显示取消按钮，其余状态隐藏。 */
    override val showCancelButton: Boolean get() = pickingDir

    /** 顶栏取消按钮点击：放弃当前批量动作并回到来源目录。 */
    override fun onCancelAction() {
        callbacks.onCancelPick()
    }

    /**
     * 取当前选中条目。
     *
     * 返回顺序与当前目录条目顺序一致，已不在当前目录的路径不返回。
     */
    fun selectedItems(): List<FileItem> = items.filter { selectedPaths.contains(it.path) }

    /** 进入多选；重复调用保持现状，并在进入后关闭操作面板。 */
    fun enterMultiSelect() {
        if (pickingDir) return
        closePanel()
        if (selecting) return
        selecting = true
        selectedPaths.clear()
        setSelectingMode(true)
        callbacks.onSelectionChanged(0)
        callbacks.onEnterMultiSelect()
        invalidate()
    }

    /** 退出多选并清空选中；未处于多选时不做处理。 */
    fun exitMultiSelect() {
        if (!selecting) return
        selecting = false
        selectedPaths.clear()
        setSelectingMode(false)
        callbacks.onSelectionChanged(0)
        callbacks.onExitMultiSelect()
        invalidate()
    }

    /**
     * 恢复一批选中路径，用于取消目标目录选择后回到多选态。
     *
     * 参数 paths 为原选中条目的路径集合；仅保留当前目录仍存在的条目，
     * 存在有效项时进入多选态，否则保持普通浏览态。
     */
    fun restoreSelection(paths: Collection<String>) {
        if (paths.isEmpty() || pickingDir) return
        val available = items.mapTo(HashSet()) { it.path }
        val restored = paths.filter { available.contains(it) }
        if (restored.isEmpty()) return
        if (!selecting) {
            selecting = true
            setSelectingMode(true)
        }
        selectedPaths.clear()
        selectedPaths.addAll(restored)
        callbacks.onSelectionChanged(selectedPaths.size)
        invalidate()
    }

    /**
     * 进入目标目录选择态。
     *
     * 参数 title 为选择用途标题，取复制到或移动到；进入前退出多选并关闭面板，
     * 此后点击目录进入，顶栏右侧动作按钮确认选择。
     */
    fun beginPickDir(title: String) {
        exitMultiSelect()
        closePanel()
        pickingDir = true
        pickDirTitle = title
        invalidate()
    }

    /** 退出目标目录选择态，恢复普通浏览与底栏动作。 */
    fun endPickDir() {
        if (!pickingDir) return
        pickingDir = false
        pickDirTitle = ""
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

    /** 处理返回：先关闭面板，随后退出多选或目标目录选择，最后由页面处理目录返回。 */
    fun handleBack(): Boolean {
        when {
            closePanel() -> Unit
            selecting -> exitMultiSelect()
            else -> callbacks.onBackRequested()
        }
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
        if (pickingDir) {
            callbacks.onPickDirConfirm()
            return
        }
        closePanel()
        callbacks.onToggleView()
    }

    override fun onAction() {
        when {
            pickingDir -> Unit
            selecting -> exitMultiSelect()
            pressedPanelOpen -> closePanel()
            else -> {
                panelOpen = !panelOpen
                invalidate()
            }
        }
    }

    override fun onContentGesture(
        x: Int,
        y: Int,
        longPress: Boolean,
    ) {
        when {
            pickingDir -> openAtPickDir(x, y)
            selecting -> handleSelectionTap(x, y)
            longPress -> handleLongPress(x, y)
            pressedPanelOpen -> handlePressedPanelTap(y)
            panelOpen -> handlePanelTap(y)
            else -> openAt(x, y)
        }
    }

    override fun onDrawOverlay(canvas: Canvas) {
        if (contentArea.isEmpty) return
        when {
            pickingDir -> Unit
            selecting -> drawSelectionOverlay(canvas)
            panelOpen -> drawPanel(canvas)
            pressedPanelOpen -> drawPressedPanel(canvas)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 页面重建或销毁时复位多选态，避免内容区停留在收缩后的几何。
        if (selecting) {
            selecting = false
            selectedPaths.clear()
            setSelectingMode(false)
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

    /** 目标目录选择态：仅目录可进入，文件点击忽略。 */
    private fun openAtPickDir(
        x: Int,
        y: Int,
    ) {
        val item = items.getOrNull(indexAt(x, y, contentArea)) ?: return
        if (item.dir) callbacks.onEnterDir(item)
    }

    /** 多选态点击：命中动作行触发批量动作，命中条目切换选中。 */
    private fun handleSelectionTap(
        x: Int,
        y: Int,
    ) {
        val cell = selectionCellAt(x, y, selectionBarRects(width, height, tokens.bottomBarH, tokens.selectionBarH))
        when (cell) {
            0 -> dispatchBatch(BatchAction.COPY)
            1 -> dispatchBatch(BatchAction.MOVE)
            2 -> dispatchBatch(BatchAction.DELETE)
            else -> toggleSelectionAt(x, y)
        }
    }

    /** 分发批量动作；空选时不触发，避免无效确认框。 */
    private fun dispatchBatch(action: BatchAction) {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        callbacks.onBatchAction(action, targets)
    }

    /** 切换坐标处条目的选中状态，并同步页面状态。 */
    private fun toggleSelectionAt(
        x: Int,
        y: Int,
    ) {
        val item = items.getOrNull(indexAt(x, y, contentArea)) ?: return
        if (!selectedPaths.remove(item.path)) selectedPaths.add(item.path)
        callbacks.onSelectionChanged(selectedPaths.size)
        invalidate()
    }

    /**
     * 在条目内容之下铺选中底色。
     *
     * 底色先于条目绘制，缩略图、名称与分隔线随即覆盖在其上，避免遮挡条目信息。
     * 参数 canvas 为画布；矩形与网格、列表的命中区域一致。
     */
    override fun onDrawContentBackground(canvas: Canvas) {
        if (!selecting || selectedPaths.isEmpty()) return
        for (index in pagerState.firstIndex until pagerState.lastIndex) {
            val rect =
                items
                    .getOrNull(index)
                    ?.takeIf { selectedPaths.contains(it.path) }
                    ?.let { itemRect(index) }
                    ?: continue
            canvas.drawRect(rect, selectionFillPaint)
        }
    }

    /** 绘制选中条目外框与左缘标记条，位置与网格、列表的命中区域一致。 */
    private fun drawSelectionOverlay(canvas: Canvas) {
        if (selectedPaths.isEmpty()) return
        for (index in pagerState.firstIndex until pagerState.lastIndex) {
            val rect =
                items
                    .getOrNull(index)
                    ?.takeIf { selectedPaths.contains(it.path) }
                    ?.let { itemRect(index) }
                    ?: continue
            drawSelectionFrame(canvas, rect)
        }
    }

    /**
     * 在条目矩形内绘制选中外框与左缘标记条。
     *
     * 参数 canvas 为画布；rect 为条目在视图内的矩形。
     * 外框按线宽内缩一半，避免边缘被条目边界裁掉。
     */
    private fun drawSelectionFrame(
        canvas: Canvas,
        rect: Rect,
    ) {
        val inset = (selectionStrokePaint.strokeWidth / 2f).toInt().coerceAtLeast(1)
        canvas.drawRect(
            (rect.left + inset).toFloat(),
            (rect.top + inset).toFloat(),
            (rect.right - inset).toFloat(),
            (rect.bottom - inset).toFloat(),
            selectionStrokePaint,
        )
        canvas.drawRect(
            rect.left.toFloat(),
            rect.top.toFloat(),
            (rect.left + selectionMarkWidth).toFloat(),
            rect.bottom.toFloat(),
            selectionMarkPaint,
        )
    }

    /**
     * 在矩形内绘制完整选中样式：中灰底、纯黑外框与左缘标记条。
     *
     * 参数 canvas 为画布；rect 为目标矩形。
     * 用于操作面板选中行等叠加在内容之上的场景，不需要再单独铺底色。
     */
    private fun drawSelectionDecoration(
        canvas: Canvas,
        rect: Rect,
    ) {
        canvas.drawRect(rect, selectionFillPaint)
        drawSelectionFrame(canvas, rect)
    }

    /** 取条目在屏幕上的矩形，按当前视图形态复用列表行或网格单元格命中区域。 */
    private fun itemRect(index: Int): Rect? {
        val metrics = geometry ?: return null
        val slot = index - pagerState.firstIndex
        return when {
            slot < 0 -> null
            prefs.grid && slot >= metrics.pageSize -> null
            prefs.grid -> {
                val hit = metrics.gridCell(slot, contentArea.left, contentArea.top).hit
                Rect(hit.left, hit.top, hit.right, hit.bottom)
            }
            slot >= metrics.listRows -> null
            else -> {
                val top = contentArea.top + slot * tokens.rowH
                Rect(contentArea.left, top, contentArea.right, top + tokens.rowH)
            }
        }
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
            PanelAction.ENTER_MULTI -> {
                closePanel()
                enterMultiSelect()
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
            if (BrowsePanel.isSelected(row, state)) drawSelectionDecoration(canvas, rowRect)
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
