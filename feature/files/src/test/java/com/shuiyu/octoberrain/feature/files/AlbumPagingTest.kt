package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.data.MediaDay
import com.shuiyu.octoberrain.feature.files.data.MediaEntry
import com.shuiyu.octoberrain.feature.files.ui.album.AlbumPaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 相册分页换算用例。
 *
 * 覆盖空相册、单日恰好九张与超过九张、单日恰好一页与超过一页、跨日分页、空日分组、
 * 页码夹取与分段顺序守恒。全部为纯函数调用，不依赖 Android 环境。
 */
class AlbumPagingTest {
    /** 构造一条媒体条目，id 与日键用于区分。 */
    private fun entry(
        id: Long,
        day: String,
    ): MediaEntry =
        MediaEntry(
            id = id,
            path = "/sdcard/Pictures/$day-$id.jpg",
            name = "$day-$id.jpg",
            takenAt = 0L,
            addedAt = id,
            width = 100,
            height = 100,
            orientation = 0,
            mime = "image/jpeg",
        )

    /** 构造日分组，条目数量由参数决定。 */
    private fun day(
        key: String,
        count: Int,
        startId: Long = 1L,
    ): MediaDay {
        val items = (0 until count).map { entry(startId + it, key) }
        return MediaDay(key = key, title = key, time = 0L, items = items)
    }

    @Test
    fun `空相册返回一页`() {
        assertEquals(1, AlbumPaging.pageCount(emptyList()))
        assertTrue(AlbumPaging.pageOf(emptyList(), 0).isEmpty())
    }

    @Test
    fun `单日九张恰好一段`() {
        val days = listOf(day("d1", 9))
        val blocks = AlbumPaging.pageOf(days, 0)
        assertEquals(1, blocks.size)
        assertEquals(0..8, blocks[0].itemRange)
        assertFalse(blocks[0].continued)
        assertEquals(1, AlbumPaging.pageCount(days))
    }

    @Test
    fun `单日十张拆成两段`() {
        val days = listOf(day("d1", 10))
        val blocks = AlbumPaging.pageOf(days, 0)
        assertEquals(2, blocks.size)
        assertEquals(0..8, blocks[0].itemRange)
        assertEquals(9..9, blocks[1].itemRange)
        assertFalse(blocks[0].continued)
        assertTrue(blocks[1].continued)
        assertEquals(1, AlbumPaging.pageCount(days))
    }

    @Test
    fun `单日二十七张恰好一页`() {
        val days = listOf(day("d1", 27))
        val blocks = AlbumPaging.pageOf(days, 0)
        assertEquals(3, blocks.size)
        assertEquals(0..8, blocks[0].itemRange)
        assertEquals(9..17, blocks[1].itemRange)
        assertEquals(18..26, blocks[2].itemRange)
        assertEquals(1, AlbumPaging.pageCount(days))
    }

    @Test
    fun `单日二十八张进入第二页`() {
        val days = listOf(day("d1", 28))
        assertEquals(2, AlbumPaging.pageCount(days))
        val first = AlbumPaging.pageOf(days, 0)
        val second = AlbumPaging.pageOf(days, 1)
        assertEquals(3, first.size)
        assertEquals(1, second.size)
        assertEquals(27..27, second[0].itemRange)
    }

    @Test
    fun `三天各九张占满一页`() {
        val days = listOf(day("d1", 9), day("d2", 9), day("d3", 9))
        assertEquals(1, AlbumPaging.pageCount(days))
        val blocks = AlbumPaging.pageOf(days, 0)
        assertEquals(3, blocks.size)
        assertEquals(listOf(0, 1, 2), blocks.map { it.dayIndex })
    }

    @Test
    fun `四天进入第二页`() {
        val days = listOf(day("d1", 9), day("d2", 9), day("d3", 9), day("d4", 9))
        assertEquals(2, AlbumPaging.pageCount(days))
        val second = AlbumPaging.pageOf(days, 1)
        assertEquals(1, second.size)
        assertEquals(3, second[0].dayIndex)
    }

    @Test
    fun `空日分组占一段`() {
        val days = listOf(day("d1", 0), day("d2", 3))
        val blocks = AlbumPaging.pageOf(days, 0)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0].itemRange.isEmpty())
        assertEquals(0..2, blocks[1].itemRange)
    }

    @Test
    fun `每页缩略图不超过容量上限`() {
        val days = listOf(day("d1", 100))
        val pages = AlbumPaging.pageCount(days)
        assertTrue(pages >= 4)
        for (page in 0 until pages) {
            var cells = 0
            for (block in AlbumPaging.pageOf(days, page)) {
                cells += if (block.itemRange.isEmpty()) 1 else block.itemRange.count()
            }
            assertTrue(cells <= AlbumPaging.MAX_CELLS_PER_PAGE)
        }
    }

    @Test
    fun `每段不超过九张`() {
        val days = listOf(day("d1", 53))
        for (page in 0 until AlbumPaging.pageCount(days)) {
            for (block in AlbumPaging.pageOf(days, page)) {
                assertTrue(block.itemRange.count() <= AlbumPaging.ITEMS_PER_BLOCK)
            }
        }
    }

    @Test
    fun `分段范围连续覆盖全部条目`() {
        val days = listOf(day("d1", 23), day("d2", 4))
        val covered = ArrayList<Int>()
        for (page in 0 until AlbumPaging.pageCount(days)) {
            for (block in AlbumPaging.pageOf(days, page)) {
                if (block.itemRange.isEmpty()) continue
                covered += block.itemRange.toList()
            }
        }
        assertEquals(27, covered.size)
        assertEquals((0..22).toList() + (0..3).toList(), covered)
    }

    @Test
    fun `页码越界时夹取`() {
        assertEquals(0, AlbumPaging.clampPage(3, -5))
        assertEquals(2, AlbumPaging.clampPage(3, 99))
        assertEquals(0, AlbumPaging.clampPage(0, 4))
    }

    @Test
    fun `页码越界时取末页分段`() {
        val days = listOf(day("d1", 9), day("d2", 9), day("d3", 9), day("d4", 9))
        val last = AlbumPaging.pageOf(days, 99)
        assertEquals(1, last.size)
        assertEquals(3, last[0].dayIndex)
    }

    @Test
    fun `单日条目顺序保持稳定`() {
        val days = listOf(day("d1", 19, startId = 100L))
        val ids = ArrayList<Long>()
        for (page in 0 until AlbumPaging.pageCount(days)) {
            for (block in AlbumPaging.pageOf(days, page)) {
                for (index in block.itemRange) {
                    ids += days[0].items[index].id
                }
            }
        }
        assertEquals((100L until 119L).toList(), ids)
    }
}
