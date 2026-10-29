package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 壁纸目标与结果类型测试。
 *
 * 覆盖目标顺序、文案与结果归类，避免界面与通道对枚举顺序的假设不一致。
 */
class WallpaperScopeTest {
    /** 菜单顺序为先休眠、再关机、最后同时设置。 */
    @Test
    fun scopeOrder() {
        assertEquals(
            listOf(WallpaperScope.STANDBY, WallpaperScope.SHUTDOWN, WallpaperScope.BOTH),
            WallpaperScope.entries.toList(),
        )
        assertEquals(listOf("休眠壁纸", "关机壁纸", "同时设置"), WallpaperScope.entries.map { it.label })
    }

    /** 单目标与同时设置的目标列表顺序固定。 */
    @Test
    fun scopeTargets() {
        assertEquals(listOf(WallpaperTarget.STANDBY), WallpaperScope.STANDBY.targets)
        assertEquals(listOf(WallpaperTarget.SHUTDOWN), WallpaperScope.SHUTDOWN.targets)
        assertEquals(listOf(WallpaperTarget.STANDBY, WallpaperTarget.SHUTDOWN), WallpaperScope.BOTH.targets)
        assertEquals("休眠壁纸", WallpaperTarget.STANDBY.label)
        assertEquals("关机壁纸", WallpaperTarget.SHUTDOWN.label)
    }

    /** 逐目标结果保留目标与成功标记。 */
    @Test
    fun outcomesKeepPerTarget() {
        val outcome = WallpaperOutcome(WallpaperTarget.SHUTDOWN, false, "超时")
        assertFalse(outcome.success)
        assertEquals(WallpaperTarget.SHUTDOWN, outcome.target)
        val done = WallpaperResult.AllDone(listOf(WallpaperOutcome(WallpaperTarget.STANDBY, true, "完成")))
        assertTrue(done is WallpaperResult.AllDone)
        assertTrue(WallpaperResult.Unavailable("不支持") is WallpaperResult.Unavailable)
    }
}
