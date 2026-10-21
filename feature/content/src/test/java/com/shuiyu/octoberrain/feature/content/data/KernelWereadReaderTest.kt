package com.shuiyu.octoberrain.feature.content.data

import com.shuiyu.octoberrain.core.common.WereadShelfItem
import com.shuiyu.octoberrain.core.common.parseRecentReadTimes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 书架最近阅读排序的单元测试。
 *
 * 覆盖时间降序、缺失时间的条目排在末尾，以及资产缺失时保持内核顺序。
 */
class KernelWereadReaderTest {
    /** 构造待排序的书架条目，只填排序用到的字段。 */
    private fun item(
        bookId: String,
        title: String,
    ): WereadShelfItem =
        WereadShelfItem(
            bookId = bookId,
            title = title,
            author = "",
            coverPath = null,
            progress = 0f,
            deepLink = null,
        )

    /** 时间最新的排最前，缺失时间的条目排在末尾。 */
    @Test
    fun `按最近阅读时间降序`() {
        val items = listOf(item("a", "甲"), item("b", "乙"), item("c", "丙"))
        val times = parseRecentReadTimes(ASSETS_JSON)
        val ordered = KernelWereadReader.orderByRecentRead(items, times)
        assertEquals(listOf("b", "a", "c"), ordered.map { it.bookId })
    }

    /** 资产为空时保持内核给出的顺序。 */
    @Test
    fun `映射为空保持原序`() {
        val items = listOf(item("a", "甲"), item("b", "乙"), item("c", "丙"))
        val ordered = KernelWereadReader.orderByRecentRead(items, emptyMap())
        assertEquals(listOf("a", "b", "c"), ordered.map { it.bookId })
    }

    private companion object {
        /** 三本书的资产片段：甲与乙各带时间，丙缺失时间。 */
        private const val ASSETS_JSON = """
            [
              {"id":"a","readUpdateTime":1700000000},
              {"id":"b","readUpdateTime":1800000000},
              {"id":"c"}
            ]
        """
    }
}
