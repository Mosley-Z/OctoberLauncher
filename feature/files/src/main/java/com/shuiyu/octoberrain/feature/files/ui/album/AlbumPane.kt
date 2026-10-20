package com.shuiyu.octoberrain.feature.files.ui.album

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.feature.files.data.FileItem
import com.shuiyu.octoberrain.feature.files.data.MediaDay
import com.shuiyu.octoberrain.feature.files.data.MediaEntry
import com.shuiyu.octoberrain.feature.files.ui.FileTokens
import com.shuiyu.octoberrain.feature.files.ui.PaneDraw
import com.shuiyu.octoberrain.feature.files.ui.ThumbPrefetcher
import kotlinx.coroutines.CoroutineScope

/**
 * 相册时间轴面板。
 *
 * 按日分块绘制三列缩略图网格，块头显示日期与当日数量；缩略图由模块既有缩略图预取器
 * 读取，命中内存缓存时直接绘制，未命中时绘制占位描边框。点击打开查看器，长按回调节目
 * 供页面弹出操作菜单。面板只读取传入的日分组快照，不访问索引与文件系统。
 *
 * 参数 context 为视图上下文；tokens 为文件页尺寸令牌；scope 供缩略图预取使用；
 * callbacks 为点击与长按回调。
 */
@Suppress("TooManyFunctions") // 详情页绘制、布局与触摸处理集中在本视图，函数数量按模块既有做法抑制。
internal class AlbumPane(
    context: Context,
    private val tokens: FileTokens,
    scope: CoroutineScope,
    private val callbacks: Callbacks,
) : View(context) {
    /**
     * 面板交互回调。
     *
     * onOpenPhoto 在点击缩略图时触发，参数为所属日分组与日分组内下标；
     * onLongPress 在长按时触发，参数为对应条目。
     */
    internal interface Callbacks {
        /** 点击缩略图，参数 day 为所属日分组，index 为日分组内下标。 */
        fun onOpenPhoto(
            day: MediaDay,
            index: Int,
        )

        /** 长按缩略图，参数 entry 为对应条目。 */
        fun onLongPress(entry: MediaEntry)
    }

    private val paintInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkColors.INK }
    private val paintMuted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkColors.INK_3 }
    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkColors.PAPER }
    private val paintStroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
            color = InkColors.INK_3
        }

    private val titlePaint = Paint(paintInk).apply { textSize = tokens.titleText }
    private val bodyPaint = Paint(paintInk).apply { textSize = tokens.bodyText }
    private val smallPaint = Paint(paintMuted).apply { textSize = tokens.smallText }

    private val prefetcher =
        ThumbPrefetcher(
            context = context,
            scope = scope,
            targetEdge = tokens.dp(120f),
            pageSize = AlbumPaging.MAX_CELLS_PER_PAGE,
            onReady = { postInvalidateOnAnimation() },
        )

    private var days: List<MediaDay> = emptyList()
    private var page: Int = 0
    private var statusText: String = ""
    private var loading: Boolean = false
    private var errorText: String = ""
    private var cells: List<Cell> = emptyList()
    private var pages: List<AlbumPage> = emptyList()

    /**
     * 写入当日数据与目标页码。
     *
     * 参数 dayList 为全部日分组；targetPage 为期望页码，越界时夹取到有效范围。
     * 写入后重算单元格并请求缩略图预取。
     */
    fun submit(
        dayList: List<MediaDay>,
        targetPage: Int,
    ) {
        days = dayList
        pages = AlbumPaging.pages(dayList)
        page = AlbumPaging.clampPage(pageCount(), targetPage)
        rebuild()
    }

    /** 设置加载状态，参数 value 为 true 时显示建立索引提示。 */
    fun setLoading(value: Boolean) {
        loading = value
        invalidate()
    }

    /** 设置错误提示，参数 text 为空串表示清除。 */
    fun setError(text: String) {
        errorText = text
        invalidate()
    }

    /** 设置状态行文本，参数 text 显示在页面顶部。 */
    fun setStatus(text: String) {
        statusText = text
        invalidate()
    }

    /** 当前页码。 */
    fun currentPage(): Int = page

    /** 总页数，供页码文本换算。 */
    fun pageCount(): Int = maxOf(1, pages.size)

    /** 日分组总数，供状态行判断相册是否为空。 */
    fun dayCount(): Int = days.size

    /** 是否还有下一页。 */
    fun hasNext(): Boolean = page < pageCount() - 1

    /** 是否还有上一页。 */
    fun hasPrevious(): Boolean = page > 0

    /**
     * 翻页。
     *
     * 参数 forward 为 true 时前进一页；已到边界时返回 false，页面不改动。
     */
    fun turnPage(forward: Boolean): Boolean {
        val target = page + if (forward) 1 else -1
        val clamped = AlbumPaging.clampPage(pageCount(), target)
        if (clamped == page) return false
        page = clamped
        rebuild()
        return true
    }

    /** 释放缩略图预取任务，页面销毁时调用。 */
    fun release() {
        prefetcher.release()
    }

    /** 重算单元格并请求缩略图。 */
    private fun rebuild() {
        cells = buildCells(page)
        val items = cells.mapNotNull { cell -> cell.entry?.let(::toFileItem) }
        prefetcher.request(items, 0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (days.isEmpty()) {
            drawEmptyState(canvas)
            return
        }
        val list = buildCells(page)
        if (list != cells) cells = list
        cells.forEach { cell -> if (cell.header) drawHeaderCell(canvas, cell) else drawEntryCell(canvas, cell) }
    }

    /** 绘制空态：顶部状态文字与居中提示。 */
    private fun drawEmptyState(canvas: Canvas) {
        if (statusText.isNotEmpty()) {
            PaneDraw.centered(canvas, statusText, smallPaint, Rect(0, 0, width, tokens.dp(28f)))
        }
        val message =
            when {
                errorText.isNotEmpty() -> errorText
                loading -> LOADING_HINT
                else -> EMPTY_HINT
            }
        PaneDraw.centered(canvas, message, bodyPaint, Rect(0, height / 2 - tokens.dp(24f), width, height / 2))
    }

    /** 绘制日标题行：左侧标题与右侧张数。 */
    private fun drawHeaderCell(
        canvas: Canvas,
        cell: Cell,
    ) {
        PaneDraw.lineCentered(
            canvas = canvas,
            text = cell.headerText,
            paint = titlePaint,
            left = cell.image.left.toFloat(),
            centerY = cell.image.centerY().toFloat(),
        )
        PaneDraw.rightCentered(canvas, cell.countText, smallPaint, cell.image)
    }

    /** 绘制单张缩略图与名称，图片尚未解码时只画描边。 */
    private fun drawEntryCell(
        canvas: Canvas,
        cell: Cell,
    ) {
        val entry = cell.entry ?: return
        val bitmap: Bitmap? = prefetcher.bitmapOf(toFileItem(entry))
        paintFill.color = InkColors.PAPER
        canvas.drawRect(cell.image, paintFill)
        if (bitmap != null) {
            PaneDraw.cropped(canvas, bitmap, cell.image, paintInk)
        } else {
            canvas.drawRect(cell.image, paintStroke)
        }
        PaneDraw.lineCentered(
            canvas = canvas,
            text = PaneDraw.fit(entry.name, smallPaint, cell.image.width()),
            paint = smallPaint,
            left = cell.image.left.toFloat(),
            centerY = cell.label.centerY().toFloat(),
        )
    }

    private val detector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val cell = cellAt(e.x.toInt(), e.y.toInt()) ?: return false
                    val index = cell.indexInDay
                    if (index >= 0 && cell.entry != null) callbacks.onOpenPhoto(cell.day, index)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    val cell = cellAt(e.x.toInt(), e.y.toInt()) ?: return
                    val entry = cell.entry ?: return
                    callbacks.onLongPress(entry)
                }
            },
        )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (detector.onTouchEvent(event)) return true
        return super.onTouchEvent(event)
    }

    /** 命中测试，参数 x、y 为视图坐标，落在缩略图内返回对应单元格。 */
    private fun cellAt(
        x: Int,
        y: Int,
    ): Cell? = cells.firstOrNull { !it.header && it.image.contains(x, y) }

    /** 依据当前宽度与当前页分段计算全部单元格矩形。 */
    private fun buildCells(pageIndex: Int): List<Cell> {
        val blocks = pages.getOrNull(pageIndex)?.blocks.orEmpty()
        if (blocks.isEmpty()) return emptyList()
        val geometry = cellGeometry()
        val result = ArrayList<Cell>(AlbumPaging.MAX_CELLS_PER_PAGE + blocks.size)
        var top = tokens.dp(4f)
        for (block in blocks) {
            val day = days.getOrNull(block.dayIndex)
            if (day != null) {
                top = appendBlock(result, day, block, geometry, top)
            }
            if (top > height) break
        }
        return result
    }

    /** 页内几何尺寸，按当前宽度计算一次，供各段复用。 */
    private data class CellGeometry(
        val padH: Int,
        val gap: Int,
        val columnW: Int,
        val imageH: Int,
        val labelH: Int,
        val headerH: Int,
        val blockGap: Int,
    )

    /** 计算页内几何尺寸。 */
    private fun cellGeometry(): CellGeometry {
        val padH = tokens.padH
        val gap = tokens.gap
        val columnW = ((width - padH * 2 - gap * 2) / 3).coerceAtLeast(1)
        return CellGeometry(
            padH = padH,
            gap = gap,
            columnW = columnW,
            imageH = (columnW * 3) / 4,
            labelH = tokens.cellLabelH,
            headerH = tokens.dp(26f),
            blockGap = tokens.gapLarge,
        )
    }

    /** 追加一段日块：标题行加空态占位或缩略图，返回该段之后的纵向起点。 */
    private fun appendBlock(
        result: MutableList<Cell>,
        day: MediaDay,
        block: AlbumBlock,
        geometry: CellGeometry,
        top: Int,
    ): Int {
        val headerText = if (block.continued) "${day.title} · 续" else "${day.title} · 当日"
        val headerCell =
            Cell(
                header = true,
                day = day,
                image =
                    Rect(
                        geometry.padH,
                        top,
                        width - geometry.padH,
                        top + geometry.headerH,
                    ),
            )
        result +=
            headerCell.copy(
                headerText = headerText,
                countText = blockCountText(day, block),
            )
        val bodyTop = top + geometry.headerH
        if (block.itemRange.isEmpty()) {
            result +=
                Cell(
                    header = false,
                    day = day,
                    entry = null,
                    image = Rect(geometry.padH, bodyTop, width - geometry.padH, bodyTop + geometry.headerH),
                )
            return bodyTop + geometry.headerH
        }
        return appendEntries(result, day, block.itemRange, geometry, bodyTop)
    }

    /** 段头右侧的张数文字，续段标注当前区间与总数。 */
    private fun blockCountText(
        day: MediaDay,
        block: AlbumBlock,
    ): String =
        when {
            block.itemRange.isEmpty() -> "${day.count} 张"
            block.continued -> "第 ${block.itemRange.first + 1}–${block.itemRange.last + 1} 张 / 共 ${day.count} 张"
            else -> "${day.count} 张"
        }

    /** 追加一段内的缩略图单元格，返回该段之后的纵向起点。 */
    private fun appendEntries(
        result: MutableList<Cell>,
        day: MediaDay,
        range: IntRange,
        geometry: CellGeometry,
        top: Int,
    ): Int {
        var rows = 0
        for (index in range) {
            val entry = day.items.getOrNull(index) ?: continue
            val offset = index - range.first
            val row = offset / 3
            val column = offset % 3
            rows = maxOf(rows, row + 1)
            val left = geometry.padH + column * (geometry.columnW + geometry.gap)
            val cellTop = top + row * (geometry.imageH + geometry.labelH + geometry.gap)
            val image = Rect(left, cellTop, left + geometry.columnW, cellTop + geometry.imageH)
            val label =
                Rect(
                    left,
                    cellTop + geometry.imageH,
                    left + geometry.columnW,
                    cellTop + geometry.imageH + geometry.labelH,
                )
            result +=
                Cell(
                    header = false,
                    day = day,
                    entry = entry,
                    indexInDay = index,
                    image = image,
                    label = label,
                )
        }
        return top + rows * (geometry.imageH + geometry.labelH + geometry.gap) + geometry.blockGap
    }

    /** 条目转文件条目，供缩略图预取器使用。 */
    private fun toFileItem(entry: MediaEntry): FileItem =
        FileItem.file(
            name = entry.name,
            path = entry.path,
            size = 0L,
            mtime = entry.time,
            mime = entry.mime,
            width = entry.width,
            height = entry.height,
        )

    /** 单元格，header 为 true 时表示日块头，占用 image 矩形绘制日期与数量。 */
    private data class Cell(
        val header: Boolean,
        val day: MediaDay,
        val entry: MediaEntry? = null,
        val indexInDay: Int = -1,
        val image: Rect,
        val label: Rect = Rect(),
        val headerText: String = "",
        val countText: String = "",
    )

    private companion object {
        private const val LOADING_HINT = "正在建立索引…"
        private const val EMPTY_HINT = "相册里还没有照片"
    }
}
