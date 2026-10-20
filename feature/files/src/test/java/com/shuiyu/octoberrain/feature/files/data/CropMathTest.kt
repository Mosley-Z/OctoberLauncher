package com.shuiyu.octoberrain.feature.files.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CropMath] 与 [CropRect] 的纯 JVM 测试。
 *
 * 覆盖旋转与镜像的分量变换、往返一致性、分量夹取、像素换算与最小边长判定。
 */
class CropMathTest {
    /** 整幅矩形旋转与镜像后仍是整幅矩形。 */
    @Test
    fun `整幅矩形保持整幅`() {
        assertEquals(CropRect.FULL, CropMath.rotate90(CropRect.FULL))
        assertEquals(CropRect.FULL, CropMath.mirror(CropRect.FULL))
        assertEquals(CropRect.FULL, CropMath.rotate(CropRect.FULL, 3))
    }

    /** 顺时针旋转 90 度的分量映射。 */
    @Test
    fun `旋转九十度分量映射`() {
        val source = CropRect(0.1f, 0.2f, 0.5f, 0.6f)
        val rotated = CropMath.rotate90(source)
        assertEquals(0.4f, rotated.left, 1e-6f)
        assertEquals(0.1f, rotated.top, 1e-6f)
        assertEquals(0.8f, rotated.right, 1e-6f)
        assertEquals(0.5f, rotated.bottom, 1e-6f)
    }

    /** 连续四次旋转回到原矩形。 */
    @Test
    fun `四次旋转回到原矩形`() {
        val source = CropRect(0.1f, 0.2f, 0.5f, 0.6f)
        assertEquals(source, CropMath.rotate(source, 4))
    }

    /** 水平镜像互换左右分量。 */
    @Test
    fun `镜像左右分量`() {
        val source = CropRect(0.1f, 0.2f, 0.5f, 0.6f)
        val mirrored = CropMath.mirror(source)
        assertEquals(0.5f, mirrored.left, 1e-6f)
        assertEquals(0.2f, mirrored.top, 1e-6f)
        assertEquals(0.9f, mirrored.right, 1e-6f)
        assertEquals(0.6f, mirrored.bottom, 1e-6f)
    }

    /** 连续两次镜像回到原矩形。浮点往返存在末位误差，按 1e-5 精度比较。 */
    @Test
    fun `两次镜像回到原矩形`() {
        val source = CropRect(0.1f, 0.2f, 0.5f, 0.6f)
        val restored = CropMath.mirror(CropMath.mirror(source))
        assertEquals(source.left, restored.left, 1e-5f)
        assertEquals(source.top, restored.top, 1e-5f)
        assertEquals(source.right, restored.right, 1e-5f)
        assertEquals(source.bottom, restored.bottom, 1e-5f)
    }

    /** 负数旋转次数归一到对应象限。 */
    @Test
    fun `负数旋转归一`() {
        val source = CropRect(0.1f, 0.2f, 0.5f, 0.6f)
        var expected = source
        repeat(3) { expected = CropMath.rotate90(expected) }
        assertEquals(expected, CropMath.rotate(source, -1))
    }

    /** 分量越界时夹取到 0 与 1 之间。 */
    @Test
    fun `越界分量夹取`() {
        val rect = CropRect.of(-0.2f, 0.3f, 1.4f, 0.9f)
        assertEquals(0f, rect.left, 1e-6f)
        assertEquals(1f, rect.right, 1e-6f)
        assertEquals(0.3f, rect.top, 1e-6f)
        assertEquals(0.9f, rect.bottom, 1e-6f)
    }

    /** 归一化矩形换算为像素矩形。 */
    @Test
    fun `像素矩形换算`() {
        val bounds = CropMath.pixels(CropRect(0.25f, 0.25f, 0.75f, 0.75f), 200, 400)
        assertEquals(50, bounds.left)
        assertEquals(100, bounds.top)
        assertEquals(100, bounds.width)
        assertEquals(200, bounds.height)
    }

    /** 退化为点的矩形仍保留一个像素宽高。 */
    @Test
    fun `退化矩形保留一像素`() {
        val bounds = CropMath.pixels(CropRect(0.5f, 0.5f, 0.5f, 0.5f), 100, 100)
        assertEquals(1, bounds.width)
        assertEquals(1, bounds.height)
    }

    /** 尺寸非正时返回全零像素矩形。 */
    @Test
    fun `非法尺寸返回零矩形`() {
        assertEquals(PixelRect(0, 0, 0, 0), CropMath.pixels(CropRect.FULL, 0, 100))
    }

    /** 最小边长按设备宽度区分。 */
    @Test
    fun `最小边长按宽度区分`() {
        assertEquals(32, CropMath.minEdge(480))
        assertEquals(48, CropMath.minEdge(552))
        assertEquals(48, CropMath.minEdge(824))
    }

    /** 裁剪结果需同时达到最小宽高。 */
    @Test
    fun `裁剪尺寸判定`() {
        val rect = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
        assertTrue(CropMath.isLargeEnough(rect, 200, 200, 48))
        assertFalse(CropMath.isLargeEnough(rect, 200, 200, 120))
    }

    /** 面积为零的矩形不视为合法裁剪。 */
    @Test
    fun `面积为零不合法`() {
        assertFalse(CropRect(0.5f, 0.5f, 0.5f, 0.5f).isValid)
        assertTrue(CropRect.FULL.isValid)
    }
}
