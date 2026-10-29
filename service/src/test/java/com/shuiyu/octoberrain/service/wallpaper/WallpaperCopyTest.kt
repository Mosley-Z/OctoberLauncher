package com.shuiyu.octoberrain.service.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 壁纸副本命名测试。
 *
 * 覆盖厂商可识别扩展名判定与固定前缀，校验副本名规则不随重构漂移。
 */
class WallpaperCopyTest {
    /** 厂商可识别扩展名不区分大小写。 */
    @Test
    fun supportedNames() {
        assertTrue(WallpaperCopy.isSupportedName("a.jpg"))
        assertTrue(WallpaperCopy.isSupportedName("a.JPEG"))
        assertTrue(WallpaperCopy.isSupportedName("a.png"))
        assertTrue(WallpaperCopy.isSupportedName("a.gif"))
        assertTrue(WallpaperCopy.isSupportedName("a.bmp"))
        assertFalse(WallpaperCopy.isSupportedName("a.webp"))
        assertFalse(WallpaperCopy.isSupportedName("a.heic"))
        assertFalse(WallpaperCopy.isSupportedName("a"))
    }

    /** 副本前缀固定，便于清理历史文件。 */
    @Test
    fun prefixStable() {
        assertEquals("!!shiyu_wallpaper", WallpaperCopy.PREFIX)
    }
}
