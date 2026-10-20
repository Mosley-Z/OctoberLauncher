package com.shuiyu.octoberrain.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 壁纸居中覆盖裁剪窗口的几何测试。
 *
 * 覆盖竖屏与横屏源图、极端比例、边界夹取与非法尺寸，
 * 断言窗口比例与目标比例一致且落在源图范围内。
 */
class WallpaperGeometryTest {
    @Test
    fun `竖屏源图裁上下`() {
        // 800×1600 源图按目标宽度铺满后高度多出，纵向居中裁掉上下。
        val window = WallpaperGeometry.centerCrop(800, 1600, 480, 800)
        assertNotNull(window)
        requireNotNull(window)
        assertEquals(800, window.width)
        assertEquals(1333, window.height)
        assertEquals(0, window.left)
        assertEquals(134, window.top)
    }

    @Test
    fun `横屏源图裁上下`() {
        // 1600×800 源图放入 480×800 目标时按高度铺满并裁水平方向。
        val window = WallpaperGeometry.centerCrop(1600, 800, 480, 800)
        assertNotNull(window)
        requireNotNull(window)
        assertEquals(800, window.height)
        assertEquals(480, window.width)
        assertEquals(0, window.top)
        assertEquals(560, window.left)
    }

    @Test
    fun `正方形源图在竖屏目标下裁左右`() {
        val window = WallpaperGeometry.centerCrop(1000, 1000, 480, 800)
        requireNotNull(window)
        assertEquals(1000, window.height)
        assertEquals(600, window.width)
        assertEquals(200, window.left)
        assertEquals(0, window.top)
    }

    @Test
    fun `窗口比例等于目标比例`() {
        val window = WallpaperGeometry.centerCrop(3000, 2000, 480, 800)
        requireNotNull(window)
        val sourceRatio = window.width.toDouble() / window.height
        val targetRatio = 480.0 / 800.0
        assertEquals(targetRatio, sourceRatio, 0.002)
    }

    @Test
    fun `窗口不越出源图`() {
        val window = WallpaperGeometry.centerCrop(7, 11, 1000, 3000)
        requireNotNull(window)
        assertEquals(true, window.left >= 0)
        assertEquals(true, window.top >= 0)
        assertEquals(true, window.right <= 7)
        assertEquals(true, window.bottom <= 11)
        assertEquals(true, window.width >= 1)
        assertEquals(true, window.height >= 1)
    }

    @Test
    fun `宽高比一致时窗口覆盖整幅源图`() {
        val window = WallpaperGeometry.centerCrop(480, 800, 960, 1600)
        requireNotNull(window)
        assertEquals(CropWindow(0, 0, 480, 800), window)
    }

    @Test
    fun `源尺寸非法返回 null`() {
        assertNull(WallpaperGeometry.centerCrop(0, 800, 480, 800))
        assertNull(WallpaperGeometry.centerCrop(480, -1, 480, 800))
    }

    @Test
    fun `目标尺寸非法返回 null`() {
        assertNull(WallpaperGeometry.centerCrop(480, 800, 0, 800))
        assertNull(WallpaperGeometry.centerCrop(480, 800, 480, 0))
    }

    @Test
    fun `窗口宽高由边界差计算`() {
        val window = CropWindow(left = 3, top = 5, right = 13, bottom = 25)
        assertEquals(10, window.width)
        assertEquals(20, window.height)
    }
}
