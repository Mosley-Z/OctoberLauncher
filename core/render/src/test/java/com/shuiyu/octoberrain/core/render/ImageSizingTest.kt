package com.shuiyu.octoberrain.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ImageSizing] 的尺寸与采样决策测试，全部在本地 JVM 上执行。 */
class ImageSizingTest {
    @Test
    fun `长边小于目标的两倍时采样倍率为一`() {
        assertEquals(1, ImageSizing.sampleFor(longEdge = 1000, minPx = 800))
    }

    @Test
    fun `长边超过目标的两倍时采样倍率翻倍`() {
        assertEquals(2, ImageSizing.sampleFor(longEdge = 3000, minPx = 800))
        assertEquals(4, ImageSizing.sampleFor(longEdge = 6000, minPx = 800))
    }

    @Test
    fun `期望边长小于一时按一处理`() {
        assertEquals(1, ImageSizing.targetEdge(0))
        assertEquals(1, ImageSizing.targetEdge(-5))
        assertEquals(64, ImageSizing.sampleFor(longEdge = 100, minPx = 0))
    }

    @Test
    fun `期望边长超过上限时压到一千六百`() {
        assertEquals(ImageSizing.MAX_TARGET, ImageSizing.targetEdge(5000))
        assertEquals(ImageSizing.MAX_TARGET, ImageSizing.targetEdge(ImageSizing.MAX_TARGET))
    }

    @Test
    fun `任意期望边长下采样结果不超过长边上限`() {
        val sizes = listOf(3200, 3201, 6400, 12_000, 20_000)
        val requests = listOf(0, 1, 512, 1600, 9000)
        for (longEdge in sizes) {
            for (request in requests) {
                val sample = ImageSizing.sampleFor(longEdge, request)
                assertTrue("长边 $longEdge 期望 $request", longEdge / sample <= ImageSizing.MAX_EDGE)
            }
        }
    }

    @Test
    fun `宽高为零或负数时不在上限内`() {
        assertFalse(ImageSizing.withinLimit(0, 100))
        assertFalse(ImageSizing.withinLimit(100, -1))
        assertTrue(ImageSizing.withinLimit(4000, 4000))
    }

    @Test
    fun `像素总数超过八千万时不在上限内`() {
        assertTrue(ImageSizing.withinLimit(10_000, 8_000))
        assertFalse(ImageSizing.withinLimit(10_000, 8_001))
    }

    @Test
    fun `双方向目标尺寸同时满足时才继续降采样`() {
        assertEquals(4, ImageSizing.requestSample(1080, 1920, 200, 200))
        assertEquals(2, ImageSizing.requestSample(1080, 1920, 540, 960))
    }

    @Test
    fun `双方向目标为零时采样倍率为一`() {
        assertEquals(1, ImageSizing.requestSample(4000, 3000, 0, 0))
        assertEquals(1, ImageSizing.requestSample(4000, 3000, -2, 0))
    }

    @Test
    fun `图标采样按像素总量兜底`() {
        val sample = ImageSizing.requestSample(8000, 8000, 1, 1)
        assertTrue(
            ImageSizing.sampledPixels(8000, 8000, sample) <= ImageSizing.ICON_MAX_PIXELS,
        )
    }

    @Test
    fun `采样后的像素数至少为一`() {
        assertEquals(1L, ImageSizing.sampledPixels(1, 1, 8))
        assertEquals(4L, ImageSizing.sampledPixels(8, 8, 4))
    }

    @Test
    fun `堆余量足够时允许解码`() {
        val pixels = 1000L * 1000L
        val bytes = pixels * ImageSizing.BYTES_PER_PIXEL
        assertTrue(ImageSizing.fitsHeap(1000, 1000, 1, bytes * 2))
        assertFalse(ImageSizing.fitsHeap(1000, 1000, 1, bytes * 2 - 1))
    }
}
