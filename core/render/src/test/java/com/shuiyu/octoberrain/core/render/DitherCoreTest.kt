package com.shuiyu.octoberrain.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [DitherCore] 的档位与灰度计算测试，全部在本地 JVM 上执行。 */
class DitherCoreTest {
    @Test
    fun `二四八档原样保留`() {
        assertEquals(2, DitherCore.normalizeLevels(2))
        assertEquals(4, DitherCore.normalizeLevels(4))
        assertEquals(8, DitherCore.normalizeLevels(8))
    }

    @Test
    fun `非法档位按四档处理`() {
        val invalid = listOf(-1, 0, 1, 3, 5, 6, 7, 9, 16, 256)
        for (levels in invalid) {
            assertEquals("档位 $levels", DitherCore.DEFAULT_LEVELS, DitherCore.normalizeLevels(levels))
        }
    }

    @Test
    fun `调色板长度与档位一致`() {
        for (levels in listOf(2, 4, 8)) {
            assertEquals(levels, DitherCore.paletteOf(levels).size)
        }
    }

    @Test
    fun `调色板由浅到深且包含两端`() {
        for (levels in listOf(2, 4, 8)) {
            val palette = DitherCore.paletteOf(levels)
            assertEquals(255, palette.first())
            assertEquals(0, palette.last())
            for (index in 1 until palette.size) {
                assertTrue("档位 $levels 第 $index 项应更暗", palette[index] < palette[index - 1])
            }
        }
    }

    @Test
    fun `四档调色板沿用既有取值`() {
        assertEquals(listOf(255, 172, 96, 0), DitherCore.paletteOf(4).toList())
    }

    @Test
    fun `最近档位选择距离最小的值`() {
        val palette = DitherCore.paletteOf(4)
        assertEquals(255, DitherCore.nearest(palette, 250f))
        assertEquals(172, DitherCore.nearest(palette, 180f))
        assertEquals(96, DitherCore.nearest(palette, 100f))
        assertEquals(0, DitherCore.nearest(palette, 10f))
    }

    @Test
    fun `亮度按权值合成且范围为浮点`() {
        assertEquals(255f, DitherCore.luma(255, 255, 255), 0.01f)
        assertEquals(0f, DitherCore.luma(0, 0, 0), 0.01f)
        assertEquals(0.299f * 255f, DitherCore.luma(255, 0, 0), 0.01f)
        assertEquals(0.587f * 255f, DitherCore.luma(0, 255, 0), 0.01f)
        assertEquals(0.114f * 255f, DitherCore.luma(0, 0, 255), 0.01f)
    }

    @Test
    fun `灰度转换保留亮度并置为不透明`() {
        val gray = DitherCore.grayscale(intArrayOf(0x00FF0000, 0xFF00FF00.toInt(), 0x7F0000FF))
        assertEquals(0xFF4C4C4C.toInt(), gray[0])
        assertEquals(0xFF959595.toInt(), gray[1])
        assertEquals(0xFF1D1D1D.toInt(), gray[2])
    }
}
