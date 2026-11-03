package com.shuiyu.octoberrain.feature.files.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs

/**
 * 顶栏、底栏与内容区的几何与绘制。
 *
 * 与 [FilePager] 分开持有矩形与画笔，避免视图类职责过多；
 * 命中判断与绘制共用同一组矩形，保证触摸位置与视觉位置一致。
 * 顶栏包含返回按钮、标题、路径副标题与右侧动作按钮，底栏包含翻页按钮、页码与动作按钮。
 *
 * 参数 tokens 为文件页尺寸令牌。
 */
@Suppress("TooManyFunctions") // 顶栏、底栏与动作行的绘制和命中集中在同一几何职责内，按模块既有做法抑制函数数量告警。
internal class FilePagerChrome(
    private val tokens: FileTokens,
) {
    /** 内容区，位于顶栏与底栏之间。 */
    val contentRect = Rect()

    /** 底栏区域。 */
    val barRect = Rect()

    /** 正文画笔，供标题与按钮文案共用。 */
    val bodyPaint: Paint = textPaint(tokens.bodyText)

    /** 标题画笔，字号取 17sp 语义。 */
    val titlePaint: Paint = textPaint(tokens.titleText)

    /** 辅助画笔，字号取 12sp 语义。 */
    val smallPaint: Paint = textPaint(tokens.smallText)

    /** 分隔线画笔，线宽沿用桌面令牌。 */
    val linePaint: Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
            color = InkColors.INK
        }

    private val iconPaint: Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
            color = InkColors.INK
        }

    private val backRect = Rect()
    private val topActionRect = Rect()
    private val cancelRect = Rect()
    private val prevRect = Rect()
    private val nextRect = Rect()
    private val actionRect = Rect()

    /** 多选动作行整行区域与三格命中区域，仅多选态绘制与命中。 */
    private val selectionBar = Rect()
    private val selectionCells = arrayOf(Rect(), Rect(), Rect())

    /** 多选动作行底色，用于遮住下方可能溢出的内容。 */
    private val paperPaint: Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = InkColors.PAPER
        }

    private var topBarH = 0

    /** 按实测宽度重建内容区、顶栏与底栏命中矩形。 */
    fun layout(
        width: Int,
        metrics: FileMetrics,
    ) {
        topBarH = metrics.contentTop
        val backWidth = tokens.dp(44f)
        backRect.set(0, 0, backWidth, topBarH)
        topActionRect.set(width - tokens.padH - tokens.dp(60f), 0, width - tokens.padH, topBarH)
        val cancelTextWidth = bodyPaint.measureText(BrowseStrings.CANCEL).toInt()
        cancelButtonRect(topActionRect.left, topBarH, cancelTextWidth, tokens.padH).applyTo(cancelRect)
        contentRect.set(0, metrics.contentTop, width, metrics.contentBottom)
        // 底栏始终贴住视图底边；多选态底栏上方再让出一条较矮的批量动作行。
        val barTop = (metrics.heightPx - tokens.bottomBarH).coerceAtLeast(metrics.contentTop)
        barRect.set(0, barTop, width, metrics.heightPx)
        val rects = selectionBarRects(width, metrics.heightPx, tokens.bottomBarH, tokens.selectionBarH)
        rects.bar.applyTo(selectionBar)
        rects.cells.forEachIndexed { index, cell -> cell.applyTo(selectionCells[index]) }
        prevRect.set(
            tokens.padH,
            barRect.top,
            tokens.padH + tokens.navButtonWidth,
            barRect.bottom,
        )
        nextRect.set(
            width - tokens.padH - tokens.actionButtonWidth - tokens.navButtonWidth,
            barRect.top,
            width - tokens.padH - tokens.actionButtonWidth,
            barRect.bottom,
        )
        actionRect.set(
            width - tokens.padH - tokens.actionButtonWidth,
            barRect.top,
            width - tokens.padH,
            barRect.bottom,
        )
    }

    /**
     * 绘制顶栏。
     *
     * 参数 canvas 为画布；title 为目录名称；subtitle 为当前路径；
     * action 为右侧动作按钮文案，空串表示不绘制；cancel 为 true 时在动作按钮左侧绘制取消按钮。
     * 文本超出可用宽度时以省略号截断。
     */
    fun drawTitle(
        canvas: Canvas,
        title: String,
        subtitle: String,
        action: String,
        cancel: Boolean = false,
    ) {
        val iconSize = tokens.dp(18f).toFloat()
        InkGlyphs.draw(
            canvas = canvas,
            kind = InkGlyphs.Kind.BACK,
            cx = tokens.padH + iconSize / 2f,
            cy = topBarH / 2f,
            size = iconSize,
            paint = iconPaint,
        )
        val left = backRect.right.toFloat()
        val rightLimit = if (cancel) cancelRect.left else topActionRect.left - tokens.padH
        val right = rightLimit.coerceAtLeast(backRect.right + 1)
        val available = (right - left).toInt()
        PaneDraw.lineCentered(canvas, PaneDraw.fit(title, titlePaint, available), titlePaint, left, topBarH * 0.33f)
        if (subtitle.isNotEmpty()) {
            PaneDraw.lineCentered(
                canvas,
                PaneDraw.fit(subtitle, smallPaint, available),
                smallPaint,
                left,
                topBarH * 0.75f,
            )
        }
        if (cancel) {
            PaneDraw.rightCentered(canvas, BrowseStrings.CANCEL, bodyPaint, cancelRect)
        }
        if (action.isNotEmpty()) {
            PaneDraw.rightCentered(canvas, action, bodyPaint, topActionRect)
        }
    }

    /** 绘制底栏：分隔线、翻页按钮、页码与动作按钮。 */
    fun drawBottomBar(
        canvas: Canvas,
        state: FilePagerState,
        action: String,
    ) {
        canvas.drawLine(
            barRect.left.toFloat(),
            barRect.top.toFloat(),
            barRect.right.toFloat(),
            barRect.top.toFloat(),
            linePaint,
        )
        PaneDraw.centered(canvas, BrowseStrings.PREVIOUS, bodyPaint, prevRect)
        PaneDraw.centered(canvas, BrowseStrings.NEXT, bodyPaint, nextRect)
        val indicator = "${state.page + 1}/${state.pageCount}"
        val indicatorRect = Rect(prevRect.right, barRect.top, nextRect.left, barRect.bottom)
        PaneDraw.centered(canvas, indicator, smallPaint, indicatorRect)
        if (action.isNotEmpty()) PaneDraw.centered(canvas, action, bodyPaint, actionRect)
    }

    /** 判断坐标是否落在返回按钮内。 */
    fun containsBack(
        x: Int,
        y: Int,
    ): Boolean = backRect.contains(x, y)

    /** 判断坐标是否落在顶栏动作按钮内。 */
    fun containsTopAction(
        x: Int,
        y: Int,
    ): Boolean = topActionRect.contains(x, y)

    /** 判断坐标是否落在取消按钮内；取消按钮未布置时返回 false。 */
    fun containsCancel(
        x: Int,
        y: Int,
    ): Boolean = !cancelRect.isEmpty && cancelRect.contains(x, y)

    /** 判断坐标是否落在上一页按钮内。 */
    fun containsPrev(
        x: Int,
        y: Int,
    ): Boolean = prevRect.contains(x, y)

    /** 判断坐标是否落在下一页按钮内。 */
    fun containsNext(
        x: Int,
        y: Int,
    ): Boolean = nextRect.contains(x, y)

    /** 判断坐标是否落在动作按钮内。 */
    fun containsAction(
        x: Int,
        y: Int,
    ): Boolean = actionRect.contains(x, y)

    /** 判断坐标是否落在底栏内。 */
    fun containsBar(
        x: Int,
        y: Int,
    ): Boolean = barRect.contains(x, y)

    /**
     * 绘制多选动作行：复制到、移动到、删除三格，位于底栏正上方。
     *
     * 参数 canvas 为画布。三格文案统一使用正文字色，未选中条目时同样清晰可读。
     */
    fun drawSelectionBar(canvas: Canvas) {
        if (selectionBar.isEmpty) return
        canvas.drawRect(selectionBar, paperPaint)
        canvas.drawLine(
            selectionBar.left.toFloat(),
            selectionBar.top.toFloat(),
            selectionBar.right.toFloat(),
            selectionBar.top.toFloat(),
            linePaint,
        )
        for (index in 0 until selectionCells.lastIndex) {
            val right = selectionCells[index].right.toFloat()
            canvas.drawLine(right, selectionBar.top.toFloat(), right, selectionBar.bottom.toFloat(), linePaint)
        }
        PaneDraw.centered(canvas, BrowseStrings.COPY_TO, bodyPaint, selectionCells[0])
        PaneDraw.centered(canvas, BrowseStrings.MOVE_TO, bodyPaint, selectionCells[1])
        PaneDraw.centered(canvas, BrowseStrings.DELETE, bodyPaint, selectionCells[2])
    }

    private fun textPaint(sizePx: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            color = InkColors.INK
        }
}

/**
 * 多选动作行几何。
 *
 * 整行按宽度三等分，末尾余数归入删除格；行高为底栏的四分之三，位于底栏正上方。
 */
internal data class SelectionBarRects(
    val bar: FileGridRect,
    val cells: List<FileGridRect>,
)

/**
 * 计算多选动作行与三格的矩形。
 *
 * 参数 width 与 height 为视图宽高；bottomBarH 为底栏高度；
 * selectionBarH 为动作行高度，通常取底栏高度的四分之三。
 * 三格顺序为复制、移动、删除，纵坐标取底栏上方一行。
 */
internal fun selectionBarRects(
    width: Int,
    height: Int,
    bottomBarH: Int,
    selectionBarH: Int,
): SelectionBarRects {
    val safeWidth = width.coerceAtLeast(0)
    val bottom = (height - bottomBarH).coerceAtLeast(0)
    val top = (bottom - selectionBarH).coerceAtLeast(0)
    val third = safeWidth / 3
    val bar = FileGridRect(0, top, safeWidth, bottom)
    val cells =
        listOf(
            FileGridRect(0, top, third, bottom),
            FileGridRect(third, top, third * 2, bottom),
            FileGridRect(third * 2, top, safeWidth, bottom),
        )
    return SelectionBarRects(bar, cells)
}

/**
 * 计算顶栏取消按钮矩形。
 *
 * 参数 topActionLeft 为右侧动作按钮左边界；topBarH 为顶栏高度；
 * textWidth 为取消文案宽度；padding 为文字两侧留白。
 * 取消按钮紧贴动作按钮左侧，宽度为文案宽度加留白。
 */
internal fun cancelButtonRect(
    topActionLeft: Int,
    topBarH: Int,
    textWidth: Int,
    padding: Int,
): FileGridRect {
    val safeLeft = topActionLeft.coerceAtLeast(0)
    val width = (textWidth + padding).coerceAtLeast(0)
    val left = (safeLeft - width).coerceAtLeast(0)
    return FileGridRect(left, 0, safeLeft, topBarH.coerceAtLeast(0))
}

/**
 * 按坐标判断多选动作格。
 *
 * 参数 x 与 y 为触摸坐标；rects 为 [selectionBarRects] 的结果。
 * 返回 0 表示复制到，1 表示移动到，2 表示删除，未命中返回 -1。
 */
internal fun selectionCellAt(
    x: Int,
    y: Int,
    rects: SelectionBarRects,
): Int = rects.cells.indexOfFirst { it.contains(x, y) }

/** 将 [FileGridRect] 写入 Android 矩形，供绘制与命中复用同一组坐标。 */
private fun FileGridRect.applyTo(rect: Rect) {
    rect.set(left, top, right, bottom)
}
