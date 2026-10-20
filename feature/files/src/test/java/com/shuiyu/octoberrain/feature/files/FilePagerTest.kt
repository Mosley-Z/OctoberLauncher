package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.ui.FilePagerState
import com.shuiyu.octoberrain.feature.files.ui.FilePaging
import com.shuiyu.octoberrain.feature.files.ui.Hit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分页测试：核对页码换算、手势阈值与命中判定。 */
class FilePagerTest {
    /** 总页数按向上取整计算，空列表保留一页。 */
    @Test
    fun `总页数按向上取整`() {
        assertEquals(1, FilePaging.pageCount(itemCount = 0, pageSize = 16))
        assertEquals(1, FilePaging.pageCount(itemCount = 1, pageSize = 16))
        assertEquals(1, FilePaging.pageCount(itemCount = 16, pageSize = 16))
        assertEquals(2, FilePaging.pageCount(itemCount = 17, pageSize = 16))
        assertEquals(2, FilePaging.pageCount(itemCount = 32, pageSize = 16))
    }

    /** 每页条目数非正时总页数保底为一。 */
    @Test
    fun `每页条目数非正时保底一页`() {
        assertEquals(1, FilePaging.pageCount(itemCount = 10, pageSize = 0))
        assertEquals(1, FilePaging.pageCount(itemCount = 10, pageSize = -1))
    }

    /** 页码夹取到合法范围。 */
    @Test
    fun `页码夹取到合法范围`() {
        assertEquals(0, FilePaging.clampPage(page = -3, pageCount = 3))
        assertEquals(0, FilePaging.clampPage(page = 0, pageCount = 3))
        assertEquals(2, FilePaging.clampPage(page = 5, pageCount = 3))
        assertEquals(0, FilePaging.clampPage(page = 0, pageCount = 0))
    }

    /** 分页状态在条目减少时回退到末页。 */
    @Test
    fun `条目减少时页码回退`() {
        val state = FilePagerState(pageSize = 16)
        state.updateItemCount(33)
        state.setPage(2)
        assertEquals(2, state.page)
        assertEquals(32, state.firstIndex)
        assertEquals(33, state.lastIndex)
        state.updateItemCount(5)
        assertEquals(0, state.page)
        assertEquals(1, state.pageCount)
    }

    /** 翻页在边界处停住。 */
    @Test
    fun `翻页在边界处停住`() {
        val state = FilePagerState(pageSize = 16)
        state.updateItemCount(20)
        state.prevPage()
        assertEquals(0, state.page)
        state.nextPage()
        assertEquals(1, state.page)
        state.nextPage()
        assertEquals(1, state.page)
        assertEquals(16, state.firstIndex)
        assertEquals(20, state.lastIndex)
    }

    /** 长按判定时长为 350 毫秒。 */
    @Test
    fun `长按判定时长为 350 毫秒`() {
        assertEquals(350L, FilePaging.LONG_PRESS_MS)
    }

    /** 上下滑阈值以 48dp 为基准，随密度换算为像素。 */
    @Test
    fun `滑动阈值以 48dp 为基准`() {
        assertEquals(48f, FilePaging.SWIPE_THRESHOLD_DP, 0f)
        assertEquals(66, dpOf(dpi = 219, value = 48f))
        assertEquals(64, dpOf(dpi = 213, value = 48f))
        assertEquals(96, dpOf(dpi = 320, value = 48f))
    }

    /** 纵向位移占优且超过阈值才视为翻页手势。 */
    @Test
    fun `纵向位移占优才翻页`() {
        assertTrue(FilePaging.isVerticalSwipe(dx = 5, dy = 60, thresholdPx = 16))
        assertFalse(FilePaging.isVerticalSwipe(dx = 60, dy = 5, thresholdPx = 16))
        assertTrue(FilePaging.isVerticalSwipe(dx = 0, dy = 17, thresholdPx = 16))
        assertFalse(FilePaging.isVerticalSwipe(dx = 0, dy = 16, thresholdPx = 16))
    }

    /** 点击容差内判定为点击。 */
    @Test
    fun `点击容差内判定为点击`() {
        assertTrue(FilePaging.isTap(dx = 16, dy = 16, slopPx = 16))
        assertTrue(FilePaging.isTap(dx = 0, dy = 0, slopPx = 16))
        assertFalse(FilePaging.isTap(dx = 17, dy = 0, slopPx = 16))
        assertFalse(FilePaging.isTap(dx = 0, dy = -17, slopPx = 16))
    }

    /** 命中块包含左边界与上边界，不包含右边界与下边界。 */
    @Test
    fun `命中块边界判定`() {
        val hit = Hit(id = 3, left = 10, top = 10, right = 20, bottom = 20)
        assertTrue(hit.contains(10, 10))
        assertTrue(hit.contains(19, 19))
        assertFalse(hit.contains(20, 19))
        assertFalse(hit.contains(19, 20))
        assertFalse(hit.contains(9, 10))
    }

    private fun dpOf(
        dpi: Int,
        value: Float,
    ): Int {
        val density = dpi / 160f
        return Math.round(value * density)
    }
}
