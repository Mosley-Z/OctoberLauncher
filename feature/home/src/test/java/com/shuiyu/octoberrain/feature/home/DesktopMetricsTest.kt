package com.shuiyu.octoberrain.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DesktopMetrics 的分页容量与触摸命中单元测试。
 *
 * compute 依赖 DesignRuntime 与 DeviceRuntime，无法在纯 JVM 测试中初始化，
 * 因此直接以构造参数建立固定版面，只验证不依赖 Android 框架的纯计算方法。
 */
class DesktopMetricsTest {
    /**
     * 构造宫格版面。
     *
     * 默认四列五行、首页三行并启用组件槽，边界与间距取固定像素值，
     * 首页与普通页的行距和顶部偏移取不同数值，便于断言页面间的差异。
     */
    private fun grid(
        hasWidget: Boolean = false,
        firstRows: Int = 3,
        fullRows: Int = 5,
        pageLimit: Int = 8,
        widgetHeight: Int = 200,
        widgetGap: Int = 8,
        contentOffsetX: Int = 0,
        columnGap: Int = 8,
    ): DesktopMetrics =
        DesktopMetrics(
            isList = false,
            columns = 4,
            fullRows = fullRows,
            firstRows = firstRows,
            widgetHeight = widgetHeight,
            hasWidget = hasWidget,
            pageLimit = pageLimit,
            left = 24,
            right = 456,
            top = 16,
            bottom = 784,
            gap = 8,
            // 组件槽下沿到应用区首行的间距，默认取 480 宽屏幕基准 28px 换算后的整数值
            widgetGap = widgetGap,
            columnGap = columnGap,
            contentOffsetX = contentOffsetX,
            cellWidth = 100,
            cellHeight = 120,
            rowHeight = 0,
            iconSize = 48,
            labelHeight = 24,
            showLabels = true,
            firstRowGap = 12,
            fullRowGap = 8,
            firstOffset = 4,
            fullOffset = 6,
        )

    /**
     * 构造列表版面。
     *
     * 列表形态每页固定一列，行高与行距用于换行计算。
     */
    private fun listWise(hasWidget: Boolean = false): DesktopMetrics =
        DesktopMetrics(
            isList = true,
            columns = 1,
            fullRows = 5,
            firstRows = 3,
            widgetHeight = 260,
            hasWidget = hasWidget,
            pageLimit = 8,
            left = 24,
            right = 456,
            top = 16,
            bottom = 784,
            gap = 8,
            widgetGap = 8,
            columnGap = 0,
            contentOffsetX = 0,
            cellWidth = 432,
            cellHeight = 60,
            rowHeight = 60,
            iconSize = 48,
            labelHeight = 0,
            showLabels = true,
            firstRowGap = 12,
            fullRowGap = 8,
            firstOffset = 4,
            fullOffset = 6,
        )

    /** 无组件时首页与普通页容量相同，页数按每页容量向上取整。 */
    @Test
    fun `宫格无组件时按每页容量分页`() {
        val metrics = grid()
        assertEquals(20, metrics.capacity(0))
        assertEquals(20, metrics.capacity(1))
        assertEquals(1, metrics.pageCount(0))
        assertEquals(1, metrics.pageCount(20))
        assertEquals(2, metrics.pageCount(21))
        assertEquals(2, metrics.pageCount(40))
        assertEquals(3, metrics.pageCount(41))
    }

    /** 首页启用组件后容量按首页行数缩减，后续页仍按完整行数计算。 */
    @Test
    fun `宫格首页组件压缩首页容量`() {
        val metrics = grid(hasWidget = true)
        assertEquals(12, metrics.capacity(0))
        assertEquals(20, metrics.capacity(1))
        assertEquals(1, metrics.pageCount(12))
        assertEquals(2, metrics.pageCount(13))
        assertEquals(2, metrics.pageCount(32))
        assertEquals(3, metrics.pageCount(33))
    }

    /** 页数上限生效，超出上限时截断到上限值。 */
    @Test
    fun `旧页数上限不再截断应用`() {
        val metrics = grid(pageLimit = 2)
        assertEquals(50, metrics.pageCount(1000))
    }

    @Test
    fun `首页独立列数与几何不影响其他页`() {
        val first = grid(hasWidget = true, firstRows = 2).copy(columns = 3, cellWidth = 130)
        val metrics = grid().copy(firstPage = first)
        assertEquals(6, metrics.capacity(0))
        assertEquals(20, metrics.capacity(1))
        assertEquals(6, metrics.startIndex(1))
        assertEquals(26, metrics.startIndex(2))
        assertEquals(0, metrics.pageOfIndex(5))
        assertEquals(1, metrics.pageOfIndex(6))
        assertEquals(2, metrics.pageOfIndex(26))
        assertEquals(130, metrics.forPage(0).cellWidth)
        assertEquals(100, metrics.forPage(1).cellWidth)
        for (page in 0..4) {
            for (index in metrics.startIndex(page) until metrics.startIndex(page + 1)) {
                assertEquals(page, metrics.pageOfIndex(index))
            }
        }
    }

    /** 列表形态每页容量等于行数，首页启用组件时容量同样缩减。 */
    @Test
    fun `列表形态容量与分页`() {
        val plain = listWise()
        assertEquals(5, plain.capacity(1))
        assertEquals(1, plain.pageCount(5))
        assertEquals(2, plain.pageCount(6))

        val widget = listWise(hasWidget = true)
        assertEquals(3, widget.capacity(0))
        assertEquals(5, widget.capacity(1))
        assertEquals(1, widget.pageCount(3))
        assertEquals(2, widget.pageCount(4))
        assertEquals(3, widget.pageCount(9))
    }

    /** 首页与普通页的首个入口下标由首页容量与每页容量推导。 */
    @Test
    fun `起始下标按首页容量递增`() {
        val plain = grid()
        assertEquals(0, plain.startIndex(0))
        assertEquals(20, plain.startIndex(1))
        assertEquals(40, plain.startIndex(2))

        val widget = grid(hasWidget = true)
        assertEquals(0, widget.startIndex(0))
        assertEquals(12, widget.startIndex(1))
        assertEquals(32, widget.startIndex(2))
    }

    /** 下标到页码的换算覆盖页边界两侧。 */
    @Test
    fun `下标换算页码覆盖页边界`() {
        val metrics = grid()
        assertEquals(0, metrics.pageOfIndex(0))
        assertEquals(0, metrics.pageOfIndex(19))
        assertEquals(1, metrics.pageOfIndex(20))
        assertEquals(1, metrics.pageOfIndex(39))
        assertEquals(2, metrics.pageOfIndex(40))
    }

    /** 起始下标与页码换算互为逆运算，覆盖全部页面与下标。 */
    @Test
    fun `起始下标与页码互为逆运算`() {
        val metrics = grid(hasWidget = true)
        for (page in 0..4) {
            for (index in metrics.startIndex(page) until metrics.startIndex(page + 1)) {
                assertEquals(page, metrics.pageOfIndex(index))
            }
        }
    }

    /** 首页与普通页使用各自的行距与顶部偏移。 */
    @Test
    fun `首页与普通页的行距偏移不同`() {
        val metrics = grid(hasWidget = true)
        assertEquals(12, metrics.rowGapOf(0))
        assertEquals(8, metrics.rowGapOf(1))
        assertEquals(4, metrics.rowOffsetOf(0))
        assertEquals(6, metrics.rowOffsetOf(1))
        assertEquals(228, metrics.appAreaTop(0))
        assertEquals(22, metrics.appAreaTop(1))
    }

    /** 宫格命中落在首个单元与相邻列。 */
    @Test
    fun `宫格命中首行各列`() {
        val metrics = grid()
        assertEquals(0, metrics.slotAt(1, 30f, 40f))
        assertEquals(1, metrics.slotAt(1, 30f + 108f, 40f))
        assertEquals(2, metrics.slotAt(1, 30f + 216f, 40f))
        assertEquals(3, metrics.slotAt(1, 30f + 324f, 40f))
    }

    /** 宫格命中第二行时槽位按列数平移。 */
    @Test
    fun `宫格命中第二行`() {
        val metrics = grid()
        assertEquals(4, metrics.slotAt(1, 30f, 155f))
        assertEquals(5, metrics.slotAt(1, 138f, 155f))
    }

    /** 纵坐标高于应用区域或横坐标位于左边界外时未命中。 */
    @Test
    fun `宫格边界外未命中`() {
        val metrics = grid()
        assertEquals(-1, metrics.slotAt(1, 30f, 10f))
        assertEquals(-1, metrics.slotAt(1, 10f, 40f))
    }

    /** 行列超出可用区域均不命中，横屏左侧组件不得穿透应用列。 */
    @Test
    fun `宫格越界行与超宽横坐标`() {
        val metrics = grid()
        assertEquals(-1, metrics.slotAt(1, 30f, 667f))
        assertEquals(-1, metrics.slotAt(1, 10_000f, 40f))
    }

    /** 宫格整体偏移为负时未命中，偏移后命中位置向右平移。 */
    @Test
    fun `宫格横向偏移影响命中`() {
        val metrics = grid(contentOffsetX = 50)
        assertEquals(-1, metrics.slotAt(1, 30f, 40f))
        assertEquals(0, metrics.slotAt(1, 79f, 40f))
    }

    /** 首页启用组件时应用区域整体下移，组件区域内未命中。 */
    @Test
    fun `宫格首页组件区域未命中`() {
        val metrics = grid(hasWidget = true)
        assertEquals(-1, metrics.slotAt(0, 30f, 100f))
        assertEquals(0, metrics.slotAt(0, 30f, 230f))
    }

    /** 首页行数少于普通页，超出行数的槽位未命中。 */
    @Test
    fun `宫格首页行数受限`() {
        val metrics = grid(hasWidget = true)
        assertEquals(-1, metrics.slotAt(0, 30f, 629f))
    }

    /** 列表形态按行高与行距换行，超出左右边界或行数未命中。 */
    @Test
    fun `列表形态命中与边界`() {
        val metrics = listWise()
        assertEquals(0, metrics.slotAt(1, 100f, 24f))
        assertEquals(2, metrics.slotAt(1, 100f, 163f))
        assertEquals(-1, metrics.slotAt(1, 100f, 367f))
        assertEquals(-1, metrics.slotAt(1, 10f, 100f))
        assertEquals(-1, metrics.slotAt(1, 500f, 100f))
    }

    /** 列表首页启用组件时应用区域下移，且受首页行数限制。 */
    @Test
    fun `列表首页组件区域未命中`() {
        val metrics = listWise(hasWidget = true)
        assertEquals(-1, metrics.slotAt(0, 100f, 200f))
        assertEquals(0, metrics.slotAt(0, 100f, 290f))
        assertEquals(-1, metrics.slotAt(0, 100f, 509f))
    }

    /**
     * 三档设备宽度下的组件槽间距。
     *
     * 间距比例取自图墨桌面图标顶距 28/480（见 DesktopMetrics.ICON_TOP_RATIO），
     * 按宽度换算后取整。480、552、824 三档分别对应基准屏、新增中屏与十寸屏，
     * 期望值为 28、32、48 像素；换算结果的相对误差不超过半个像素。
     */
    @Test
    fun `三档设备宽度的组件槽间距符合比例`() {
        // 与 DesktopMetrics.ICON_TOP_RATIO 保持一致的基准比例
        val ratio = 28f / 480f
        val widths = listOf(480, 552, 824)
        val expected = listOf(28, 32, 48)
        for ((index, width) in widths.withIndex()) {
            val widgetGap = Math.round(width * ratio)
            assertEquals(expected[index], widgetGap)
            val metrics = grid(hasWidget = true, widgetGap = widgetGap)
            // 首页应用区域起点由版面上沿、组件槽高度、间距与顶部偏移共同决定
            assertEquals(16 + 200 + widgetGap + 4, metrics.appAreaTop(0))
            assertEquals(22, metrics.appAreaTop(1))
            // 实际间距与理论比例的偏差不超过半个像素
            assertTrue(Math.abs(widgetGap - width * ratio) <= 0.5f)
        }
    }

    @Test
    fun `横屏分栏应用命中不受左侧组件高度影响`() {
        val landscape =
            grid(hasWidget = true, firstRows = 3).copy(
                columns = 1,
                left = 520,
                right = 780,
                widgetBounds = android.graphics.RectF(),
            )
        assertEquals(20, landscape.appAreaTop(0))
        assertEquals(-1, landscape.slotAt(0, 200f, 40f))
        assertEquals(0, landscape.slotAt(0, 600f, 40f))
        assertEquals(1, landscape.slotAt(0, 600f, 160f))
        assertEquals(3, landscape.capacity(0))
    }

    @Test
    fun `首页两列八格与全宽普通页容量独立且索引连续`() {
        val normal =
            grid(firstRows = 4).copy(
                firstPage =
                    grid(firstRows = 4).copy(
                        columns = 2,
                        fullRows = 4,
                        firstRows = 4,
                    ),
            )
        assertEquals(8, normal.capacity(0))
        assertEquals(20, normal.capacity(1))
        assertEquals(8, normal.startIndex(1))
        assertEquals(28, normal.startIndex(2))
        assertEquals(0, normal.pageOfIndex(7))
        assertEquals(1, normal.pageOfIndex(8))
        assertEquals(1, normal.pageOfIndex(27))
        assertEquals(2, normal.pageOfIndex(28))
        assertEquals(3, normal.pageCount(29))
        for (index in 0 until 49) {
            val page = normal.pageOfIndex(index)
            assertTrue(index >= normal.startIndex(page))
            assertTrue(index < normal.startIndex(page) + normal.capacity(page))
        }
    }

    @Test
    fun `首页显式小容量不改变普通页分页容量`() {
        val normal = grid().copy(firstPage = grid().copy(columns = 1, fullRows = 2, firstRows = 2))
        assertEquals(2, normal.capacity(0))
        assertEquals(20, normal.capacity(1))
        assertEquals(2, normal.startIndex(1))
        assertEquals(22, normal.startIndex(2))
    }

    @Test
    fun `阅读层右边界包含首页两列而非普通页较窄的页边距`() {
        val normal = grid().copy(right = 720, firstPage = grid().copy(left = 500, right = 735, columns = 2))
        assertEquals(735f, normal.noteRightBoundary(), .001f)
        assertEquals(720, normal.forPage(1).right)
        assertEquals(735f, normal.copy(firstPage = normal.firstPage?.copy(columns = 1)).noteRightBoundary(), .001f)
        assertEquals(705f, normal.copy(firstPage = normal.firstPage?.copy(right = 705)).noteRightBoundary(), .001f)
    }

    @Test fun `variable widget pages retain slots and cumulative pagination stays invertible`() {
        val compact = grid(hasWidget = true, firstRows = 1).copy(widgetPageGeometry = true)
        val normal = grid().copy(firstPage = compact, pageLayouts = mapOf(1 to compact, 3 to compact))
        assertEquals(4, normal.capacity(0))
        assertEquals(4, normal.capacity(1))
        assertEquals(20, normal.capacity(2))
        assertEquals(8, normal.startIndex(2))
        assertEquals(28, normal.startIndex(3))
        assertEquals(4, normal.pageCount(0))
        assertEquals(normal.appAreaTop(0), normal.appAreaTop(1))
        (0 until 60).forEach { index ->
            val page = normal.pageOfIndex(index)
            org.junit.Assert.assertTrue(
                index in normal.startIndex(page) until normal.startIndex(page) + normal.capacity(page),
            )
        }
        assertEquals(24, normal.copy(pageLayouts = emptyMap()).startIndex(2))
    }
}
