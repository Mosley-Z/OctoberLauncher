package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.ui.cancelButtonRect
import com.shuiyu.octoberrain.feature.files.ui.selectionBarRects
import com.shuiyu.octoberrain.feature.files.ui.selectionCellAt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 多选动作行与顶栏取消按钮几何测试。
 *
 * 动作行位于底栏上方、高度为底栏的四分之三，宽度三等分为复制到、移动到、删除三格；
 * 覆盖格位命中、边界、宽度余数与视图过矮时的保底行为。
 */
class SelectionBarTest {
    /** 动作行紧贴底栏上沿，三格等宽且不留空隙。 */
    @Test
    fun `动作行位于底栏上方并三等分`() {
        val rects = selectionBarRects(width = 300, height = 800, bottomBarH = 70, selectionBarH = 50)

        assertEquals(680, rects.bar.top)
        assertEquals(730, rects.bar.bottom)
        assertEquals(50, rects.bar.height)
        assertEquals(300, rects.bar.width)
        assertEquals(3, rects.cells.size)
        assertEquals(0, rects.cells[0].left)
        assertEquals(100, rects.cells[0].right)
        assertEquals(100, rects.cells[1].left)
        assertEquals(200, rects.cells[1].right)
        assertEquals(200, rects.cells[2].left)
        assertEquals(300, rects.cells[2].right)
    }

    /** 格位命中按横向位置区分，动作行之外返回 -1。 */
    @Test
    fun `格位命中与范围外返回`() {
        val rects = selectionBarRects(width = 300, height = 800, bottomBarH = 70, selectionBarH = 50)

        assertEquals(0, selectionCellAt(50, 700, rects))
        assertEquals(1, selectionCellAt(100, 680, rects))
        assertEquals(1, selectionCellAt(150, 700, rects))
        assertEquals(2, selectionCellAt(299, 729, rects))
        assertEquals(-1, selectionCellAt(150, 500, rects))
        assertEquals(-1, selectionCellAt(150, 730, rects))
        assertEquals(-1, selectionCellAt(-1, 700, rects))
    }

    /** 宽度无法被三整除时余数归入删除格，三格仍覆盖整个宽度。 */
    @Test
    fun `宽度余数归入删除格`() {
        val rects = selectionBarRects(width = 302, height = 800, bottomBarH = 70, selectionBarH = 50)

        assertEquals(100, rects.cells[0].right)
        assertEquals(200, rects.cells[1].right)
        assertEquals(302, rects.cells[2].right)
        assertEquals(302, rects.bar.width)
    }

    /** 视图高度过矮时动作行贴住顶部，不出现负坐标。 */
    @Test
    fun `过矮时动作行不越出视图`() {
        val rects = selectionBarRects(width = 300, height = 100, bottomBarH = 70, selectionBarH = 50)

        assertEquals(0, rects.bar.top)
        assertEquals(30, rects.bar.bottom)
        assertEquals(0, rects.cells[0].top)
        assertEquals(30, rects.cells[0].bottom)
        assertEquals(0, selectionCellAt(10, 10, rects))
    }

    /** 动作行高度取底栏高度的四分之三，480×800 设备上底栏 71px 时动作行为 53px。 */
    @Test
    fun `动作行高度为底栏的四分之三`() {
        val rects = selectionBarRects(width = 480, height = 754, bottomBarH = 71, selectionBarH = 53)

        assertEquals(53, rects.bar.height)
        assertEquals(754 - 71, rects.bar.bottom)
        assertEquals(53, rects.cells[0].height)
        assertEquals(53, rects.cells[2].height)
    }

    /** 高度不足以容纳底栏时动作行退化为空，命中查询返回 -1。 */
    @Test
    fun `高度不足时动作行退化为空`() {
        val rects = selectionBarRects(width = 300, height = 0, bottomBarH = 70, selectionBarH = 50)

        assertEquals(0, rects.bar.top)
        assertEquals(0, rects.bar.bottom)
        assertEquals(-1, selectionCellAt(0, 0, rects))
    }

    /** 取消按钮紧贴动作按钮左侧，宽度为文案宽度加两侧留白。 */
    @Test
    fun `取消按钮紧贴动作按钮左侧`() {
        val rect = cancelButtonRect(topActionLeft = 373, topBarH = 77, textWidth = 40, padding = 25)

        assertEquals(308, rect.left)
        assertEquals(373, rect.right)
        assertEquals(65, rect.width)
        assertEquals(77, rect.height)
    }

    /** 顶栏高度为零时取消按钮退化为空，不参与命中。 */
    @Test
    fun `取消按钮在顶栏高度不足时退化为空`() {
        val rect = cancelButtonRect(topActionLeft = 373, topBarH = 0, textWidth = 40, padding = 25)

        assertEquals(0, rect.height)
    }
}
