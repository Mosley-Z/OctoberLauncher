package com.shuiyu.octoberrain.feature.files.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 查看器解码计划的单元测试。
 *
 * 断言 [ViewerDecoder.requestFor] 按三档缩放给出目标像素，并透传灰度量化开关。
 * 不构造位图与 Android 图形类，仅验证纯计算部分。
 */
class ViewerDecodePlanTest {
    /** 单图查看器状态。 */
    private fun state(): ViewerState = ViewerState(listOf("/sdcard/Pictures/a.jpg"))

    /** 适配档取内容区短边。 */
    @Test
    fun `适配档取内容区短边`() {
        val request = ViewerDecoder.requestFor(state(), contentWidth = 480, contentHeight = 700)
        assertEquals(480, request.minPx)
        assertTrue(request.quantized)
    }

    /** 填充档取内容区长边。 */
    @Test
    fun `填充档取内容区长边`() {
        val viewer = state()
        viewer.cycleZoom()
        assertEquals(ViewerZoom.FILL, viewer.zoom)
        val request = ViewerDecoder.requestFor(viewer, contentWidth = 480, contentHeight = 700)
        assertEquals(700, request.minPx)
    }

    /** 原尺寸档取内容区长边的两倍。 */
    @Test
    fun `原尺寸档取长边两倍`() {
        val viewer = state()
        viewer.cycleZoom()
        viewer.cycleZoom()
        assertEquals(ViewerZoom.ORIGINAL, viewer.zoom)
        val request = ViewerDecoder.requestFor(viewer, contentWidth = 480, contentHeight = 700)
        assertEquals(1400, request.minPx)
    }

    /** 原尺寸档的目标像素受解码上限约束。 */
    @Test
    fun `原尺寸档受解码上限约束`() {
        val viewer = state()
        viewer.cycleZoom()
        viewer.cycleZoom()
        val request = ViewerDecoder.requestFor(viewer, contentWidth = 2400, contentHeight = 3200)
        assertEquals(3200, request.minPx)
    }

    /** 关闭灰度后请求不再量化。 */
    @Test
    fun `关闭灰度后不量化`() {
        val viewer = state()
        viewer.toggleGrayscale()
        assertFalse(viewer.grayscale)
        val request = ViewerDecoder.requestFor(viewer, contentWidth = 480, contentHeight = 700)
        assertFalse(request.quantized)
    }
}
