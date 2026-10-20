package com.shuiyu.octoberrain.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 壁纸量化档位映射测试。
 *
 * 仅覆盖不依赖 Android 位图的档位语义与空输入路径；
 * 抖动本身由 core:render 的 Ditherer 测试覆盖。
 */
class WallpaperQuantizeTest {
    @Test
    fun `关闭档位返回零`() {
        assertEquals(0, WallpaperQuantize.levelsFor(WallpaperQuantize.OFF))
    }

    @Test
    fun `二级灰档位返回二`() {
        assertEquals(2, WallpaperQuantize.levelsFor(WallpaperQuantize.LEVEL_TWO))
    }

    @Test
    fun `四级灰档位返回四`() {
        assertEquals(4, WallpaperQuantize.levelsFor(WallpaperQuantize.LEVEL_FOUR))
    }

    @Test
    fun `八级灰档位返回八`() {
        assertEquals(8, WallpaperQuantize.levelsFor(WallpaperQuantize.LEVEL_EIGHT))
    }

    @Test
    fun `非法档位按关闭处理`() {
        assertEquals(0, WallpaperQuantize.levelsFor(-1))
        assertEquals(0, WallpaperQuantize.levelsFor(4))
        assertEquals(0, WallpaperQuantize.levelsFor(99))
    }

    @Test
    fun `空位图返回空`() {
        assertNull(WallpaperQuantize.apply(null, WallpaperQuantize.LEVEL_FOUR))
    }
}
