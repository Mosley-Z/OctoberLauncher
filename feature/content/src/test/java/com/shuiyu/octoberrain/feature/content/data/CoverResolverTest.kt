package com.shuiyu.octoberrain.feature.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CoverResolver 封面词干生成的单元测试。
 *
 * 词干由可读书名与路径散列后缀拼成，只依赖 java.io.File，
 * 不读取文件系统也不触碰 Android 框架，可在本地 JVM 中验证。
 */
class CoverResolverTest {
    /** 按生产实现的口径计算词干后缀，便于逐项断言。 */
    private fun suffixOf(book: File): String = Integer.toHexString(book.absolutePath.hashCode())

    /** 普通中文书名保留字母与数字，并以路径散列后缀结尾。 */
    @Test
    fun `中文书名保留可读部分`() {
        val book = File("C:/books/科幻世界.epub")
        val stem = CoverResolver.stem(book)
        assertEquals("科幻世界_${suffixOf(book)}", stem)
    }

    /** 空格与符号逐字符替换为下划线，词干与后缀之间保留一个分隔下划线。 */
    @Test
    fun `非法字符替换为下划线`() {
        val book = File("C:/books/a b@c#.epub")
        val stem = CoverResolver.stem(book)
        assertTrue(stem.startsWith("a_b_c_"))
        // 末位 `#` 替换为下划线后，与“可读部分_散列”的分隔下划线相邻，形成两个连续下划线。
        assertEquals("a_b_c__${suffixOf(book)}", stem)
    }

    /** 不同目录下的同名书籍得到不同词干，缓存封面不会互相覆盖。 */
    @Test
    fun `不同目录同名书籍词干不同`() {
        val first = CoverResolver.stem(File("C:/library/one/book.epub"))
        val second = CoverResolver.stem(File("C:/library/two/book.epub"))
        assertNotEquals(first, second)
        assertTrue(first.startsWith("book_"))
        assertTrue(second.startsWith("book_"))
    }

    /** 可读部分截断到 40 个字符，避免文件名过长。 */
    @Test
    fun `可读部分截断到四十字符`() {
        val name = "a".repeat(60)
        val book = File("C:/books/$name.epub")
        val stem = CoverResolver.stem(book)
        val readable = "a".repeat(40)
        assertEquals("${readable}_${suffixOf(book)}", stem)
    }

    /** 书名不含可用字符时回退为 cover 前缀，保证词干非空。 */
    @Test
    fun `空书名回退到 cover 前缀`() {
        val book = File("C:/books/.epub")
        val stem = CoverResolver.stem(book)
        assertEquals("cover_${suffixOf(book)}", stem)
    }
}
