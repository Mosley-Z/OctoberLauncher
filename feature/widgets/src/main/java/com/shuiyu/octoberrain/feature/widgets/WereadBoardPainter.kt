package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors

/**
 * 单帧绘制所需的数据。
 *
 * @property books 轮播书目
 * @property slots 需要绘制的封面层，已按由远到近排序
 * @property selectedIndex 当前选中位置
 * @property note 当前书目的划线正文，空串表示无笔记
 * @property clock 时间文案
 * @property date 日期文案
 * @property cover 按书目编号取封面位图的函数
 */
internal class WereadBoardScene(
    val books: List<WereadBookCard>,
    val slots: List<WereadCoverSlot>,
    val selectedIndex: Int,
    val note: String,
    val clock: String,
    val date: String,
    val cover: (String) -> Bitmap?,
) {
    /** 当前选中的书目，索引越界时返回空。 */
    val current: WereadBookCard?
        get() = books.getOrNull(selectedIndex)
}

/**
 * 杂志版式的绘制实现。
 *
 * 组件只负责状态与手势，全部绘制集中在本类：白底、左上时间日期、右上划线笔记、
 * 中部叠放封面、中央下方书名、进度条与底部阅读信息。绘制过程不发起请求、
 * 不修改状态，仅读取传入的场景数据。
 */
internal class WereadBoardPainter {
    /** 通用画笔，绘制前按用途改写字号与颜色。 */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 位图画笔，按需开启双线性过滤。 */
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** 衬线字体，用于时间、书名与笔记。 */
    private val serif: Typeface = DesignRuntime.typeface(SERIF_ID)

    /** 粗衬线字体，用于大号时间。 */
    private val serifBold: Typeface = Typeface.create(serif, Typeface.BOLD)

    /** 无衬线字体，用于日期与底部信息。 */
    private val sans: Typeface = DesignRuntime.typeface()

    /** 临时矩形，避免每帧分配。 */
    private val rect = RectF()

    /**
     * 绘制整帧内容。
     *
     * @param canvas 目标画布，坐标原点为组件左上角
     * @param scene 本帧数据
     * @param frame 版式参数
     * @return 底部三个按钮的绘制区域，供点击命中使用
     */
    fun draw(
        canvas: Canvas,
        scene: WereadBoardScene,
        frame: WereadBoardFrame,
        scale: Float,
    ): WereadBoardChrome {
        canvas.drawColor(InkColors.PAPER)
        scene.slots.forEach { drawCover(canvas, scene, frame, it, scale) }
        drawClock(canvas, scene, frame)
        drawNote(canvas, scene, frame)
        drawTitle(canvas, scene, frame)
        return drawFooter(canvas, scene, frame)
    }

    /**
     * 绘制单层封面，按位图比例在给定框内适配，保持真实比例。
     *
     * 位图缺失时绘制细边框与书名占位，不使用圆角与阴影。
     *
     * @param canvas 目标画布
     * @param scene 本帧数据
     * @param frame 版式参数
     * @param slot 封面层
     */
    private fun drawCover(
        canvas: Canvas,
        scene: WereadBoardScene,
        frame: WereadBoardFrame,
        slot: WereadCoverSlot,
        scale: Float,
    ) {
        val book = scene.books.getOrNull(slot.index) ?: return
        val bounds = coverBoundsOf(frame, slot).takeIf { it.width > 0f && it.height > 0f } ?: return
        val bitmap = scene.cover(book.bookId)?.takeIf { !it.isRecycled }
        if (bitmap == null) {
            drawCoverPlaceholder(canvas, book, bounds)
        } else {
            val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
            var width = bounds.width
            var height = width / aspect
            if (height > bounds.height) {
                height = bounds.height
                width = height * aspect
            }
            rect.set(
                bounds.centerX - width / 2f,
                bounds.centerY - height / 2f,
                bounds.centerX + width / 2f,
                bounds.centerY + height / 2f,
            )
            canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
            drawCoverBorder(canvas, scale)
        }
    }

    /**
     * 在封面矩形内缘描一圈细边框。
     *
     * 描边宽度按组件缩放取不小于 1 像素的值，落在封面矩形内侧，不影响位图裁切。
     *
     * @param canvas 目标画布
     * @param scale 组件缩放倍数
     */
    private fun drawCoverBorder(
        canvas: Canvas,
        scale: Float,
    ) {
        val stroke = maxOf(MIN_BORDER_STROKE, scale)
        val inset = stroke / 2f
        rect.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = InkColors.INK_2
        canvas.drawRect(rect, paint)
        paint.style = Paint.Style.FILL
    }

    /**
     * 绘制封面占位：白底、细边框与居中的书名。
     *
     * @param canvas 目标画布
     * @param book 书目
     * @param bounds 封面区域
     */
    private fun drawCoverPlaceholder(
        canvas: Canvas,
        book: WereadBookCard,
        bounds: BoardBounds,
    ) {
        rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
        paint.style = Paint.Style.FILL
        paint.color = InkColors.PAPER
        canvas.drawRect(rect, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = HAIRLINE
        paint.color = InkColors.INK_3
        canvas.drawRect(rect, paint)
        paint.style = Paint.Style.FILL
        val size = (bounds.width * PLACEHOLDER_TEXT_RATIO).coerceIn(MIN_PLACEHOLDER_TEXT, MAX_PLACEHOLDER_TEXT)
        paint.typeface = serif
        paint.color = InkColors.INK_2
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        val lines = InkDraw.wrap(book.title, paint, bounds.width * PLACEHOLDER_PADDING_RATIO, PLACEHOLDER_LINES)
        val lineHeight = InkDraw.lineHeight(paint)
        val startTop = bounds.centerY - lineHeight * lines.size / 2f
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, bounds.centerX, startTop + lineHeight * index - paint.fontMetrics.ascent, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    /**
     * 绘制左上角时间与日期。
     *
     * @param canvas 目标画布
     * @param scene 本帧数据
     * @param frame 版式参数
     */
    private fun drawClock(
        canvas: Canvas,
        scene: WereadBoardScene,
        frame: WereadBoardFrame,
    ) {
        paint.typeface = serifBold
        paint.color = InkColors.INK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = frame.timeSize
        canvas.drawText(scene.clock, frame.inset, frame.timeBaseline, paint)
        paint.typeface = sans
        paint.color = InkColors.INK_2
        paint.textSize = frame.dateSize
        InkDraw.text(canvas, scene.date, frame.inset, frame.dateTop, paint)
    }

    /**
     * 绘制右上角划线笔记与来源；正文为空时整块隐藏。
     *
     * @param canvas 目标画布
     * @param scene 本帧数据
     * @param frame 版式参数
     */
    private fun drawNote(
        canvas: Canvas,
        scene: WereadBoardScene,
        frame: WereadBoardFrame,
    ) {
        val book = scene.current?.takeIf { scene.note.isNotEmpty() } ?: return
        val bounds = frame.noteBounds
        paint.typeface = serif
        paint.color = InkColors.INK
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = frame.noteSize
        val lines = clipNoteLines(scene.note, paint, bounds.width, frame.noteMaxLines)
        val lineHeight = frame.noteLineHeight
        lines.forEachIndexed { index, line ->
            val top = bounds.top + lineHeight * index
            canvas.drawText(line, bounds.centerX, top - paint.fontMetrics.ascent, paint)
        }
        val sourceTop = bounds.top + lineHeight * (lines.size + NOTE_SOURCE_GAP_RATIO)
        paint.typeface = sans
        paint.color = InkColors.INK_2
        paint.textSize = frame.noteSize * NOTE_SOURCE_RATIO
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(noteSourceText(book.title), bounds.right, sourceTop - paint.fontMetrics.ascent, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    /**
     * 绘制中央封面下方的书名，超长时缩小字号并截断。
     *
     * @param canvas 目标画布
     * @param scene 本帧数据
     * @param frame 版式参数
     */
    private fun drawTitle(
        canvas: Canvas,
        scene: WereadBoardScene,
        frame: WereadBoardFrame,
    ) {
        val book = scene.current ?: return
        paint.typeface = serif
        paint.color = InkColors.INK
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = frame.titleSize
        val maxWidth = frame.coverArea.width * TITLE_MAX_RATIO
        val size = InkDraw.fitSize(book.title, paint, maxWidth, frame.titleSize, frame.titleSize * TITLE_MIN_RATIO)
        paint.textSize = size
        val text = InkDraw.ellipsize(book.title, paint, maxWidth)
        canvas.drawText(text, frame.coverCenterX, frame.titleTop - paint.fontMetrics.ascent, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    /**
     * 绘制进度条、阅读信息与底部按钮。
     *
     * 阅读信息与打开按钮组成居中的信息带，右侧固定形态切换与刷新按钮；
     * 宽度不足时信息文字截断，按钮保持完整。
     *
     * @param canvas 目标画布
     * @param scene 本帧数据
     * @param frame 版式参数
     * @return 底部三个按钮区域
     */
    private fun drawFooter(
        canvas: Canvas,
        scene: WereadBoardScene,
        frame: WereadBoardFrame,
    ): WereadBoardChrome {
        val book = scene.current
        drawProgress(canvas, frame, book?.progressPercent ?: 0)
        val actions = actionsOf(paint, frame, frame.coverCenterX * 2f)
        paint.textSize = actions.textSize
        val openWidth = paint.measureText(OPEN_LABEL) + actions.padding * 2f
        val open = drawInfoBand(canvas, frame, actions, book, openWidth)
        drawActions(canvas, paint, actions)
        return WereadBoardChrome(open, actions.layoutButton, actions.refreshButton)
    }

    /**
     * 绘制居中的阅读信息与打开按钮。
     *
     * @param canvas 目标画布
     * @param frame 版式参数
     * @param actions 右下角动作按钮的几何与排版参数
     * @param book 当前书目，缺失时按零进度绘制
     * @param openWidth 打开按钮宽度
     * @return 打开按钮区域
     */
    private fun drawInfoBand(
        canvas: Canvas,
        frame: WereadBoardFrame,
        actions: WereadBoardActions,
        book: WereadBookCard?,
        openWidth: Float,
    ): BoardBounds {
        paint.typeface = sans
        paint.color = InkColors.INK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = frame.infoSize
        val infoGap = frame.infoSize * INFO_BUTTON_GAP_RATIO
        val available = (actions.layoutButton.left - infoGap - frame.inset).coerceAtLeast(0f)
        val maxInfo = (available - infoGap - openWidth).coerceAtLeast(0f)
        val percent = book?.progressPercent ?: 0
        val info = if (maxInfo > 0f) readingInfoText(percent, book?.readingSeconds ?: 0) else ""
        val infoText = if (info.isEmpty()) "" else InkDraw.ellipsize(info, paint, maxInfo)
        val infoWidth = paint.measureText(infoText)
        val bandWidth = infoWidth + infoGap + openWidth
        val startX = frame.inset + ((available - bandWidth) / 2f).coerceAtLeast(0f)
        val rowCenterY = frame.infoBottom - actions.height / 2f
        val baseline = rowCenterY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        if (infoText.isNotEmpty()) canvas.drawText(infoText, startX, baseline, paint)
        val openLeft = startX + infoWidth + infoGap
        val open = BoardBounds(openLeft, frame.infoBottom - actions.height, openLeft + openWidth, frame.infoBottom)
        drawChromeButton(canvas, paint, open, OPEN_LABEL, actions.textSize)
        return open
    }

    /**
     * 绘制进度条：浅灰轨道加黑色已读段，直角细条。
     *
     * @param canvas 目标画布
     * @param frame 版式参数
     * @param percent 阅读进度百分数
     */
    private fun drawProgress(
        canvas: Canvas,
        frame: WereadBoardFrame,
        percent: Int,
    ) {
        val bounds = frame.barBounds
        paint.style = Paint.Style.FILL
        paint.color = InkColors.INK_4
        rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
        canvas.drawRect(rect, paint)
        val ratio = percent.coerceIn(0, PERCENT_MAX) / PERCENT_MAX.toFloat()
        if (ratio <= 0f) return
        paint.color = InkColors.INK
        rect.set(bounds.left, bounds.top, bounds.left + bounds.width * ratio, bounds.bottom)
        canvas.drawRect(rect, paint)
    }

    /** 衬线字体的字体标识。 */
    companion object {
        /** 衬线字体标识，与设计令牌一致。 */
        const val SERIF_ID: String = "serif"

        /** 打开按钮文案。 */
        const val OPEN_LABEL: String = "打开"

        /** 百分数上限。 */
        const val PERCENT_MAX: Int = 100

        /** 细线宽度。 */
        const val HAIRLINE: Float = 0.8f

        /** 占位封面文字相对封面宽度的比例。 */
        const val PLACEHOLDER_TEXT_RATIO: Float = 0.075f

        /** 占位封面文字最小字号。 */
        const val MIN_PLACEHOLDER_TEXT: Float = 7f

        /** 占位封面文字最大字号。 */
        const val MAX_PLACEHOLDER_TEXT: Float = 18f

        /** 占位封面折行宽度比例。 */
        const val PLACEHOLDER_PADDING_RATIO: Float = 0.82f

        /** 占位封面最多行数。 */
        const val PLACEHOLDER_LINES: Int = 3

        /** 行列截断时使用的省略号。 */
        const val ELLIPSIS: String = "…"

        /** 笔记来源与正文的行距比例。 */
        const val NOTE_SOURCE_GAP_RATIO: Float = 0.85f

        /** 笔记来源字号相对正文字号的比例。 */
        const val NOTE_SOURCE_RATIO: Float = 0.84f

        /** 书名可用宽度相对封面区域宽度的比例。 */
        const val TITLE_MAX_RATIO: Float = 0.86f

        /** 书名缩小字号的下限比例。 */
        const val TITLE_MIN_RATIO: Float = 0.80f

        /** 信息文字与按钮之间的间距比例。 */
        const val INFO_BUTTON_GAP_RATIO: Float = 1.20f

        /** 封面边框的最小描边宽度。 */
        const val MIN_BORDER_STROKE: Float = 1f
    }
}
