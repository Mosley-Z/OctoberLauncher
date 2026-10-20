package com.shuiyu.octoberrain.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ThumbKey] 的摘要计算测试，全部在本地 JVM 上执行。 */
class ThumbKeyTest {
    private val path = "/storage/emulated/0/Books/示例.epub"

    @Test
    fun `相同参数的键一致`() {
        val first = ThumbKey.of(path, 1_000L, 256)
        val second = ThumbKey.of(path, 1_000L, 256)
        assertEquals(first, second)
    }

    @Test
    fun `路径变化后键不同`() {
        assertNotEquals(
            ThumbKey.of(path, 1_000L, 256),
            ThumbKey.of("$path.bak", 1_000L, 256),
        )
    }

    @Test
    fun `修改时间变化后键不同`() {
        assertNotEquals(
            ThumbKey.of(path, 1_000L, 256),
            ThumbKey.of(path, 1_001L, 256),
        )
    }

    @Test
    fun `目标长边变化后键不同`() {
        assertNotEquals(
            ThumbKey.of(path, 1_000L, 256),
            ThumbKey.of(path, 1_000L, 512),
        )
    }

    @Test
    fun `键为六十四位十六进制串`() {
        val key = ThumbKey.of(path, 1_000L, 256)
        assertEquals(64, key.length)
        assertTrue(key.all { it in "0123456789abcdef" })
    }

    @Test
    fun `键中不包含明文路径`() {
        val key = ThumbKey.of(path, 1_000L, 256)
        assertTrue(!key.contains("Books"))
        assertTrue(!key.contains("示例"))
    }

    @Test
    fun `路径摘要与完整键不同且长度一致`() {
        val digest = ThumbKey.pathDigest(path)
        assertEquals(64, digest.length)
        assertNotEquals(digest, ThumbKey.of(path, 1_000L, 256))
    }

    @Test
    fun `摘要函数输出固定值`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ThumbKey.sha256Hex(""),
        )
    }
}
