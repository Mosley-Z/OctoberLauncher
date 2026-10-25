package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 其他页方向容量与有效组件标识的单元测试。
 *
 * 方向容量只作用于其他页，0 表示继承原列数与行数；有效组件标识决定桌面
 * 是否占用组件槽位，两处规则都被分页与版面换算共用，因此在纯 JVM 中直接断言。
 */
class DesktopDirectionCapacityTest {
    /** 竖屏容量取自身配置，横屏容量独立保存且互不影响。 */
    @Test
    fun `方向容量分别生效`() {
        val desktop = DesktopConfig(columns = 5, rows = 5, portraitColumns = 3, portraitRows = 4)
        assertEquals(3, desktop.effectivePortraitColumns)
        assertEquals(4, desktop.effectivePortraitRows)
        assertEquals(5, desktop.effectiveLandscapeColumns)
        assertEquals(5, desktop.effectiveLandscapeRows)
    }

    /** 方向容量为 0 时继承原列数与行数，保持旧配置的排版结果。 */
    @Test
    fun `方向容量为零时继承原值`() {
        val desktop = DesktopConfig(columns = 4, rows = 3)
        assertEquals(4, desktop.effectivePortraitColumns)
        assertEquals(3, desktop.effectivePortraitRows)
        assertEquals(4, desktop.effectiveLandscapeColumns)
        assertEquals(3, desktop.effectiveLandscapeRows)
    }

    /** 旧配置缺少方向字段时按 0 解析，不改变既有版面。 */
    @Test
    fun `旧配置缺少方向字段按继承处理`() {
        val decoded =
            AppJson.format.decodeFromString<LauncherConfig>(
                """{"desktop":{"columns":4,"rows":3}}""",
            )
        assertEquals(0, decoded.desktop.portraitColumns)
        assertEquals(0, decoded.desktop.portraitRows)
        assertEquals(0, decoded.desktop.landscapeColumns)
        assertEquals(0, decoded.desktop.landscapeRows)
        assertEquals(4, decoded.desktop.effectivePortraitColumns)
        assertEquals(3, decoded.desktop.effectiveLandscapeRows)
    }

    /** 有效组件标识只看 widgetId，轮换列表仅为旧配置残留。 */
    @Test
    fun `有效组件标识忽略轮换列表`() {
        val rotated = DesktopConfig(widgetId = "clock", widgetCycleIds = listOf("todo_week", "clock"))
        assertEquals("clock", rotated.effectiveWidgetId)
        val single = DesktopConfig(widgetId = "todo_week", widgetCycleIds = emptyList())
        assertEquals("todo_week", single.effectiveWidgetId)
    }

    /** 轮换列表非空但 widgetId 为空时，桌面仍视为不使用组件。 */
    @Test
    fun `有效组件标识不看空白项`() {
        assertEquals("clock", DesktopConfig(widgetId = "clock", widgetCycleIds = listOf("", "  ")).effectiveWidgetId)
        assertEquals("", DesktopConfig(widgetId = "", widgetCycleIds = listOf("usage")).effectiveWidgetId)
    }
}
