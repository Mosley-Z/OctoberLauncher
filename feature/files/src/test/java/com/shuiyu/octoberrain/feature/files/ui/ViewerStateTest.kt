package com.shuiyu.octoberrain.feature.files.ui

import com.shuiyu.octoberrain.feature.files.ui.viewer.ViewerState
import com.shuiyu.octoberrain.feature.files.ui.viewer.ViewerText
import com.shuiyu.octoberrain.feature.files.ui.viewer.ViewerZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 查看器状态测试：核对页码、档位、解码目标与预取计划。 */
class ViewerStateTest {
    /** 起始下标越界时夹取到合法范围。 */
    @Test
    fun `起始下标夹取`() {
        assertEquals(0, ViewerState(listOf("a", "b", "c"), -3).index)
        assertEquals(2, ViewerState(listOf("a", "b", "c"), 9).index)
        assertEquals(0, ViewerState(emptyList(), 5).index)
    }

    /** 首张与末张的翻页边界。 */
    @Test
    fun `首张与末张的翻页边界`() {
        val state = ViewerState(listOf("a", "b", "c"))
        assertFalse(state.previous())
        assertTrue(state.next())
        assertEquals(1, state.index)
        assertTrue(state.next())
        assertFalse(state.next())
        assertEquals("c", state.path)
    }

    /** 页码文案在空列表与非空列表下的表现。 */
    @Test
    fun `页码文案`() {
        assertEquals("0/0", ViewerState(emptyList()).pageLabel)
        assertEquals("2/3", ViewerState(listOf("a", "b", "c"), 1).pageLabel)
    }

    /** 档位按适配、填充、原尺寸、适配循环，文案同步。 */
    @Test
    fun `档位循环顺序`() {
        val state = ViewerState(listOf("a"))
        assertEquals(ViewerZoom.FIT, state.zoom)
        assertEquals(ViewerZoom.FILL, state.cycleZoom())
        assertEquals("填充", state.zoomLabel)
        assertEquals(ViewerZoom.ORIGINAL, state.cycleZoom())
        assertEquals(ViewerZoom.FIT, state.cycleZoom())
    }

    /** 三档解码目标分别取短边、长边与长边两倍。 */
    @Test
    fun `三档解码目标`() {
        val state = ViewerState(listOf("a"))
        assertEquals(200, state.targetPx(300, 200))
        state.cycleZoom()
        assertEquals(300, state.targetPx(300, 200))
        state.cycleZoom()
        assertEquals(600, state.targetPx(300, 200))
    }

    /** 原尺寸档的解码目标受解码长边上限约束。 */
    @Test
    fun `原尺寸档受长边上限约束`() {
        val state = ViewerState(listOf("a"))
        state.cycleZoom()
        state.cycleZoom()
        assertEquals(3200, state.targetPx(1800, 2000))
    }

    /** 内容区尺寸无效时返回最小目标。 */
    @Test
    fun `内容区无效时返回最小目标`() {
        assertEquals(1, ViewerState(listOf("a")).targetPx(0, 0))
    }

    /** 预取计划只包含相邻且合法的下标。 */
    @Test
    fun `预取计划剔除越界项`() {
        val state = ViewerState(listOf("a", "b", "c"))
        assertEquals(listOf(1), state.prefetchIndices())
        state.next()
        assertEquals(listOf(0, 2), state.prefetchIndices())
        state.next()
        assertEquals(listOf(1), state.prefetchIndices())
        assertEquals(emptyList<Int>(), ViewerState(emptyList()).prefetchIndices())
    }

    /** 解码结果在代际或下标不符时被丢弃。 */
    @Test
    fun `解码结果校验`() {
        val state = ViewerState(listOf("a", "b"), 1)
        assertTrue(state.shouldAccept(3, 3, 1))
        assertFalse(state.shouldAccept(2, 3, 1))
        assertFalse(state.shouldAccept(3, 3, 0))
    }

    /** 灰度开关可反复切换，重新显示时只复位档位。 */
    @Test
    fun `灰度开关与档位复位`() {
        val state = ViewerState(listOf("a", "b"), 1)
        assertTrue(state.grayscale)
        assertFalse(state.toggleGrayscale())
        state.cycleZoom()
        state.resetZoom()
        assertEquals(ViewerZoom.FIT, state.zoom)
        assertEquals(1, state.index)
    }

    /** 省略号截断保留末尾并追加省略号。 */
    @Test
    fun `省略号截断`() {
        val text = "a-very-long-file-name.png"
        val trimmed = ViewerText.ellipsize(text) { it.length <= 10 }
        assertTrue(trimmed.length <= 10)
        assertTrue(trimmed.endsWith("…"))
        assertEquals("short.png", ViewerText.ellipsize("short.png") { true })
    }

    /** 大小与时间的格式化。 */
    @Test
    fun `大小与时间格式化`() {
        assertEquals("0 B", ViewerText.formatSize(-5))
        assertEquals("512 B", ViewerText.formatSize(512))
        assertEquals("1.5 KB", ViewerText.formatSize(1536))
        assertEquals("", ViewerText.formatTime(0))
    }

    /** 格式行取大写扩展名。 */
    @Test
    fun `格式行取大写扩展名`() {
        assertEquals("PNG", ViewerText.formatLabel("cover.png"))
        assertEquals("未知", ViewerText.formatLabel("README"))
    }
}
