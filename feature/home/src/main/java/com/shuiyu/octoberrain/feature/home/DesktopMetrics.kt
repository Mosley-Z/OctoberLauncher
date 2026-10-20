package com.shuiyu.octoberrain.feature.home

import android.graphics.RectF
import com.shuiyu.octoberrain.core.common.DesktopKind
import com.shuiyu.octoberrain.core.common.GridAlign
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.device.DeviceRuntime

/**
 * 桌面版面度量。
 *
 * 负责把设备画像、设计令牌与桌面配置换算成像素几何：
 * 可用区域、列数、行数、单格尺寸、图标尺寸、组件槽高度、行距与分页索引。
 * 所有数值均由令牌与设备信息计算，不写死像素；构造后不可变。
 *
 * 参数说明：
 * - isList 为是否为列表形态；
 * - columns 为宫格列数，列表形态固定按一列处理；
 * - fullRows 为普通页的应用行数；
 * - firstRows 为首页应用行数（首页启用组件时被压缩）；
 * - widgetHeight 为首页组件槽高度，单位为像素；
 * - hasWidget 表示当前是否放置组件；
 * - pageLimit 为页数上限；
 * - left/right/top/bottom 为版面边界（bottom 已扣除页码指示点区域）；
 * - gap 为设计令牌给出的常规间距，用于图标内边距；
 * - widgetGap 为组件槽下沿到应用区首行的间距，按 30/480 与 28/480 的上游比例换算；
 * - columnGap 为宫格列间距，来自桌面配置覆盖或令牌；
 * - contentOffsetX 为宫格整体横向偏移，由对齐方式决定；
 * - cellWidth/cellHeight 为宫格单元尺寸，rowHeight 为列表行高；
 * - iconSize 为图标外框边长；
 * - labelHeight 为标签占位高度，showLabels 表示是否显示标签；
 * - firstRowGap/fullRowGap 为首页与普通页的行间距，余量已均分到行间；
 * - firstOffset/fullOffset 为首页与普通页应用区域的顶部偏移，用于让剩余空间上下对称。
 */
internal class DesktopMetrics(
    val isList: Boolean,
    val columns: Int,
    val fullRows: Int,
    val firstRows: Int,
    val widgetHeight: Int,
    val hasWidget: Boolean,
    val pageLimit: Int,
    val left: Int,
    val right: Int,
    val top: Int,
    val bottom: Int,
    val gap: Int,
    val widgetGap: Int,
    val columnGap: Int,
    val contentOffsetX: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val rowHeight: Int,
    val iconSize: Int,
    val labelHeight: Int,
    val showLabels: Boolean,
    val firstRowGap: Int,
    val fullRowGap: Int,
    val firstOffset: Int,
    val fullOffset: Int,
) {
    /** 每页列数：列表形态只有一列。 */
    private val columnsPerPage: Int get() = if (isList) 1 else columns

    /** 首页组件槽矩形，无组件时返回空矩形。 */
    fun widgetRect(): RectF =
        if (hasWidget) {
            RectF(left.toFloat(), top.toFloat(), right.toFloat(), (top + widgetHeight).toFloat())
        } else {
            RectF()
        }

    /** 指定页面的应用行数。 */
    fun rowsOf(page: Int): Int = if (page == 0 && hasWidget) firstRows else fullRows

    /** 指定页面可容纳的入口数量。 */
    fun capacity(page: Int): Int = columnsPerPage * rowsOf(page)

    /** 指定页面第一个入口在整体列表中的下标。 */
    fun startIndex(page: Int): Int {
        if (page <= 0) return 0
        return capacity(0) + (page - 1) * (columnsPerPage * fullRows)
    }

    /** 入口下标所在页码。 */
    fun pageOfIndex(index: Int): Int {
        val firstCapacity = capacity(0)
        if (index < firstCapacity) return 0
        return 1 + (index - firstCapacity) / (columnsPerPage * fullRows)
    }

    /** 依据入口总数计算页数，结果受 pageLimit 限制。 */
    fun pageCount(entryCount: Int): Int {
        if (entryCount <= 0) return 1
        val firstCapacity = capacity(0)
        if (entryCount <= firstCapacity) return 1
        val perPage = columnsPerPage * fullRows
        val pages = 1 + (entryCount - firstCapacity + perPage - 1) / perPage
        return pages.coerceIn(1, pageLimit.coerceAtLeast(1))
    }

    /** 指定页面的行距：首页与普通页的余量分配结果不同。 */
    fun rowGapOf(page: Int): Int = if (page == 0 && hasWidget) firstRowGap else fullRowGap

    /** 指定页面应用区域的顶部偏移，用于让剩余空间上下对称。 */
    fun rowOffsetOf(page: Int): Int = if (page == 0 && hasWidget) firstOffset else fullOffset

    /** 指定页面应用区域的顶部坐标；首页组件槽下方使用 widgetGap 而不是常规间距。 */
    fun appAreaTop(page: Int): Int {
        val widgetPart = if (page == 0 && hasWidget) widgetHeight + widgetGap else 0
        return top + widgetPart + rowOffsetOf(page)
    }

    /** 指定页面内某个槽位的矩形。 */
    fun cellRect(
        page: Int,
        slot: Int,
    ): RectF {
        val areaTop = appAreaTop(page)
        val rowGap = rowGapOf(page)
        if (isList) {
            val y = areaTop + slot * (rowHeight + rowGap)
            return RectF(left.toFloat(), y.toFloat(), right.toFloat(), (y + rowHeight).toFloat())
        }
        val column = slot % columns
        val row = slot / columns
        val x = left + contentOffsetX + column * (cellWidth + columnGap)
        val y = areaTop + row * (cellHeight + rowGap)
        return RectF(x.toFloat(), y.toFloat(), (x + cellWidth).toFloat(), (y + cellHeight).toFloat())
    }

    /**
     * 计算触点落在哪个槽位。
     *
     * 返回值是页面内槽位下标，未命中返回 -1。行距与绘制使用同一套数值，
     * 触摸命中的行与屏幕上绘制的行保持一致。
     */
    fun slotAt(
        page: Int,
        x: Float,
        y: Float,
    ): Int {
        val areaTop = appAreaTop(page)
        val rowGap = rowGapOf(page)
        if (y < areaTop) return -1
        if (isList) {
            if (x < left || x > right) return -1
            val slot = ((y - areaTop) / (rowHeight + rowGap)).toInt()
            return if (slot in 0 until rowsOf(page)) slot else -1
        }
        if (x < left) return -1
        val columnOffset = x - left - contentOffsetX
        if (columnOffset < 0f) return -1
        // 列间空隙与超宽余量都归入相邻列，保证绘制区外仍有可点区域。
        val column =
            (columnOffset / (cellWidth + columnGap).coerceAtLeast(1))
                .toInt()
                .coerceIn(0, columns - 1)
        val row = ((y - areaTop) / (cellHeight + rowGap).coerceAtLeast(1)).toInt()
        val slot = row * columns + column
        return if (slot in 0 until capacity(page)) slot else -1
    }

    companion object {
        /** 宫格普通页行数上限，避免极小屏幕上出现过密布局。 */
        private const val MAX_GRID_ROWS = 12

        /** 左右页边距相对宽度的比例，取自图墨 480 宽屏幕的 30px。 */
        private const val GRID_SIDE_RATIO = 30f / 480f

        /** 组件槽下沿到应用区首行的间距比例，取自图墨的 28px。 */
        private const val ICON_TOP_RATIO = 28f / 480f

        /** 图标外框边长相对单元格宽度的比例，取自图墨 94/140。 */
        private const val ICON_FRAME_RATIO = 0.68f

        /** 图标外框相对令牌图标尺寸的上限倍率。 */
        private const val ICON_MAX_RATIO = 1.05f

        /** 自定义图标尺寸相对单元格宽度的下界比例。 */
        private const val ICON_MIN_RATIO = 0.55f

        /** 自定义图标尺寸相对单元格宽度的上界比例。 */
        private const val ICON_MAX_CELL_RATIO = 0.8f

        /** 列表形态每页行数上限。 */
        private const val MAX_LIST_ROWS = 10

        /** 首页应用行数下限：首页至少保留两行应用。 */
        private const val MIN_FIRST_ROWS = 2

        /** 宫格组件槽最小高度相对单元高度的比例。 */
        private const val WIDGET_MIN_CELL_RATIO = 1.6f

        /** 列表组件槽最小高度对应的行数。 */
        private const val LIST_WIDGET_MIN_ROWS = 4

        /**
         * 依据画布尺寸与当前配置计算版面。
         *
         * 首页组件槽高度不再固定为两行单元高度，而是按可用空间反算：
         * 先取首页应用行数的最大可行值，再把剩余高度全部交给组件槽，
         * 从源头消除组件与网格之间的大块空白。
         *
         * 各页应用区域的行距按余量均分：先按基准间距排布，
         * 再把剩余像素平均加到行与行之间，每行最多增加一个基准间距；
         * 分配后仍有余量时，将图标区域整体垂直居中，避免顶部堆叠、底部留白。
         */
        fun compute(
            width: Int,
            height: Int,
            config: LauncherConfig,
        ): DesktopMetrics {
            val tokens = DesignRuntime.tokens
            val desktop = config.desktop
            val isList = desktop.kind == DesktopKind.LIST
            // 左右边界对齐图墨：480 宽屏幕基准为 30px，其他宽度按比例换算；
            // 下限取令牌页边距，上限取画布四分之一，避免窄屏把内容挤成一条。
            val sideByWidth = Math.round(width * GRID_SIDE_RATIO).coerceAtLeast(0)
            val sideMin = tokens.pageGutter
            val sideMax = (width / 4).coerceAtLeast(sideMin)
            val side = sideByWidth.coerceIn(sideMin, sideMax)
            val left = side
            val right = (width - side).coerceAtLeast(left + 1)
            val showLabels = config.appearance.showLabels && !desktop.iconOnly
            val indicatorHeight = if (desktop.showPageIndicator) tokens.indicatorHeight else 0
            val top = tokens.pageTop
            val bottom = (height - tokens.pageBottom - indicatorHeight).coerceAtLeast(top + 1)
            val gap = tokens.gap
            // 组件槽下沿到应用区首行的间距对齐图墨 desktop_icon_top：480 宽基准为 28px。
            val widgetGap = Math.round(width * ICON_TOP_RATIO).coerceAtLeast(gap)
            // 版面覆盖项：配置为正数时直接采用像素换算值，否则沿用令牌推导结果。
            val columnGap =
                if (desktop.columnSpacingDp > 0) {
                    tokens.dp(desktop.columnSpacingDp.toFloat())
                } else {
                    gap
                }
            val rowBaseGap =
                if (desktop.rowSpacingDp > 0) {
                    tokens.dp(desktop.rowSpacingDp.toFloat())
                } else {
                    gap
                }
            val explicitIcon =
                if (desktop.iconSizeDp > 0) {
                    tokens.dp(desktop.iconSizeDp.toFloat())
                } else {
                    0
                }
            val hasWidget = desktop.widgetId.isNotBlank()
            val pageLimit = desktop.pageLimit.coerceAtLeast(1)
            val available = bottom - top

            if (isList) {
                val rowHeight = tokens.listRowHeight
                // 列表形态行间默认不留公共间距，仅当用户显式设置行间距时启用。
                val listRowBase = if (desktop.rowSpacingDp > 0) rowBaseGap else 0
                // 列表形态每页最多十行，矮屏按可用高度继续减少；
                // 用户覆盖行数同样受实际可容纳行数约束，避免溢出屏幕。
                val listRowsFitting = (available / rowHeight.coerceAtLeast(1)).coerceAtLeast(1)
                val fullRows =
                    (desktop.rows.takeIf { it > 0 } ?: listRowsFitting)
                        .coerceIn(1, minOf(listRowsFitting, MAX_LIST_ROWS))
                var firstRows = fullRows
                var widgetHeight = 0
                if (hasWidget) {
                    // 组件槽最小高度按四行列表高度约束，剩余空间全部并入组件槽。
                    val minWidget = rowHeight * LIST_WIDGET_MIN_ROWS
                    val autoFirst = maxFirstRows(available, fullRows, rowHeight, minWidget, listRowBase)
                    val block =
                        resolveWidgetBlock(
                            available = available,
                            fullRows = fullRows,
                            autoFirst = autoFirst,
                            rowSize = rowHeight,
                            gap = listRowBase,
                            widgetGap = widgetGap,
                            minWidget = minWidget,
                            preferred = desktop.firstPageRows,
                        )
                    firstRows = block.rows
                    widgetHeight = block.height
                }
                // 列表图标尺寸可被配置覆盖，但不超过行高扣除留白后的高度。
                val listIcon =
                    if (explicitIcon > 0) {
                        explicitIcon.coerceAtMost((rowHeight - gap).coerceAtLeast(tokens.glyphStroke * 2))
                    } else {
                        tokens.listIcon
                    }
                // 列表行之间原本无间距，余量以不超过基准间距的方式均分到行间。
                val normalFit = fitRows(bottom, top, fullRows, rowHeight, listRowBase, gap)
                val firstFit =
                    if (hasWidget) {
                        fitRows(bottom, top + widgetHeight + widgetGap, firstRows, rowHeight, listRowBase, gap)
                    } else {
                        normalFit
                    }
                return DesktopMetrics(
                    isList = true,
                    columns = 1,
                    fullRows = fullRows,
                    firstRows = firstRows,
                    widgetHeight = widgetHeight,
                    hasWidget = hasWidget,
                    pageLimit = pageLimit,
                    left = left,
                    right = right,
                    top = top,
                    bottom = bottom,
                    gap = gap,
                    widgetGap = widgetGap,
                    columnGap = 0,
                    contentOffsetX = 0,
                    cellWidth = right - left,
                    cellHeight = rowHeight,
                    rowHeight = rowHeight,
                    iconSize = listIcon,
                    labelHeight = 0,
                    showLabels = showLabels,
                    firstRowGap = firstFit.rowGap,
                    fullRowGap = normalFit.rowGap,
                    firstOffset = firstFit.offset,
                    fullOffset = normalFit.offset,
                )
            }

            // 列数优先取配置覆盖值，否则由设备画像按最小格宽换算。
            val columns =
                (
                    if (desktop.columns > 0) {
                        desktop.columns
                    } else {
                        DeviceRuntime.profile.gridColumns(tokens.cellMinWidth)
                    }
                ).coerceIn(1, 8)
            // 单元格宽度按可用宽度与列数均分；列间距非零时先扣除间距再均分，
            // 保证列间距调整后网格整体仍占满左右边界。
            val cellWidth = (((right - left) - columnGap * (columns - 1)) / columns).coerceAtLeast(1)
            val iconSize =
                if (explicitIcon > 0) {
                    // 用户指定图标外框边长时夹在单元格宽度的 0.55 到 0.8 之间，避免溢出或过小。
                    val lower = (cellWidth * ICON_MIN_RATIO).toInt().coerceAtLeast(1)
                    val upper = (cellWidth * ICON_MAX_CELL_RATIO).toInt().coerceAtLeast(lower)
                    explicitIcon.coerceIn(lower, upper)
                } else {
                    // 图墨 480 基准图标框 94px，约为单元格宽度的 0.68；
                    // 上限取令牌图标尺寸的 1.15 倍，避免大屏图标过度膨胀。
                    val byCell = Math.round(cellWidth * ICON_FRAME_RATIO)
                    val cap = Math.round(tokens.iconMax * ICON_MAX_RATIO)
                    minOf(byCell, cap).coerceAtLeast(tokens.glyphStroke * 4)
                }
            val totalGridWidth = cellWidth * columns + columnGap * (columns - 1)
            val contentOffsetX =
                when (desktop.gridAlign) {
                    GridAlign.LEFT -> 0
                    GridAlign.CENTER -> (((right - left) - totalGridWidth) / 2).coerceAtLeast(0)
                    GridAlign.RIGHT -> ((right - left) - totalGridWidth).coerceAtLeast(0)
                }
            val labelHeight = if (showLabels) tokens.labelHeight else 0
            val cellHeight = iconSize + labelHeight + rowBaseGap
            val rowsFitting = ((available + rowBaseGap) / (cellHeight + rowBaseGap).coerceAtLeast(1)).toInt()
            // 用户覆盖行数同样受实际可容纳行数约束，避免溢出屏幕。
            val fullRows =
                (desktop.rows.takeIf { it > 0 } ?: rowsFitting)
                    .coerceIn(1, minOf(rowsFitting.coerceAtLeast(1), MAX_GRID_ROWS))
            var firstRows = fullRows
            var widgetHeight = 0
            if (hasWidget) {
                // 首页按应用行数反算组件槽：优先保证多放一行应用，组件槽吸收剩余高度。
                val minWidget = (cellHeight * WIDGET_MIN_CELL_RATIO).toInt().coerceAtLeast(1)
                val autoFirst = maxFirstRows(available, fullRows, cellHeight, minWidget, rowBaseGap)
                val block =
                    resolveWidgetBlock(
                        available = available,
                        fullRows = fullRows,
                        autoFirst = autoFirst,
                        rowSize = cellHeight,
                        gap = rowBaseGap,
                        widgetGap = widgetGap,
                        minWidget = minWidget,
                        preferred = desktop.firstPageRows,
                    )
                firstRows = block.rows
                widgetHeight = block.height
            }
            // 宫格行间基准间距取行间距覆盖值，余量在每行最多增加一个基准间距的前提下均分。
            val normalFit = fitRows(bottom, top, fullRows, cellHeight, rowBaseGap, maxOf(gap, rowBaseGap))
            val firstFit =
                if (hasWidget) {
                    fitRows(
                        bottom,
                        top + widgetHeight + widgetGap,
                        firstRows,
                        cellHeight,
                        rowBaseGap,
                        maxOf(gap, rowBaseGap),
                    )
                } else {
                    normalFit
                }
            return DesktopMetrics(
                isList = false,
                columns = columns,
                fullRows = fullRows,
                firstRows = firstRows,
                widgetHeight = widgetHeight,
                hasWidget = hasWidget,
                pageLimit = pageLimit,
                left = left,
                right = right,
                top = top,
                bottom = bottom,
                gap = gap,
                widgetGap = widgetGap,
                columnGap = columnGap,
                contentOffsetX = contentOffsetX,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                rowHeight = 0,
                iconSize = iconSize,
                labelHeight = labelHeight,
                showLabels = showLabels,
                firstRowGap = firstFit.rowGap,
                fullRowGap = normalFit.rowGap,
                firstOffset = firstFit.offset,
                fullOffset = normalFit.offset,
            )
        }

        /**
         * 求首页应用行数的最大可行值。
         *
         * 参数 available 为版面的可用高度；fullRows 为常规页行数上限；
         * rowSize 为单行占位高度；minWidget 为组件槽最小高度；gap 为基准间距。
         * 行数下限为 MIN_FIRST_ROWS，上限为 fullRows；空间不足时退回下限，
         * 由调用方把组件槽钳制到最小高度。
         */
        private fun maxFirstRows(
            available: Int,
            fullRows: Int,
            rowSize: Int,
            minWidget: Int,
            gap: Int,
        ): Int {
            val lower = minOf(MIN_FIRST_ROWS, fullRows)
            val fitting = (available - gap - minWidget) / (rowSize + gap)
            return if (fitting < lower) lower else minOf(fitting, fullRows)
        }

        /**
         * 计算首页组件槽的行数与高度。
         *
         * 参数 available 为可用高度；fullRows 为常规页行数；
         * autoFirst 为自动推算的首页行数；rowSize 为单行占位高度；
         * gap 为基准间距；widgetGap 为组件槽下方间距；minWidget 为组件槽最小高度；
         * preferred 为用户指定的首页行数，小于等于零表示未指定。
         *
         * 逻辑：用户指定且放得下时优先采用；否则从自动行数向下尝试，
         * 直到组件槽不低于最小高度；组件槽高度按剩余空间扣除组件槽下方间距得到。
         */
        private fun resolveWidgetBlock(
            available: Int,
            fullRows: Int,
            autoFirst: Int,
            rowSize: Int,
            gap: Int,
            widgetGap: Int,
            minWidget: Int,
            preferred: Int,
        ): WidgetBlock {
            // 组件槽高度由可用高度反算：行间只占 (rows - 1) 个基准间距，
            // 组件槽下沿再占一个 widgetGap，其余全部归组件槽。
            // 旧实现按 (rows + 1) 个间距扣除，比实际排版多扣一个间距，
            // 使组件槽偏矮而被迫减少首页行数。
            fun heightFor(rows: Int): Int = available - rows * rowSize - (rows - 1) * gap - widgetGap

            if (preferred > 0) {
                val wanted = preferred.coerceIn(1, fullRows)
                val height = heightFor(wanted)
                if (height >= minWidget) return WidgetBlock(wanted, height)
            }
            // 首页应用行数下限取 1：矮屏上宁可只保留一行完整应用，
            // 也不保留半截行；组件槽高度不足最小时由后续分支压缩。
            val lower = 1.coerceAtMost(fullRows)
            var rows = (if (autoFirst > 0) autoFirst else fullRows).coerceIn(lower, fullRows)
            while (true) {
                val height = heightFor(rows)
                if (height >= minWidget || rows <= lower) {
                    // 行数到达下限仍不足最小高度时，组件槽压到实际剩余高度；
                    // 行距由 fitRows 在剩余空间内分配，保证最后一行完整可见。
                    return WidgetBlock(rows, height.coerceAtLeast(0))
                }
                rows--
            }
        }

        /**
         * 计算一页的行间距与顶部偏移。
         *
         * 参数 bottom 为版面底边；areaTop 为应用区域起点；rows 为行数；
         * rowSize 为单行占位高度；baseGap 为该形态的基准行间距；maxExtra 为每行允许的最大额外间距。
         * 返回值 rowGap 为最终行距，offset 为让剩余空间上下对称的顶部偏移。
         */
        private fun fitRows(
            bottom: Int,
            areaTop: Int,
            rows: Int,
            rowSize: Int,
            baseGap: Int,
            maxExtra: Int,
        ): RowFit {
            if (rows <= 0) return RowFit(baseGap, 0)
            val remaining = (bottom - areaTop - rows * rowSize).coerceAtLeast(0)
            if (rows == 1) return RowFit(baseGap, remaining / 2)
            val baseSpacing = (rows - 1) * baseGap
            val leftover = remaining - baseSpacing
            // 余量按行数均分，每行最多增加一个基准间距。
            val extra = if (leftover > 0) minOf(maxExtra, leftover / (rows - 1)) else 0
            val rowGap = baseGap + extra
            val residual = (remaining - rowGap * (rows - 1)).coerceAtLeast(0)
            return RowFit(rowGap, residual / 2)
        }
    }
}

/**
 * 单页行距与顶部偏移的计算结果。
 *
 * rowGap 为行与行之间的最终距离；offset 为应用区域相对版面上沿的偏移。
 */
private data class RowFit(
    val rowGap: Int,
    val offset: Int,
)

/**
 * 首页组件槽的计算结果。
 *
 * rows 为首页应用行数；height 为组件槽高度，单位为像素。
 */
private data class WidgetBlock(
    val rows: Int,
    val height: Int,
)
