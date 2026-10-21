package com.shuiyu.octoberrain.feature.widgets

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * 矩形区域，坐标为组件像素坐标。
 *
 * 与绘制框架的矩形类型区分，避免纯几何计算依赖 Android 类型，便于单元测试。
 *
 * @property left 左边界
 * @property top 上边界
 * @property right 右边界
 * @property bottom 下边界
 */
internal data class BoardBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** 区域宽度。 */
    val width: Float
        get() = right - left

    /** 区域高度。 */
    val height: Float
        get() = bottom - top

    /** 水平中心。 */
    val centerX: Float
        get() = (left + right) / 2f

    /** 垂直中心。 */
    val centerY: Float
        get() = (top + bottom) / 2f

    /**
     * 判断坐标是否落在区域内。
     *
     * @param x 横向坐标
     * @param y 纵向坐标
     * @return 是否命中
     */
    fun contains(
        x: Float,
        y: Float,
    ): Boolean = x >= left && x <= right && y >= top && y <= bottom
}

/**
 * 杂志版式的区域与字号，全部按组件像素尺寸推导。
 *
 * 布局按纵向分带：顶部时间与日期、右上划线笔记、中部叠放封面、中央下方书名、
 * 进度条、底部阅读信息。各带位置由组件高度推导，字号由宽度与高度共同约束，
 * 组件尺寸变化时维持同一比例关系。
 */
internal data class WereadBoardFrame(
    /** 四周边距。 */
    val inset: Float,
    /** 时间文字基线。 */
    val timeBaseline: Float,
    /** 时间字号。 */
    val timeSize: Float,
    /** 日期文字顶边。 */
    val dateTop: Float,
    /** 日期字号。 */
    val dateSize: Float,
    /** 划线笔记区域，供排版与高度估算。 */
    val noteBounds: BoardBounds,
    /** 笔记正文字号。 */
    val noteSize: Float,
    /** 笔记行高。 */
    val noteLineHeight: Float,
    /** 笔记正文最多行数。 */
    val noteMaxLines: Int,
    /** 封面区域，用于判断手势起点是否落在封面带内。 */
    val coverArea: BoardBounds,
    /** 封面横向中心。 */
    val coverCenterX: Float,
    /** 封面纵向中心。 */
    val coverCenterY: Float,
    /** 中央封面宽度。 */
    val coverWidth: Float,
    /** 中央封面高度。 */
    val coverHeight: Float,
    /** 书名文字顶边。 */
    val titleTop: Float,
    /** 书名字号。 */
    val titleSize: Float,
    /** 进度条区域。 */
    val barBounds: BoardBounds,
    /** 底部信息行底边。 */
    val infoBottom: Float,
    /** 底部信息字号。 */
    val infoSize: Float,
) {
    companion object {
        /** 书封的默认高宽比，实际绘制按位图比例在给定框内适配。 */
        const val DEFAULT_COVER_ASPECT: Float = 1.45f

        /** 五层封面完整展开所需的横向宽度倍数。 */
        const val TOTAL_SPAN: Float = 2.24f

        /** 封面高度相对封面带宽度的比例，两侧层叠后仍完整落在带内。 */
        const val COVER_HEIGHT_RATIO: Float = 0.88f

        /** 单本候选时的展开倍数，中部只留中央封面。 */
        private const val SPAN_BASE: Float = 1.06f

        /** 每增加一层侧边封面追加的展开倍数。 */
        private const val SPAN_STEP: Float = 0.42f

        /** 笔记行数由组件高度自动决定。 */
        const val AUTO_NOTE_LINES: Int = -1

        /** 时间字号相对内部宽度的比例。 */
        const val TIME_WIDTH_RATIO: Float = 0.095f

        /** 时间字号相对内部高度的比例，用于矮卡片压缩。 */
        const val TIME_HEIGHT_RATIO: Float = 0.22f

        /** 时间与日期的字号比例。 */
        const val DATE_RATIO: Float = 0.26f

        /** 笔记区域相对内部宽度的比例。 */
        const val NOTE_WIDTH_RATIO: Float = 0.50f

        /** 笔记字号相对日期字号的比例。 */
        const val NOTE_DATE_RATIO: Float = 1.0f

        /** 底部信息字号相对内部宽度的比例。 */
        const val INFO_WIDTH_RATIO: Float = 0.028f

        /** 底部信息行的放大倍数，只作用于阅读进度、时长、进度条与三个按钮。 */
        const val INFO_TEXT_SCALE: Float = 1.1f

        /** 书名字号相对内部宽度的比例。 */
        const val TITLE_WIDTH_RATIO: Float = 0.062f

        /** 进度条宽度相对内部宽度的比例。 */
        const val BAR_WIDTH_RATIO: Float = 0.26f

        /**
         * 计算封面带的上边界、下边界与中央封面宽度。
         *
         * 顶部时间与笔记、下方书名与页脚之间的剩余高度决定封面宽度，
         * 高度不足时封面带退化为零高度，保证各区域互不重叠。
         *
         * @param inset 四周边距
         * @param innerW 内部宽度
         * @param innerH 内部高度
         * @param headerBottom 顶部区域下边界
         * @param noteBandHeight 笔记区域高度
         * @param titleTop 书名区域上边界
         * @param span 叠放展开倍数
         * @return 封面带的边界与宽度
         */
        private fun coverBandOf(
            inset: Float,
            innerW: Float,
            innerH: Float,
            headerBottom: Float,
            noteBandHeight: Float,
            titleTop: Float,
            span: Float,
        ): CoverBand {
            val gap = (innerH * BAND_GAP_RATIO).coerceAtLeast(MIN_BAND_GAP)
            val headerLimit = max(headerBottom, inset + noteBandHeight)
            val top = min(headerLimit + gap, titleTop)
            val height = (titleTop - top).coerceAtLeast(0f)
            val coverHeight = height * COVER_HEIGHT_RATIO
            val width = min(innerW / span, coverHeight / DEFAULT_COVER_ASPECT).coerceAtLeast(0f)
            return CoverBand(BoardBounds(inset, top, innerW + inset, top + height), width)
        }

        /**
         * 按候选书籍数量推导叠放展开倍数。
         *
         * 只有一本时中央封面独占中部，两到三本各留一层侧边，四本以上按五层展开，
         * 使封面随可见层数增大，候选较少时中部不留过多空位。
         *
         * @param count 候选书籍数量
         * @return 横向展开倍数
         */
        fun spanFor(count: Int): Float {
            val visible = (count - 1).coerceIn(0, 2)
            return (SPAN_BASE + visible * SPAN_STEP).coerceAtMost(TOTAL_SPAN)
        }

        /**
         * 计算划线笔记占用的行数。
         *
         * @param noteLineCount 调用方给定的行数，取 [AUTO_NOTE_LINES] 时按组件高度自动决定
         * @param innerH 内部高度
         * @return 实际行数，零表示不预留笔记带
         */
        private fun noteLinesOf(
            noteLineCount: Int,
            innerH: Float,
        ): Int =
            if (noteLineCount == AUTO_NOTE_LINES) {
                if (innerH < COMPACT_HEIGHT) COMPACT_NOTE_LINES else NOTE_LINES
            } else {
                noteLineCount.coerceIn(0, NOTE_LINES)
            }

        /**
         * 按组件像素尺寸推导版式。
         *
         * @param width 组件宽度，单位像素
         * @param height 组件高度，单位像素
         * @param inset 四周边距，单位像素
         * @param span 叠放展开倍数，默认为五层完整展开
         * @param noteLineCount 笔记行数，取 [AUTO_NOTE_LINES] 时按组件高度决定
         * @return 版式参数
         */
        fun of(
            width: Float,
            height: Float,
            inset: Float,
            span: Float = TOTAL_SPAN,
            noteLineCount: Int = AUTO_NOTE_LINES,
        ): WereadBoardFrame {
            val innerW = (width - inset * 2f).coerceAtLeast(MIN_INNER)
            val innerH = (height - inset * 2f).coerceAtLeast(MIN_INNER)
            val timeSize = min(innerW * TIME_WIDTH_RATIO, innerH * TIME_HEIGHT_RATIO).coerceAtLeast(MIN_TIME)
            val dateSize = (timeSize * DATE_RATIO).coerceAtLeast(MIN_TEXT)
            val noteSize = (dateSize * NOTE_DATE_RATIO).coerceIn(MIN_TEXT, MAX_NOTE)
            val infoSize = (innerW * INFO_WIDTH_RATIO * INFO_TEXT_SCALE).coerceIn(MIN_TEXT, MAX_INFO)
            val titleSize = (innerW * TITLE_WIDTH_RATIO).coerceIn(MIN_TITLE, MAX_TITLE)
            val timeBaseline = inset + timeSize * BASELINE_RATIO
            val dateTop = timeBaseline + dateSize * DATE_GAP_RATIO
            val headerBottom = dateTop + dateSize * DATE_HEIGHT_RATIO
            val noteWidth = innerW * NOTE_WIDTH_RATIO
            val noteLineHeight = noteSize * NOTE_LINE_RATIO
            // 无笔记时不预留笔记带高度，让封面带向上扩张；-1 表示按组件高度自动取行数。
            val noteLines = noteLinesOf(noteLineCount, innerH)
            val noteBandHeight = if (noteLines == 0) 0f else noteLineHeight * (noteLines + NOTE_SOURCE_LINES)
            val infoBottom = height - inset
            val openHeight = (infoSize * OPEN_HEIGHT_RATIO).coerceAtLeast(MIN_OPEN)
            val barHeight =
                (innerW * BAR_HEIGHT_RATIO * INFO_TEXT_SCALE)
                    .coerceIn(MIN_BAR * INFO_TEXT_SCALE, MAX_BAR * INFO_TEXT_SCALE)
            val barWidth = innerW * BAR_WIDTH_RATIO
            val barTop = infoBottom - openHeight - infoSize * INFO_GAP_RATIO - barHeight
            val barBounds =
                BoardBounds(
                    left = width / 2f - barWidth / 2f,
                    top = barTop,
                    right = width / 2f + barWidth / 2f,
                    bottom = barTop + barHeight,
                )
            val titleTop = barTop - infoSize * TITLE_GAP_RATIO - titleSize * TITLE_HEIGHT_RATIO
            val band = coverBandOf(inset, innerW, innerH, headerBottom, noteBandHeight, titleTop, span)
            return WereadBoardFrame(
                inset = inset,
                timeBaseline = timeBaseline,
                timeSize = timeSize,
                dateTop = dateTop,
                dateSize = dateSize,
                noteBounds =
                    BoardBounds(
                        left = width - inset - noteWidth,
                        top = inset,
                        right = width - inset,
                        bottom = inset + noteLineHeight * noteLines,
                    ),
                noteSize = noteSize,
                noteLineHeight = noteLineHeight,
                noteMaxLines = noteLines,
                coverArea = BoardBounds(inset, band.area.top, width - inset, band.area.bottom),
                coverCenterX = width / 2f,
                coverCenterY = band.area.centerY,
                coverWidth = band.width,
                coverHeight = band.width * DEFAULT_COVER_ASPECT,
                titleTop = titleTop,
                titleSize = titleSize,
                barBounds = barBounds,
                infoBottom = infoBottom,
                infoSize = infoSize,
            )
        }

        /** 内部区域的最小边长，避免极端尺寸出现负值。 */
        private const val MIN_INNER: Float = 32f

        /** 时间最小字号。 */
        private const val MIN_TIME: Float = 16f

        /** 正文最小字号。 */
        private const val MIN_TEXT: Float = 9f

        /** 书名字号下限。 */
        private const val MIN_TITLE: Float = 16f

        /** 书名字号上限。 */
        private const val MAX_TITLE: Float = 96f

        /** 笔记字号上限。 */
        private const val MAX_NOTE: Float = 34f

        /** 底部信息字号上限。 */
        private const val MAX_INFO: Float = 42f

        /** 时间基线相对字号的偏移比例。 */
        private const val BASELINE_RATIO: Float = 0.80f

        /** 日期与时间基线的间距比例。 */
        private const val DATE_GAP_RATIO: Float = 0.55f

        /** 日期行高比例。 */
        private const val DATE_HEIGHT_RATIO: Float = 1.15f

        /** 笔记行高相对字号的倍数。 */
        private const val NOTE_LINE_RATIO: Float = 1.55f

        /** 笔记正文最多占用的行数，字号不随文本长度变化。 */
        private const val NOTE_LINES: Int = 2

        /** 紧凑尺寸下的笔记行数。 */
        private const val COMPACT_NOTE_LINES: Int = 2

        /** 笔记来源占用的附加行数，含行距与来源文字本身。 */
        private const val NOTE_SOURCE_LINES: Float = 1.9f

        /** 紧凑尺寸的高度阈值。 */
        private const val COMPACT_HEIGHT: Float = 300f

        /** 打开按钮高度相对底部信息字号的倍数。 */
        private const val OPEN_HEIGHT_RATIO: Float = 1.90f

        /** 打开按钮最小高度。 */
        private const val MIN_OPEN: Float = 24f

        /** 进度条高度上限。 */
        private const val MAX_BAR: Float = 4f

        /** 进度条最小高度。 */
        private const val MIN_BAR: Float = 2f

        /** 进度条高度相对内部宽度的比例。 */
        private const val BAR_HEIGHT_RATIO: Float = 0.0040f

        /** 进度条与底部信息行的间距比例。 */
        private const val INFO_GAP_RATIO: Float = 0.85f

        /** 书名与信息行的间距比例。 */
        private const val TITLE_GAP_RATIO: Float = 0.55f

        /** 书名行高相对字号的倍数。 */
        private const val TITLE_HEIGHT_RATIO: Float = 1.35f

        /** 纵向分带间距相对内部高度的比例。 */
        private const val BAND_GAP_RATIO: Float = 0.025f

        /** 纵向分带最小间距。 */
        private const val MIN_BAND_GAP: Float = 6f
    }
}

/**
 * 计算单层封面的绘制区域。
 *
 * 水平位置由连续位置决定，缩放按层数递减，纵向统一以封面带中心对齐，
 * 使中央封面上下略高于两侧。
 *
 * @param frame 版式参数
 * @param slot 封面层
 * @return 封面绘制区域
 */
internal fun coverBoundsOf(
    frame: WereadBoardFrame,
    slot: WereadCoverSlot,
): BoardBounds {
    val scale = WereadBoardCarousel.scaleAt(slot.position)
    val width = frame.coverWidth * scale
    val height = frame.coverHeight * scale
    val shift = WereadBoardCarousel.offsetAt(slot.position) * frame.coverWidth * slot.position.sign
    val centerX = frame.coverCenterX + shift
    return BoardBounds(
        left = centerX - width / 2f,
        top = frame.coverCenterY - height / 2f,
        right = centerX + width / 2f,
        bottom = frame.coverCenterY + height / 2f,
    )
}

/** 封面带的纵向范围与中央封面宽度。 */
private class CoverBand(
    val area: BoardBounds,
    val width: Float,
)

/**
 * 底部右侧按钮的几何。
 *
 * 版式与刷新按钮固定在右下角，阅读信息带向左让出空间，保证两者不重叠。
 *
 * @property layoutButton 版式切换按钮区域
 * @property refreshButton 刷新按钮区域
 * @property infoRight 阅读信息可用的右边界
 */
internal data class WereadBoardButtons(
    val layoutButton: BoardBounds,
    val refreshButton: BoardBounds,
    val infoRight: Float,
)

/**
 * 推导底部按钮与阅读信息的水平分区。
 *
 * 按钮自右向左依次排列为刷新与版式切换，高度与打开按钮一致；
 * 内部宽度不足时按钮整体向左收拢，信息带据此截断。
 *
 * @param width 组件宽度
 * @param inset 四周边距
 * @param infoBottom 底部信息行底边
 * @param height 按钮高度
 * @param layoutWidth 版式切换按钮宽度
 * @param refreshWidth 刷新按钮宽度
 * @param gap 按钮之间的间距
 * @return 底部按钮与信息带的几何
 */
internal fun footerButtonsOf(
    width: Float,
    inset: Float,
    infoBottom: Float,
    height: Float,
    layoutWidth: Float,
    refreshWidth: Float,
    gap: Float,
): WereadBoardButtons {
    val right = (width - inset).coerceAtLeast(inset)
    val refresh = BoardBounds(right - refreshWidth, infoBottom - height, right, infoBottom)
    val layoutLeft = (refresh.left - gap - layoutWidth).coerceAtLeast(inset)
    val layoutButton = BoardBounds(layoutLeft, infoBottom - height, layoutLeft + layoutWidth, infoBottom)
    return WereadBoardButtons(layoutButton, refresh, (layoutButton.left - gap).coerceAtLeast(inset))
}
