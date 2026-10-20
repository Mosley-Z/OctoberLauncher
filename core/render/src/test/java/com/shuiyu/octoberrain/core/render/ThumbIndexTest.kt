package com.shuiyu.octoberrain.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 缩略图缓存索引的隔离与持久化测试。
 *
 * 同一磁盘目录下不同目标长边或抖动开关的缓存实例各自持有索引文件，写入、失效与重建
 * 互不影响；测试直接验证索引文件层面的行为，不构造位图，避免引入 Android 运行时依赖。
 */
class ThumbIndexTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** 索引文件名包含目标长边与抖动标记，实例之间不会指向同一文件。 */
    @Test
    fun `索引文件名按目标长边与抖动开关区分`() {
        val directory = folder.newFolder("thumbs")
        val large = ThumbIndex(directory, targetEdge = 256, ditherEnabled = true)
        val small = ThumbIndex(directory, targetEdge = 128, ditherEnabled = true)
        val plain = ThumbIndex(directory, targetEdge = 256, ditherEnabled = false)

        assertEquals("index-256-dither1.txt", large.file().name)
        assertEquals("index-128-dither1.txt", small.file().name)
        assertEquals("index-256-dither0.txt", plain.file().name)
    }

    /** 两个实例先后写入同一来源路径，各自索引只保留自身条目，磁盘保留两份 PNG。 */
    @Test
    fun `两个实例的索引互不覆盖`() {
        val directory = folder.newFolder("thumbs")
        val digest = "source-digest"
        val largeKey = "large-key"
        val smallKey = "small-key"

        val large = ThumbIndex(directory, targetEdge = 256, ditherEnabled = true)
        large.add(digest, largeKey)
        large.save()
        File(directory, "$largeKey.png").writeText("large")

        val small = ThumbIndex(directory, targetEdge = 128, ditherEnabled = true)
        small.add(digest, smallKey)
        small.save()
        File(directory, "$smallKey.png").writeText("small")

        val reopenedLarge = ThumbIndex(directory, targetEdge = 256, ditherEnabled = true)
        val reopenedSmall = ThumbIndex(directory, targetEdge = 128, ditherEnabled = true)

        assertEquals(setOf(largeKey), reopenedLarge.keys(digest))
        assertEquals(setOf(smallKey), reopenedSmall.keys(digest))
        assertTrue(File(directory, "$largeKey.png").isFile)
        assertTrue(File(directory, "$smallKey.png").isFile)
    }

    /** 一个实例失效后，另一个实例的索引条目与 PNG 仍然存在，重建实例可继续命中。 */
    @Test
    fun `单实例失效保留其他实例条目`() {
        val directory = folder.newFolder("thumbs")
        val digest = "source-digest"
        val largeKey = "large-key"
        val smallKey = "small-key"

        val large = ThumbIndex(directory, targetEdge = 256, ditherEnabled = true)
        large.add(digest, largeKey)
        large.save()
        File(directory, "$largeKey.png").writeText("large")

        val small = ThumbIndex(directory, targetEdge = 128, ditherEnabled = true)
        small.add(digest, smallKey)
        small.save()
        File(directory, "$smallKey.png").writeText("small")

        val removed = large.remove(digest)
        large.save()
        removed.forEach { key -> File(directory, "$key.png").delete() }

        val reopenedLarge = ThumbIndex(directory, targetEdge = 256, ditherEnabled = true)
        val reopenedSmall = ThumbIndex(directory, targetEdge = 128, ditherEnabled = true)

        assertTrue(reopenedLarge.keys(digest).isEmpty())
        assertFalse(File(directory, "$largeKey.png").exists())
        assertEquals(setOf(smallKey), reopenedSmall.keys(digest))
        assertTrue(File(directory, "$smallKey.png").isFile)
    }

    /** 旧版共享索引文件不再读取，旧 PNG 交由磁盘清理自然淘汰。 */
    @Test
    fun `旧版共享索引文件不再读取`() {
        val directory = folder.newFolder("thumbs")
        File(directory, "index.txt").writeText("legacy-digest legacy-key")

        val index = ThumbIndex(directory, targetEdge = 256, ditherEnabled = true)

        assertTrue(index.keys("legacy-digest").isEmpty())
    }
}
