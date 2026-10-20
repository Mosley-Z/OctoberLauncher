package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.data.FileItem
import com.shuiyu.octoberrain.feature.files.data.FileSortKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** 条目排序测试：核对目录优先、升降序与回退规则。 */
class FileSortTest {
    /** 目录始终排在文件之前，与排序依据无关。 */
    @Test
    fun `目录优先于文件`() {
        val items =
            listOf(
                fileOf("a.txt", mtime = 100),
                dirOf("zzz"),
            )
        val sorted = items.sortedWith(FileSortKey.NAME.comparator(ascending = true, dirFirst = true))
        assertEquals(listOf("zzz", "a.txt"), sorted.map { it.name })
    }

    /** 名称忽略大小写升序。 */
    @Test
    fun `名称忽略大小写升序`() {
        val items =
            listOf(
                fileOf("Beta.txt", mtime = 100),
                fileOf("alpha.txt", mtime = 100),
                fileOf("gamma.txt", mtime = 100),
            )
        val sorted = items.sortedWith(FileSortKey.NAME.comparator(ascending = true, dirFirst = false))
        assertEquals(listOf("alpha.txt", "Beta.txt", "gamma.txt"), sorted.map { it.name })
    }

    /** 降序在同类条目内反转，目录优先保持不变。 */
    @Test
    fun `降序反转名称顺序`() {
        val items =
            listOf(
                fileOf("a.txt", mtime = 100),
                fileOf("c.txt", mtime = 100),
                fileOf("b.txt", mtime = 100),
                dirOf("dir"),
            )
        val sorted = items.sortedWith(FileSortKey.NAME.comparator(ascending = false, dirFirst = true))
        assertEquals(listOf("dir", "c.txt", "b.txt", "a.txt"), sorted.map { it.name })
    }

    /** 按修改时间升序。 */
    @Test
    fun `按修改时间升序`() {
        val items =
            listOf(
                fileOf("new.txt", mtime = 300),
                fileOf("old.txt", mtime = 100),
                fileOf("mid.txt", mtime = 200),
            )
        val sorted = items.sortedWith(FileSortKey.TIME.comparator(ascending = true, dirFirst = false))
        assertEquals(listOf("old.txt", "mid.txt", "new.txt"), sorted.map { it.name })
    }

    /** 按字节数降序。 */
    @Test
    fun `按字节数降序`() {
        val items =
            listOf(
                fileOf("small.txt", size = 10),
                fileOf("large.txt", size = 1000),
                fileOf("medium.txt", size = 100),
            )
        val sorted = items.sortedWith(FileSortKey.SIZE.comparator(ascending = false, dirFirst = false))
        assertEquals(listOf("large.txt", "medium.txt", "small.txt"), sorted.map { it.name })
    }

    /** 按扩展名排序，无扩展名排在最前。 */
    @Test
    fun `按扩展名忽略大小写排序`() {
        val items =
            listOf(
                fileOf("c.PNG", mtime = 100),
                fileOf("a", mtime = 100),
                fileOf("b.jpg", mtime = 100),
            )
        val sorted = items.sortedWith(FileSortKey.TYPE.comparator(ascending = true, dirFirst = false))
        assertEquals(listOf("a", "b.jpg", "c.PNG"), sorted.map { it.name })
    }

    /** 依据相同时回退到名称比较。 */
    @Test
    fun `依据相同时回退名称比较`() {
        val items =
            listOf(
                fileOf("b.txt", mtime = 100),
                fileOf("a.txt", mtime = 100),
            )
        val sorted = items.sortedWith(FileSortKey.TIME.comparator(ascending = true, dirFirst = false))
        assertEquals(listOf("a.txt", "b.txt"), sorted.map { it.name })
    }

    /** 持久化序号越界时回退为名称排序。 */
    @Test
    fun `越界序号回退名称排序`() {
        assertEquals(FileSortKey.NAME, FileSortKey.fromOrdinal(-1))
        assertEquals(FileSortKey.NAME, FileSortKey.fromOrdinal(99))
        assertEquals(FileSortKey.SIZE, FileSortKey.fromOrdinal(2))
    }

    private fun fileOf(
        name: String,
        size: Long = 0L,
        mtime: Long = 0L,
    ): FileItem = FileItem.file(name, "/tmp/$name", size, mtime, "text/plain")

    private fun dirOf(name: String): FileItem = FileItem.dir(name, "/tmp/$name", 0L)
}
