package com.shuiyu.octoberrain.feature.widgets.kernel

import com.inkread.weekread.kernel.ShelfBook
import com.inkread.weekread.kernel.ShelfSnapshot
import com.shuiyu.octoberrain.core.common.parseRecentReadTimes
import com.shuiyu.octoberrain.core.common.sortByRecentRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 书架按最近阅读时间排序的单元测试。
 *
 * 覆盖时间倒序、缺失时间后置、时间相同保持原序，以及资产文件解析。
 */
class KernelShelfSortTest {
    /**
     * 构造一本测试书目。
     *
     * @param bookId 书目编号
     * @return 仅填写编号的书目
     */
    private fun book(bookId: String): ShelfBook =
        ShelfBook().also {
            it.bookId = bookId
            it.title = bookId
        }

    /**
     * 构造一份测试书架快照。
     *
     * @param bookIds 书目编号
     * @return 填写书目与标识集合的快照
     */
    private fun snapshot(vararg bookIds: String): ShelfSnapshot =
        ShelfSnapshot().also { snapshot ->
            snapshot.books = bookIds.map(::book)
            snapshot.ids = bookIds.toSet()
        }

    /** 内核加载器有数据时优先于网络缓存。 */
    @Test
    fun loaderShelfWinsOverNetworkCache() {
        val loader = snapshot("p4-book")
        val network = snapshot("stale-one", "stale-two")
        val chosen = preferredShelf(loader, null, network)
        assertSame(loader, chosen)
        assertEquals(listOf("p4-book"), chosen?.books?.map { it.bookId })
    }

    /** 前两项为空时回落到网络缓存。 */
    @Test
    fun emptyCandidatesFallThroughToNetwork() {
        val network = snapshot("stale-one")
        val chosen = preferredShelf(snapshot(), null, network)
        assertSame(network, chosen)
    }

    /** 时间新的排在前面，缺失时间的书排在最后。 */
    @Test
    fun sortsByRecentReadAndKeepsMissingLast() {
        val books = listOf(book("a"), book("b"), book("c"), book("d"))
        val times = mapOf("a" to 100L, "c" to 300L, "d" to 200L)
        val sorted = sortByRecentRead(books, ShelfBook::bookId, times)
        assertEquals(listOf("c", "d", "a", "b"), sorted.map { it.bookId })
    }

    /** 上次选中的书不是最近阅读时，书架首本仍是最近阅读那本。 */
    @Test
    fun selectedBookDoesNotLeadShelf() {
        val selected = book("selected")
        val recent = book("recent")
        val snapshot =
            ShelfSnapshot().also {
                it.books = listOf(selected, recent)
                it.ids = setOf(selected.bookId, recent.bookId)
                it.fetchedAt = 1L
            }
        val ordered = recentFirstShelf(snapshot, mapOf("selected" to 100L, "recent" to 200L))
        assertEquals("recent", ordered.books.first().bookId)
        assertEquals(setOf("selected", "recent"), ordered.ids)
    }

    /** 时间相同的书目保持原有相对顺序。 */
    @Test
    fun keepsOriginalOrderForEqualTimes() {
        val books = listOf(book("a"), book("b"), book("c"))
        val times = mapOf("a" to 5L, "b" to 5L, "c" to 5L)
        assertEquals(listOf("a", "b", "c"), sortByRecentRead(books, ShelfBook::bookId, times).map { it.bookId })
    }

    /** 映射为空时原样返回同一实例，避免无谓复制。 */
    @Test
    fun keepsListWhenNoTimes() {
        val books = listOf(book("a"), book("b"))
        assertSame(books, sortByRecentRead(books, ShelfBook::bookId, emptyMap()))
    }

    /** 资产文本解析出书目编号与时间。 */
    @Test
    fun parsesReadTimesFromAssets() {
        val json =
            """
            {"books":[
              {"id":"1","title":"罪与罚","readUpdateTime":1792000000,"at":1792599226239},
              {"id":"2","title":"唐诗鉴赏辞典","readUpdateTime":1792600034,"at":1792599226239},
              {"id":"3","title":"无时间","at":1792599226239}
            ]}
            """.trimIndent()
        val times = parseRecentReadTimes(json)
        assertEquals(1792000000L, times["1"])
        assertEquals(1792600034L, times["2"])
        assertTrue(times.containsKey("3").not())
    }

    /** 无效文本返回空映射。 */
    @Test
    fun parsesNothingFromGarbage() {
        assertTrue(parseRecentReadTimes("").isEmpty())
        assertTrue(parseRecentReadTimes("{\"books\":[]}").isEmpty())
    }
}
