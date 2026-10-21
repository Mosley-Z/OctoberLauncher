package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 负一屏书架行数配置测试。
 *
 * 行数允许在 1 至 5 之间调整，每行两本；读取时越界值夹取到合法区间，
 * 旧配置缺少该字段时取默认值，取书上限覆盖最大行数。
 */
class NegativePageConfigShelfRowsTest {
    /** 越界行数在读取时夹取到下限与上限。 */
    @Test
    fun `越界行数夹取到合法区间`() {
        assertEquals(NegativePageConfig.SHELF_ROWS_MIN, NegativePageConfig(shelfRows = 0).effectiveShelfRows)
        assertEquals(NegativePageConfig.SHELF_ROWS_MIN, NegativePageConfig(shelfRows = -3).effectiveShelfRows)
        assertEquals(NegativePageConfig.SHELF_ROWS_MAX, NegativePageConfig(shelfRows = 6).effectiveShelfRows)
        assertEquals(3, NegativePageConfig(shelfRows = 3).effectiveShelfRows)
    }

    /** 缺少 shelfRows 字段的旧配置按默认行数解析。 */
    @Test
    fun `缺少字段取默认行数`() {
        val decoded = AppJson.format.decodeFromString<NegativePageConfig>("{\"enabled\":true}")
        assertEquals(2, decoded.shelfRows)
        assertEquals(NegativePageConfig.SHELF_ROWS_DEFAULT, decoded.effectiveShelfRows)
    }

    /** 展示条目上限等于有效行数与每行本数之积。 */
    @Test
    fun `展示上限等于行数乘每行本数`() {
        assertEquals(2, NegativePageConfig(shelfRows = 1).shelfVisibleLimit)
        assertEquals(10, NegativePageConfig(shelfRows = 5).shelfVisibleLimit)
        for (rows in NegativePageConfig.SHELF_ROWS_MIN..NegativePageConfig.SHELF_ROWS_MAX) {
            val page = NegativePageConfig(shelfRows = rows)
            assertEquals(page.effectiveShelfRows * NegativePageConfig.SHELF_COLUMNS, page.shelfVisibleLimit)
        }
    }

    /** 任一合法行数的展示上限都不超过取书上限。 */
    @Test
    fun `展示上限不超过取书上限`() {
        assertEquals(10, NegativePageConfig.SHELF_ITEM_LIMIT)
        for (rows in NegativePageConfig.SHELF_ROWS_MIN..NegativePageConfig.SHELF_ROWS_MAX) {
            assertTrue(NegativePageConfig(shelfRows = rows).shelfVisibleLimit <= NegativePageConfig.SHELF_ITEM_LIMIT)
        }
        assertEquals(
            NegativePageConfig.SHELF_ROWS_MAX * NegativePageConfig.SHELF_COLUMNS,
            NegativePageConfig.SHELF_ITEM_LIMIT,
        )
    }
}
