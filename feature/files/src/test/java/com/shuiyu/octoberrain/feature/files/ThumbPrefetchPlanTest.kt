package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.ui.ThumbPrefetchPlan
import org.junit.Assert.assertEquals
import org.junit.Test

/** 缩略图预取顺序测试：核对当前页优先、前后页次序与边界裁剪。 */
class ThumbPrefetchPlanTest {
    /** 中间页按当前页、上一页、下一页排列。 */
    @Test
    fun `中间页预取顺序以当前页优先`() {
        assertEquals(listOf(1, 0, 2), ThumbPrefetchPlan.pagesOf(page = 1, pageCount = 3))
    }

    /** 末页只有当前页与上一页。 */
    @Test
    fun `末页不包含越界下一页`() {
        assertEquals(listOf(2, 1), ThumbPrefetchPlan.pagesOf(page = 5, pageCount = 3))
    }

    /** 单页目录只预取当前页。 */
    @Test
    fun `单页目录只预取当前页`() {
        assertEquals(listOf(0), ThumbPrefetchPlan.pagesOf(page = 0, pageCount = 1))
    }

    /** 非法页数返回空列表。 */
    @Test
    fun `页数为零时返回空列表`() {
        assertEquals(emptyList<Int>(), ThumbPrefetchPlan.pagesOf(page = 0, pageCount = 0))
    }

    /** 首页按当前页与下一页展开条目下标。 */
    @Test
    fun `首页展开当前页与下一页条目`() {
        val expected = listOf(0, 1, 2, 3, 4, 5, 6, 7)
        assertEquals(expected, ThumbPrefetchPlan.indicesOf(page = 0, pageSize = 4, itemCount = 10))
    }

    /** 中间页依次展开当前页、上一页与下一页。 */
    @Test
    fun `中间页展开三页条目`() {
        assertEquals(
            listOf(4, 5, 6, 7, 0, 1, 2, 3, 8, 9),
            ThumbPrefetchPlan.indicesOf(page = 1, pageSize = 4, itemCount = 10),
        )
    }

    /** 末页不补齐不存在的条目。 */
    @Test
    fun `末页只展开实际存在的条目`() {
        assertEquals(
            listOf(8, 9, 4, 5, 6, 7),
            ThumbPrefetchPlan.indicesOf(page = 2, pageSize = 4, itemCount = 10),
        )
    }

    /** 每页条目数或条目总数非正数时返回空列表。 */
    @Test
    fun `非法分页参数返回空列表`() {
        assertEquals(emptyList<Int>(), ThumbPrefetchPlan.indicesOf(page = 0, pageSize = 0, itemCount = 5))
        assertEquals(emptyList<Int>(), ThumbPrefetchPlan.indicesOf(page = 0, pageSize = 4, itemCount = 0))
    }
}
