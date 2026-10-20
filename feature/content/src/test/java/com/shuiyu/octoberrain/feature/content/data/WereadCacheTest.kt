package com.shuiyu.octoberrain.feature.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 两级统计缓存的单元测试。
 *
 * 覆盖内存命中、磁盘复用、六期淘汰、按模式与整体失效，全部在临时目录中完成。
 */
class WereadCacheTest {
    private fun directory(name: String): File = File(Files.createTempDirectory(name).toFile(), "cache")

    /** 写入后立即读取命中内存副本。 */
    @Test
    fun `内存写入后可读取`() {
        val cache = WereadCache(directory("weread-cache-memory"))
        cache.put("weekly", 100L, "payload")
        assertEquals("payload", cache.get("weekly", 100L))
    }

    /** 不同模式的同一起点缓存键互不覆盖。 */
    @Test
    fun `模式参与缓存键`() {
        val cache = WereadCache(directory("weread-cache-mode"))
        cache.put("weekly", 100L, "week")
        cache.put("monthly", 100L, "month")
        assertEquals("week", cache.get("weekly", 100L))
        assertEquals("month", cache.get("monthly", 100L))
    }

    /** 未写入的周期读取返回 null。 */
    @Test
    fun `未命中返回空`() {
        val cache = WereadCache(directory("weread-cache-miss"))
        assertNull(cache.get("weekly", 100L))
    }

    /** 磁盘目录中的条目可被新的实例读取。 */
    @Test
    fun `磁盘缓存跨实例复用`() {
        val root = directory("weread-cache-disk")
        WereadCache(root).put("weekly", 100L, "persisted")
        assertEquals("persisted", WereadCache(root).get("weekly", 100L))
    }

    /** 超过六个周期时按起点从旧到新淘汰。 */
    @Test
    fun `六期淘汰最旧条目`() {
        val cache = WereadCache(directory("weread-cache-evict"))
        for (index in 1..7) cache.put("weekly", index * 100L, "p$index")
        val periods = cache.periods()
        assertEquals(6, periods.size)
        assertEquals(listOf(200L, 300L, 400L, 500L, 600L, 700L), periods)
        assertNull(cache.get("weekly", 100L))
    }

    /** 按模式失效只删除该模式条目。 */
    @Test
    fun `按模式失效保留其它模式`() {
        val cache = WereadCache(directory("weread-cache-invalidate-mode"))
        cache.put("weekly", 100L, "week")
        cache.put("monthly", 100L, "month")
        cache.invalidate("weekly")
        assertNull(cache.get("weekly", 100L))
        assertEquals("month", cache.get("monthly", 100L))
        assertTrue(cache.periods().contains(100L))
    }

    /** 整体失效清空内存与磁盘。 */
    @Test
    fun `整体失效清空全部条目`() {
        val cache = WereadCache(directory("weread-cache-invalidate-all"))
        cache.put("weekly", 100L, "week")
        cache.put("monthly", 200L, "month")
        cache.invalidate()
        assertTrue(cache.periods().isEmpty())
        assertNull(cache.get("weekly", 100L))
    }
}
