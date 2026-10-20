package com.shuiyu.octoberrain.feature.files.ui.negative

import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.feature.files.data.MediaEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「最近图片」卡片纯逻辑用例。
 *
 * 覆盖条目排序与截取、非图片过滤、点击目标构造、空态文案与网格换算。
 */
class RecentImagesTest {
    /** 构造图片条目，时间取拍摄时间，宽高与方向使用固定值。 */
    private fun entry(
        name: String,
        time: Long,
        mime: String = "image/jpeg",
    ): MediaEntry =
        MediaEntry(
            id = 0L,
            path = "/pictures/$name",
            name = name,
            takenAt = time,
            addedAt = 0L,
            width = 100,
            height = 100,
            orientation = 0,
            mime = mime,
        )

    @Test
    fun `最近条目按时间倒序排列`() {
        val entries = listOf(entry("a.jpg", 100L), entry("b.jpg", 300L), entry("c.jpg", 200L))

        assertEquals(
            listOf("/pictures/b.jpg", "/pictures/c.jpg", "/pictures/a.jpg"),
            RecentImages.recentPaths(entries),
        )
    }

    @Test
    fun `同一时间戳按路径升序`() {
        val entries = listOf(entry("b.jpg", 100L), entry("a.jpg", 100L))

        assertEquals(
            listOf("/pictures/a.jpg", "/pictures/b.jpg"),
            RecentImages.recentPaths(entries),
        )
    }

    @Test
    fun `超出上限的条目被丢弃`() {
        val entries = (1..8).map { index -> entry("img-$index.jpg", index.toLong()) }

        val paths = RecentImages.recentPaths(entries)

        assertEquals(RecentImages.LIMIT, paths.size)
        assertEquals("/pictures/img-8.jpg", paths.first())
    }

    @Test
    fun `非正上限返回空列表`() {
        val entries = listOf(entry("a.jpg", 1L))

        assertTrue(RecentImages.recentPaths(entries, 0).isEmpty())
        assertTrue(RecentImages.recentPaths(entries, -1).isEmpty())
    }

    @Test
    fun `非图片条目被排除`() {
        val entries = listOf(entry("a.jpg", 200L), entry("note.txt", 300L, mime = "text/plain"))

        assertEquals(listOf("/pictures/a.jpg"), RecentImages.recentPaths(entries))
    }

    @Test
    fun `空列表的查看器目的地为空`() {
        assertNull(RecentImages.viewerTarget(emptyList(), 0))
    }

    @Test
    fun `查看器目的地携带卡片顺序与下标`() {
        val entries = listOf(entry("a.jpg", 100L), entry("b.jpg", 200L))

        val target = RecentImages.viewerTarget(entries, 1)

        assertEquals(listOf("/pictures/b.jpg", "/pictures/a.jpg"), target?.paths)
        assertEquals(1, target?.index)
    }

    @Test
    fun `越界下标夹取到有效范围`() {
        val entries = listOf(entry("a.jpg", 100L), entry("b.jpg", 200L))

        assertEquals(0, RecentImages.viewerTarget(entries, -5)?.index)
        assertEquals(1, RecentImages.viewerTarget(entries, 9)?.index)
    }

    @Test
    fun `全部图片目的地指向文件页`() {
        val target: Destination = RecentImages.filesTarget()

        assertTrue(target is Destination.Files)
        assertNull((target as Destination.Files).path)
    }

    @Test
    fun `空态文案固定`() {
        assertEquals("暂无图片", RecentImages.EMPTY_TEXT)
    }

    @Test
    fun `行数按三列换算`() {
        assertEquals(0, RecentImages.gridRows(0))
        assertEquals(1, RecentImages.gridRows(3))
        assertEquals(2, RecentImages.gridRows(6))
        assertEquals(3, RecentImages.gridRows(7))
    }

    @Test
    fun `缩略图边长按内容宽度换算`() {
        assertEquals(94, RecentImages.tileSize(300, 8))
    }

    @Test
    fun `内容宽度不足时边长至少为一`() {
        assertEquals(1, RecentImages.tileSize(1, 10))
    }
}
