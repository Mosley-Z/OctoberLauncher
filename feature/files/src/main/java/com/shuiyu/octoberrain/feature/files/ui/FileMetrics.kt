package com.shuiyu.octoberrain.feature.files.ui

/**
 * 文件页几何派生。
 *
 * 所有尺寸基于宿主内容区的实测宽高，不使用整屏像素或 DisplayMetrics：
 * 页面由宿主放在状态栏之下，几何以内容区顶边为原点，底栏贴内容区底边。
 * 网格保持四列四行、每页 16 项，单元格宽度按列数与间距均分，缩略图高度取宽度的四分之三。
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

    /** 网格单元宽度，按左右留白、列数与间距均分。 */
    val cellW: Int = maxOf(1, (widthPx - tokens.padH * 2 - tokens.gap * (GRID_COLS - 1)) / GRID_COLS)

    /** 网格缩略图高度，宽高比取 3:4。 */
    val cellH: Int = maxOf(1, cellW * 4 / 3)

    /** 网格单元总高度，缩略图加标签。 */
    val cellTotalH: Int = cellH + tokens.cellLabelH

    /** 网格四行内容合计高度，含行间距，按标称缩略图高度计算。 */
    val gridContentH: Int = GRID_ROWS * cellTotalH + (GRID_ROWS - 1) * tokens.gap

    /**
     * 网格绘制用缩略图高度。
     *
     * 内容区容得下四行 3:4 缩略图时取标称高度 [cellH]；容不下时按四行、标签与行间距
     * 反推可用高度并压缩缩略图，保证四行完整落在内容区内，不遮挡底栏。
     */
    val gridCellDrawH: Int =
        maxOf(
            1,
            minOf(
                cellH,
                (contentH - GRID_ROWS * tokens.cellLabelH - (GRID_ROWS - 1) * tokens.gap) / GRID_ROWS,
            ),
        )

    /** 网格四行绘制合计高度，含标签与行间距。 */
    val gridDrawH: Int = GRID_ROWS * (gridCellDrawH + tokens.cellLabelH) + (GRID_ROWS - 1) * tokens.gap

    /** 网格每页条目数。 */
    val pageSize: Int = GRID_PAGE

    companion object {
        /** 网格列数，各设备一致。 */
        const val GRID_COLS = 4

        /** 网格行数，各设备一致。 */
        const val GRID_ROWS = 4

        /** 网格每页条目数。 */
        const val GRID_PAGE = GRID_COLS * GRID_ROWS
    }
}
