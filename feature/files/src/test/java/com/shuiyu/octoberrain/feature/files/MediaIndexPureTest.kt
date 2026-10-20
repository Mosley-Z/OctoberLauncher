package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.data.MediaBuckets
import com.shuiyu.octoberrain.feature.files.data.MediaEntry
import com.shuiyu.octoberrain.feature.files.data.MediaIndexCodec
import com.shuiyu.octoberrain.feature.files.data.MediaIndexData
import com.shuiyu.octoberrain.feature.files.data.MediaIndexMerge
import com.shuiyu.octoberrain.feature.files.data.MediaTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 相册索引纯逻辑用例。
 *
 * 覆盖时间换算与跨月跨年边界、拍摄时间缺失时的排序回退、JSON 编解码的容错、
 * 增量合并的路径去重与重命名处理。用例不加载 Android 类，全部在本地 JVM 执行。
 */
class MediaIndexPureTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 构造条目，id 与路径由参数决定。 */
    private fun entry(
        path: String,
        takenAt: Long,
        addedAt: Long = 0L,
        id: Long = 0L,
    ): MediaEntry =
        MediaEntry(
            id = id,
            path = path,
            name = path.substringAfterLast('/'),
            takenAt = takenAt,
            addedAt = addedAt,
            width = 0,
            height = 0,
            orientation = 0,
            mime = "image/jpeg",
        )

    /** 取指定日期时间的时间戳。 */
    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
    ): Long = MediaTime.toMillis(LocalDateTime.of(year, month, day, hour, minute), zone)

    @Test
    fun `拍摄时间缺失时按加入时间排序`() {
        val noTaken = entry("/a.jpg", takenAt = 0L, addedAt = millis(2026, 10, 19, 8))
        val withTaken = entry("/b.jpg", takenAt = millis(2026, 10, 19, 9))
        val sorted = MediaBuckets.sorted(listOf(noTaken, withTaken))
        assertEquals(listOf("/b.jpg", "/a.jpg"), sorted.map { it.path })
    }

    @Test
    fun `同一时间按路径升序保持稳定`() {
        val time = millis(2026, 10, 19)
        val sorted = MediaBuckets.sorted(listOf(entry("/c.jpg", time), entry("/a.jpg", time), entry("/b.jpg", time)))
        assertEquals(listOf("/a.jpg", "/b.jpg", "/c.jpg"), sorted.map { it.path })
    }

    @Test
    fun `同日跨零点按不同日期分组`() {
        val before = entry("/before.jpg", millis(2026, 10, 19, 23, 59))
        val after = entry("/after.jpg", millis(2026, 10, 20, 0, 1))
        val days = MediaBuckets.days(listOf(before, after), zone)
        assertEquals(listOf("2026-10-20", "2026-10-19"), days.map { it.key })
        assertEquals(listOf(1, 1), days.map { it.count })
    }

    @Test
    fun `跨月与跨年分桶正确`() {
        val january = entry("/jan.jpg", millis(2026, 1, 31, 23, 59))
        val february = entry("/feb.jpg", millis(2026, 2, 1, 0, 1))
        val december = entry("/dec.jpg", millis(2025, 12, 31, 23, 59))
        val days = MediaBuckets.days(listOf(january, february, december), zone)
        assertEquals(listOf("2026-02-01", "2026-01-31", "2025-12-31"), days.map { it.key })
        val months = MediaBuckets.months(days, zone)
        assertEquals(listOf("2026-02", "2026-01", "2025-12"), months.map { it.key })
        assertEquals(listOf(1, 1, 1), months.map { it.count })
    }

    @Test
    fun `月份聚合累计数量并取首张封面`() {
        val first = entry("/a.jpg", millis(2026, 10, 19, 18))
        val second = entry("/b.jpg", millis(2026, 10, 18, 9))
        val days = MediaBuckets.days(listOf(first, second), zone)
        val months = MediaBuckets.months(days, zone)
        assertEquals(1, months.size)
        assertEquals(2, months.first().count)
        assertEquals("/a.jpg", months.first().coverPath)
        assertEquals("2026年10月", months.first().title)
    }

    @Test
    fun `日分组标题为月日格式`() {
        val time = millis(2026, 10, 9)
        assertEquals("10月9日", MediaTime.dayTitle(time, zone))
    }

    @Test
    fun `条目键与索引快照分桶一致`() {
        val data =
            MediaIndexData(
                updatedAt = millis(2026, 10, 19),
                entries = listOf(entry("/a.jpg", millis(2026, 10, 19, 9)), entry("/b.jpg", millis(2026, 10, 18, 9))),
            )
        assertEquals(2, data.days.size)
        assertEquals(2, data.entries.size)
        assertEquals(1, data.months.size)
    }

    @Test
    fun `编码解码往返保持字段`() {
        val data =
            MediaIndexData(
                updatedAt = 1234L,
                entries = listOf(entry("/p/a.jpg", 999L, id = 7L)),
            )
        val decoded = MediaIndexCodec.decode(MediaIndexCodec.encode(data))
        assertNotNull(decoded)
        assertEquals(1234L, decoded!!.updatedAt)
        assertEquals(1, decoded.entries.size)
        assertEquals("/p/a.jpg", decoded.entries.first().path)
        assertEquals(7L, decoded.entries.first().id)
    }

    @Test
    fun `解码拒绝非法内容`() {
        assertNull(MediaIndexCodec.decode("not json"))
        assertNull(MediaIndexCodec.decode("{}"))
        assertNull(MediaIndexCodec.decode("{\"version\":1}"))
    }

    @Test
    fun `解码拒绝版本不一致`() {
        val encoded = MediaIndexCodec.encode(MediaIndexData(updatedAt = 1L, entries = emptyList()))
        val tampered = encoded.replace("\"version\":1", "\"version\":9")
        assertNull(MediaIndexCodec.decode(tampered))
    }

    @Test
    fun `解码拒绝缺失字段`() {
        val broken =
            "{\"version\":1,\"updatedAt\":1,\"entries\":[{\"id\":1,\"path\":\"/a.jpg\"}]}"
        assertNull(MediaIndexCodec.decode(broken))
    }

    @Test
    fun `合并按路径去重并采用新记录`() {
        val old = entry("/a.jpg", 100L, id = 1L)
        val renamed = entry("/a.jpg", 200L, id = 1L)
        val merged = MediaIndexMerge.merge(listOf(old), listOf(renamed))
        assertEquals(1, merged.size)
        assertEquals(200L, merged.first().takenAt)
    }

    @Test
    fun `合并保留未出现在新结果中的条目`() {
        val existing = listOf(entry("/a.jpg", 100L), entry("/b.jpg", 50L))
        val incoming = listOf(entry("/c.jpg", 300L))
        val merged = MediaIndexMerge.merge(existing, incoming)
        assertEquals(listOf("/c.jpg", "/a.jpg", "/b.jpg"), merged.map { it.path })
    }

    @Test
    fun `合并时同一媒体行号保留较新路径`() {
        val oldPath = entry("/old.jpg", 100L, id = 9L)
        val newPath = entry("/new.jpg", 200L, id = 9L)
        val merged = MediaIndexMerge.merge(listOf(oldPath), listOf(newPath))
        assertEquals(1, merged.size)
        assertTrue(merged.first().path.endsWith("/new.jpg"))
    }

    @Test
    fun `空增量结果返回原条目并保持排序`() {
        val existing = listOf(entry("/a.jpg", 100L), entry("/b.jpg", 200L))
        val merged = MediaIndexMerge.merge(existing, emptyList())
        assertEquals(listOf("/b.jpg", "/a.jpg"), merged.map { it.path })
    }
}
