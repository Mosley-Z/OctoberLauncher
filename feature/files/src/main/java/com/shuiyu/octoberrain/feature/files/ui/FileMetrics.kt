package com.shuiyu.octoberrain.feature.files.ui

import kotlin.math.roundToInt

/**
 * 文件页几何派生。
 *
 * 所有尺寸基于宿主内容区的实测宽高，不使用整屏像素或 DisplayMetrics：
 * 页面由宿主放在状态栏之下，几何以内容区顶边为原点，底栏贴内容区底边。
 * 网格竖屏三列、横屏五列，行数按实测内容高度选择；方形缩略图占主位，分页容量随布局变化。
 *
 * 参数 widthPx 与 heightPx 为内容区宽高；tokens 为尺寸令牌。
 */
class FileMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val tokens: FileTokens,
) {
    /** 内容区上边界，即顶栏底边。 */
    val contentTop: Int = tokens.topBarH

    /** 内容区下边界，即底栏顶边。 */
    val contentBottom: Int = (heightPx - tokens.bottomBarH).coerceAtLeast(contentTop)

    /** 内容区高度。 */
    val contentH: Int = (contentBottom - contentTop).coerceAtLeast(0)

    /** 列表每页行数，由内容区高度整除行高得到，至少一行。 */
    val listRows: Int = maxOf(1, contentH / tokens.rowH)

    val gridColumns: Int = if (widthPx > heightPx) 5 else 3

    /** 网格单元宽度，按左右留白、列数与间距均分。 */
    val cellW: Int = maxOf(1, (widthPx - tokens.padH * 2 - tokens.gap * (gridColumns - 1)) / gridColumns)

    /** 标称方形缩略图边长；实际绘制再按实际行数可用高度约束。 */
    val cellH: Int = cellW

    /** 网格单元总高度，缩略图加标签。 */
    val cellTotalH: Int = cellH + tokens.cellLabelH

    /** Choose rows near the natural square-card height, without shrinking every card to a fixed page count. */
    val gridRows: Int =
        ((contentH + tokens.gap).toFloat() / (cellTotalH + tokens.gap))
            .roundToInt()
            .coerceAtLeast(1)

    /** 网格实际行数内容合计高度，含行间距，按标称缩略图高度计算。 */
    val gridContentH: Int = gridRows * cellTotalH + (gridRows - 1) * tokens.gap

    /**
     * 网格绘制用缩略图高度。
     *
     * 内容区容得下实际行数方形缩略图时取标称边长 [cellH]；容不下时按实际行数、标签与行间距
     * 反推可用高度并压缩缩略图，保证实际行数完整落在内容区内，不遮挡底栏。
     */
    val gridCellDrawH: Int =
        maxOf(
            1,
            minOf(
                cellH,
                (contentH - gridRows * tokens.cellLabelH - (gridRows - 1) * tokens.gap) / gridRows,
            ),
        )

    /** 网格实际行数绘制合计高度，含标签与行间距。 */
    val gridDrawH: Int = gridRows * (gridCellDrawH + tokens.cellLabelH) + (gridRows - 1) * tokens.gap

    /** 网格每页条目数。 */
    val pageSize: Int = gridColumns * gridRows

    /** One shared layout for browser and picker; labels and side whitespace remain clickable. */
    fun gridCell(
        slot: Int,
        originX: Int = 0,
        originY: Int = contentTop,
    ): FileGridCell {
        val column = slot % gridColumns
        val row = slot / gridColumns
        val left = originX + tokens.padH + column * (cellW + tokens.gap)
        val top = originY + row * (gridCellDrawH + tokens.cellLabelH + tokens.gap)
        val edge = minOf(cellW, gridCellDrawH)
        val thumbLeft = left + (cellW - edge) / 2
        val imageBottom = top + gridCellDrawH
        return FileGridCell(
            FileGridRect(thumbLeft, top, thumbLeft + edge, top + edge),
            FileGridRect(left, imageBottom, left + cellW, imageBottom + tokens.cellLabelH),
            FileGridRect(left, top, left + cellW, imageBottom + tokens.cellLabelH),
        )
    }
}

/** Integer geometry remains testable without Android Rect stubs. */
data class FileGridRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun contains(
        x: Int,
        y: Int,
    ): Boolean = x >= left && x < right && y >= top && y < bottom
}

data class FileGridCell(
    val thumbnail: FileGridRect,
    val label: FileGridRect,
    val hit: FileGridRect,
)
