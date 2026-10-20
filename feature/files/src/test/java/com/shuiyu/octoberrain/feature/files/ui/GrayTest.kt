package com.shuiyu.octoberrain.feature.files.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Gray] 的纯 JVM 测试。
 *
 * 覆盖五档滤镜的确定性输出、对比度边界、透明通道策略与非法尺寸兜底。
 * 全部断言针对固定输入的像素值，不依赖设备与 Android 框架。
 */
class GrayTest {
    /** 原图灰阶档位按权重表计算红、绿、蓝与白。 */
    @Test
    fun `原图灰阶按权重计算`() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())
        val output = Gray.apply(pixels, 4, 1, GrayParams(level = 0))
        assertArrayEquals(intArrayOf(gray(76), gray(149), gray(29), gray(255)), output)
    }

    /** 透明通道默认沿用原值，关闭后输出不透明。 */
    @Test
    fun `透明通道策略`() {
        val pixel = 0x7F808080
        val kept = Gray.apply(intArrayOf(pixel), 1, 1, GrayParams(keepAlpha = true))
        val dropped = Gray.apply(intArrayOf(pixel), 1, 1, GrayParams(keepAlpha = false))
        assertEquals(0x7F, kept[0] ushr 24)
        assertEquals(0xFF, dropped[0] ushr 24)
        assertEquals(0x80, kept[0] and 0xFF)
    }

    /** 三档对比度围绕 128 缩放，128 本身保持不变。 */
    @Test
    fun `对比度三档缩放`() {
        val input = intArrayOf(gray(200), gray(128))
        val soft = Gray.apply(input, 2, 1, GrayParams(contrast = 0))
        val plain = Gray.apply(input, 2, 1, GrayParams(contrast = 1))
        val hard = Gray.apply(input, 2, 1, GrayParams(contrast = 2))
        assertEquals(189, soft[0] and 0xFF)
        assertEquals(200, plain[0] and 0xFF)
        assertEquals(211, hard[0] and 0xFF)
        assertEquals(128, soft[1] and 0xFF)
        assertEquals(128, hard[1] and 0xFF)
    }

    /** 对比度档位越界时按最大档处理。 */
    @Test
    fun `对比度档位越界夹取`() {
        val output = Gray.apply(intArrayOf(gray(200)), 1, 1, GrayParams(contrast = 9))
        assertEquals(211, output[0] and 0xFF)
    }

    /** 十六级抖动的单像素结果落在 17 的倍数上。 */
    @Test
    fun `十六级抖动单像素量化`() {
        val output = Gray.apply(intArrayOf(gray(100), gray(0), gray(255)), 3, 1, GrayParams(level = 1))
        assertEquals(102, output[0] and 0xFF)
        assertEquals(0, output[1] and 0xFF)
        assertEquals(255, output[2] and 0xFF)
    }

    /** 十六级抖动的多点输出均由 17 的倍数构成。 */
    @Test
    fun `十六级抖动输出为调色板值`() {
        val pixels = IntArray(16) { index -> gray(index * 16 + 1) }
        val output = Gray.apply(pixels, 4, 4, GrayParams(level = 1))
        output.forEach { value ->
            val level = value and 0xFF
            assertEquals(0, level % 17)
        }
    }

    /** 高反差档位以 128 为界输出黑白。 */
    @Test
    fun `高反差二值化`() {
        val output = Gray.apply(intArrayOf(gray(0), gray(100), gray(160), gray(255)), 4, 1, GrayParams(level = 2))
        assertEquals(0, output[0] and 0xFF)
        assertEquals(0, output[1] and 0xFF)
        assertEquals(255, output[2] and 0xFF)
        assertEquals(255, output[3] and 0xFF)
    }

    /** 有序抖动在第一行按 Bayer 阈值交替输出黑白。 */
    @Test
    fun `有序抖动首行图案`() {
        val pixels = IntArray(8) { gray(128) }
        val output = Gray.apply(pixels, 8, 1, GrayParams(level = 3))
        assertArrayEquals(intArrayOf(255, 0, 255, 0, 255, 0, 255, 0), IntArray(8) { output[it] and 0xFF })
    }

    /**
     * 柔和档位查表。
     *
     * 公式为伽马 0.85 后按 0.9 倍对比度围绕 128 缩放，端点随对比度收缩到 13 与 242，
     * 中灰 128 提升到 141。该收缩为参数定义的结果，不使用端点保持的写法。
     */
    @Test
    fun `柔和档位查表`() {
        val output = Gray.apply(intArrayOf(gray(0), gray(128), gray(255)), 3, 1, GrayParams(level = 4))
        assertEquals(13, output[0] and 0xFF)
        assertEquals(141, output[1] and 0xFF)
        assertEquals(242, output[2] and 0xFF)
    }

    /** 非法尺寸返回输入副本且不改写内容。 */
    @Test
    fun `非法尺寸返回副本`() {
        val pixels = intArrayOf(0xFF112233.toInt())
        val output = Gray.apply(pixels, 0, 3, GrayParams(level = 3))
        assertArrayEquals(pixels, output)
        assertTrue(pixels !== output)
    }

    /** 输入数组不被修改。 */
    @Test
    fun `输入数组保持不变`() {
        val pixels = intArrayOf(0x80112233.toInt(), 0x80445566.toInt())
        val original = pixels.copyOf()
        Gray.apply(pixels, 2, 1, GrayParams(level = 2, contrast = 2))
        assertArrayEquals(original, pixels)
    }

    /** 把灰阶值构造为不透明像素，便于断言。 */
    private fun gray(value: Int): Int = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
}
