package com.shuiyu.octoberrain.feature.files.ui.viewer

/** 查看器内的矩形区域，坐标以屏幕像素为单位，右边界与下边界为开区间。 */
data class ViewerArea(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    /** 区域宽度，负值按 0 处理。 */
    val width: Int get() = (right - left).coerceAtLeast(0)

    /** 区域高度，负值按 0 处理。 */
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    /** 区域是否为空。 */
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    /**
     * 判断坐标是否落在区域内。
     *
     * @param x 横坐标。
     * @param y 纵坐标。
     * @return 是否命中。
     */
    fun contains(
        x: Int,
        y: Int,
    ): Boolean = x >= left && x < right && y >= top && y < bottom
}

/**
 * 查看器几何布局。
 *
 * 纯计算对象，不含绘制与测量，可在本地 JVM 单元测试中验证命中区与面板行划分。
 * 顶栏固定为 [topBarHeight]，内容区为顶栏以下至屏幕底部，动作面板与详情面板贴内容区底边。
 *
 * 参数 width 与 height 为视图宽高；topBarHeight 为顶栏高度；rowHeight 为面板行高；
 * minTouch 为可点击范围下限，用于保证返回按钮与页码区的高度。
 */
class ViewerMetrics(
    width: Int,
    height: Int,
    private val topBarHeight: Int,
    private val rowHeight: Int,
    private val minTouch: Int,
    private val bottomBarHeight: Int = rowHeight,
) {
    /** 视图宽度。 */
    val screenWidth: Int = width.coerceAtLeast(0)

    /** 视图高度。 */
    val screenHeight: Int = height.coerceAtLeast(0)

    /** 顶栏区域。 */
    val topBar: ViewerArea =
        ViewerArea(0, 0, screenWidth, topBarHeight.coerceAtLeast(minTouch).coerceAtMost(screenHeight))

    /** 内容区，顶栏以下至屏幕底部。 */
    val content: ViewerArea =
        ViewerArea(0, topBar.bottom, screenWidth, screenHeight.coerceAtLeast(topBar.bottom))

    /** 返回按钮的点击区，位于顶栏左侧。 */
    val backArea: ViewerArea =
        ViewerArea(
            left = 0,
            top = topBar.top,
            right = minOf(screenWidth, topBarHeight.coerceAtLeast(minTouch)),
            bottom = topBar.bottom,
        )

    /**
     * 页码区的点击区，位于顶栏右侧。
     *
     * 页码只用于显示，返回该区域便于绘制时定位，点击不触发行为。
     */
    val pageArea: ViewerArea =
        ViewerArea(
            left = (screenWidth - backArea.width).coerceAtLeast(0),
            top = topBar.top,
            right = screenWidth,
            bottom = topBar.bottom,
        )

    /** 信息按钮的点击区，复用页码区的位置。 */
    val infoArea: ViewerArea get() = pageArea

    /** 顶栏标题区，位于返回与信息按钮之间。 */
    val titleArea: ViewerArea
        get() = ViewerArea(backArea.right, topBar.top, infoArea.left, topBar.bottom)

    /** 底栏区域，贴屏幕底边。 */
    val bottomBar: ViewerArea
        get() {
            val height = bottomBarHeight.coerceAtLeast(minTouch)
            val top = (screenHeight - height).coerceAtLeast(topBar.bottom)
            return ViewerArea(0, top, screenWidth, screenHeight)
        }

    /**
     * 底栏动作区的等分划分。
     *
     * @param count 动作数量，小于 1 时按 1 处理。
     * @return 从左到右的动作区域列表，宽度均分。
     */
    fun bottomActions(count: Int): List<ViewerArea> {
        val safeCount = count.coerceAtLeast(1)
        val bar = bottomBar
        val width = bar.width / safeCount
        return (0 until safeCount).map { index ->
            val left = bar.left + index * width
            val right = if (index == safeCount - 1) bar.right else left + width
            ViewerArea(left, bar.top, right, bar.bottom)
        }
    }

    /**
     * 计算面板整体区域。
     *
     * @param rowCount 可见行数，至少为 1。
     * @return 贴着内容区底边的面板区域。
     */
    fun panelArea(rowCount: Int): ViewerArea {
        val rows = rowCount.coerceAtLeast(1)
        val panelHeight = rowHeight.coerceAtLeast(minTouch) * rows
        val top = (screenHeight - panelHeight).coerceAtLeast(topBar.bottom)
        return ViewerArea(0, top, screenWidth, screenHeight)
    }

    /**
     * 计算面板内某一行的区域。
     *
     * @param rowCount 可见行数，至少为 1。
     * @param row 行序号，从 0 开始。
     * @return 行区域；序号越界时返回空区域。
     */
    fun rowArea(
        rowCount: Int,
        row: Int,
    ): ViewerArea {
        if (row < 0 || row >= rowCount.coerceAtLeast(1)) return ViewerArea(0, 0, 0, 0)
        val panel = panelArea(rowCount)
        val rowTop = panel.top + row * rowHeight.coerceAtLeast(minTouch)
        return ViewerArea(panel.left, rowTop, panel.right, rowTop + rowHeight.coerceAtLeast(minTouch))
    }

    /**
     * 判断点击是否命中面板某一行。
     *
     * @param x 横坐标。
     * @param y 纵坐标。
     * @param rowCount 可见行数。
     * @return 命中的行序号；未命中返回 -1。
     */
    fun panelRowAt(
        x: Int,
        y: Int,
        rowCount: Int,
    ): Int {
        for (row in 0 until rowCount.coerceAtLeast(1)) {
            if (rowArea(rowCount, row).contains(x, y)) return row
        }
        return -1
    }
}
