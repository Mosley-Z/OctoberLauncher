package com.shuiyu.octoberrain.feature.settings

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BeautifyPreviewState 的状态写入与清理单元测试。
 *
 * 该对象只保存内存中的顺序与页号，不触碰 Android 框架，
 * 因此可以直接在本地 JVM 中验证写入、覆盖与清理行为。
 */
class BeautifyPreviewStateTest {
    /** 每个用例结束后清空共享状态，避免用例之间互相影响。 */
    @After
    fun tearDown() {
        BeautifyPreviewState.clear()
    }

    /** 初始状态为空，版本号为 0。 */
    @Test
    fun `初始状态为空`() {
        assertEquals(0L, BeautifyPreviewState.version)
        assertEquals(0, BeautifyPreviewState.pageIndex)
        assertTrue(BeautifyPreviewState.workingKeys.isEmpty())
        assertTrue(BeautifyPreviewState.pages.isEmpty())
        assertTrue(BeautifyPreviewState.labels.isEmpty())
        assertFalse(BeautifyPreviewState.isFilled)
    }

    /** 写入后各字段就绪，版本号加一，状态标记为已填充。 */
    @Test
    fun `写入后状态就绪`() {
        BeautifyPreviewState.put(
            pageIndex = 1,
            workingKeys = listOf("app:a", "app:b"),
            pages = listOf(listOf("app:a", "app:b"), listOf("app:c")),
            labels = mapOf("app:a" to "甲", "app:b" to "乙"),
        )
        assertEquals(1L, BeautifyPreviewState.version)
        assertEquals(1, BeautifyPreviewState.pageIndex)
        assertEquals(listOf("app:a", "app:b"), BeautifyPreviewState.workingKeys)
        assertEquals(2, BeautifyPreviewState.pages.size)
        assertEquals("甲", BeautifyPreviewState.labels["app:a"])
        assertTrue(BeautifyPreviewState.isFilled)
    }

    /** 外部列表在写入后继续修改，不影响已保存状态。 */
    @Test
    fun `写入时复制传入集合`() {
        val keys = mutableListOf("app:a")
        val pages = mutableListOf(listOf("app:a"))
        val labels = mutableMapOf("app:a" to "甲")
        BeautifyPreviewState.put(0, keys, pages, labels)

        keys.add("app:b")
        pages.add(listOf("app:b"))
        labels["app:a"] = "乙"

        assertEquals(listOf("app:a"), BeautifyPreviewState.workingKeys)
        assertEquals(1, BeautifyPreviewState.pages.size)
        assertEquals("甲", BeautifyPreviewState.labels["app:a"])
        assertNotSame(keys, BeautifyPreviewState.workingKeys)
    }

    /** 页号小于 0 时按 0 记录，避免预览页出现负下标。 */
    @Test
    fun `负页号收敛到零`() {
        BeautifyPreviewState.put(-3, emptyList(), listOf(listOf("app:a")), emptyMap())
        assertEquals(0, BeautifyPreviewState.pageIndex)
    }

    /** 再次写入替换旧值，版本号累计。 */
    @Test
    fun `重复写入替换旧状态`() {
        BeautifyPreviewState.put(0, listOf("app:a"), listOf(listOf("app:a")), emptyMap())
        BeautifyPreviewState.put(2, listOf("app:b"), listOf(listOf("app:b"), emptyList()), emptyMap())
        assertEquals(2L, BeautifyPreviewState.version)
        assertEquals(2, BeautifyPreviewState.pageIndex)
        assertEquals(listOf("app:b"), BeautifyPreviewState.workingKeys)
        assertEquals(2, BeautifyPreviewState.pages.size)
    }

    /** 清理后回到初始状态。 */
    @Test
    fun `清理后回到初始状态`() {
        BeautifyPreviewState.put(1, listOf("app:a"), listOf(listOf("app:a")), mapOf("app:a" to "甲"))
        BeautifyPreviewState.clear()
        assertEquals(0L, BeautifyPreviewState.version)
        assertEquals(0, BeautifyPreviewState.pageIndex)
        assertTrue(BeautifyPreviewState.workingKeys.isEmpty())
        assertTrue(BeautifyPreviewState.pages.isEmpty())
        assertTrue(BeautifyPreviewState.labels.isEmpty())
        assertFalse(BeautifyPreviewState.isFilled)
    }
}
