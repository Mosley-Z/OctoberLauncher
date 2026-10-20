package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.DesktopLayoutSnapshot
import com.shuiyu.octoberrain.core.common.EntryType
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.LauncherEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面分页换算的单元测试。
 *
 * 分页为纯函数，不读取布局提供方与设备信息，便于在本地单元测试中验证
 * 首页行数与通用行数的差异、空目录补页、越界页下标等边界。
 */
class DesktopPaginationTest {
    @Test
    fun `首页按首页行数切分且后续页按通用行数切分`() {
        val entries = entries(16)
        val pages = DesktopPagination.paginate(entries, columns = 3, firstPageRows = 2, rowsPerPage = 3)
        assertEquals(listOf(6, 9, 1), pages.map { it.size })
        assertEquals(entries.take(6), pages[0])
        assertEquals(entries.subList(6, 15), pages[1])
        assertEquals(entries.subList(15, 16), pages[2])
    }

    @Test
    fun `空条目返回空页列表`() {
        assertTrue(DesktopPagination.paginate(emptyList(), 5, 5, 5).isEmpty())
    }

    @Test
    fun `行列小于一时按一处理`() {
        val pages = DesktopPagination.paginate(entries(3), columns = 0, firstPageRows = 0, rowsPerPage = -1)
        assertEquals(3, pages.size)
        pages.forEach { assertEquals(1, it.size) }
    }

    @Test
    fun `本地换算在配置为零时使用兜底行列`() {
        val snapshot = DesktopPagination.fallbackSnapshot(LauncherConfig(), emptyList())
        assertEquals(DesktopPagination.DEFAULT_COLUMNS, snapshot.columns)
        assertEquals(DesktopPagination.DEFAULT_ROWS, snapshot.firstPageRows)
        assertEquals(DesktopPagination.DEFAULT_ROWS, snapshot.rowsPerPage)
        assertEquals(1, snapshot.pages.size)
        assertTrue(snapshot.pages[0].isEmpty())
    }

    @Test
    fun `本地换算读取配置的列数与首页行数`() {
        val config =
            LauncherConfig(
                desktop = LauncherConfig().desktop.copy(columns = 4, rows = 3, firstPageRows = 1),
            )
        val snapshot = DesktopPagination.fallbackSnapshot(config, entries(13))
        assertEquals(4, snapshot.columns)
        assertEquals(1, snapshot.firstPageRows)
        assertEquals(3, snapshot.rowsPerPage)
        assertEquals(listOf(4, 9), snapshot.pages.map { it.size })
    }

    @Test
    fun `页容量区分首页与后续页`() {
        val snapshot =
            DesktopLayoutSnapshot(
                columns = 3,
                firstPageRows = 2,
                rowsPerPage = 4,
                pages = emptyList(),
            )
        assertEquals(6, DesktopPagination.pageCapacity(snapshot, 0))
        assertEquals(12, DesktopPagination.pageCapacity(snapshot, 1))
        assertEquals(12, DesktopPagination.pageCapacity(snapshot, 9))
    }

    @Test
    fun `页容量在行列无效时至少为一`() {
        val snapshot = DesktopLayoutSnapshot(columns = 0, firstPageRows = 0, rowsPerPage = 0, pages = emptyList())
        assertEquals(1, DesktopPagination.pageCapacity(snapshot, 0))
        assertEquals(1, DesktopPagination.pageCapacity(snapshot, 1))
    }

    @Test
    fun `条目键按页展开`() {
        val snapshot =
            DesktopLayoutSnapshot(
                columns = 2,
                firstPageRows = 2,
                rowsPerPage = 2,
                pages = listOf(entries(2), entries(2, offset = 2)),
            )
        assertEquals(listOf(listOf("pkg-0", "pkg-1"), listOf("pkg-2", "pkg-3")), DesktopPagination.keysOf(snapshot))
    }

    @Test
    fun `当前页顺序替换到完整顺序中`() {
        val pages = listOf(listOf("a", "b"), listOf("c", "d"))
        assertEquals(listOf("a", "b", "x"), DesktopPagination.mergeOrder(pages, 1, listOf("x")))
        assertEquals(listOf("a", "b", "c", "d"), DesktopPagination.mergeOrder(pages, 3, listOf("x")))
    }

    @Test
    fun `页结构为空时直接采用当前页顺序`() {
        assertEquals(listOf("x", "y"), DesktopPagination.mergeOrder(emptyList(), 0, listOf("x", "y")))
    }

    /** 构造指定数量的应用条目，键为 pkg-序号。 */
    private fun entries(
        count: Int,
        offset: Int = 0,
    ): List<LauncherEntry> =
        (0 until count).map { index ->
            val key = "pkg-${index + offset}"
            LauncherEntry(key = key, type = EntryType.APP, label = key, packageName = key)
        }
}
