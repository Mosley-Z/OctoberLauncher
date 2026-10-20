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
    private val prevRect = Rect()
    private val nextRect = Rect()
    private val actionRect = Rect()

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
        contentRect.set(0, metrics.contentTop, width, metrics.contentBottom)
        barRect.set(0, metrics.contentBottom, width, metrics.contentBottom + tokens.bottomBarH)
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
     * action 为右侧动作按钮文案，空串表示不绘制。文本超出可用宽度时以省略号截断。
     */
    fun drawTitle(
        canvas: Canvas,
        title: String,
        subtitle: String,
        action: String,
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
        val right = (topActionRect.left - tokens.padH).coerceAtLeast(backRect.right + 1)
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

    private fun textPaint(sizePx: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            color = InkColors.INK
        }
}
