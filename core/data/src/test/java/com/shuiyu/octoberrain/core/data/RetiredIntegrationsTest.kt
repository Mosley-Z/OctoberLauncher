package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.WidgetSwitchGesture
import org.junit.Assert.assertEquals
import org.junit.Test

class RetiredIntegrationsTest {
    @Test
    fun `retirement removes only integrated targets and preserves installed app`() {
        val installed = "app:org.koreader.launcher"
        val retired = "special:koreader"
        val input =
            LauncherConfig(
                desktop =
                    DesktopConfig(
                        order = listOf(retired, installed),
                        hiddenSpecials = setOf(retired, "special:settings"),
                        favoriteEntryKeys = listOf(retired, installed),
                        widgetId = "koreader_month",
                        widgetCycleIds = listOf("koreader_month", "clock"),
                        widgetSwitchGesture = WidgetSwitchGesture.RIGHT,
                    ),
                appearance =
                    AppearanceConfig(
                        iconOverrides = mapOf(retired to "old", installed to "custom"),
                        transparentIconTargets = setOf(retired, installed),
                        wallpaperLandscapeRotation = 90,
                    ),
            )
        val actual = RetiredIntegrations.sanitize(input)
        assertEquals(listOf(installed), actual.desktop.order)
        assertEquals(listOf(installed), actual.desktop.favoriteEntryKeys)
        assertEquals(setOf("special:settings"), actual.desktop.hiddenSpecials)
        assertEquals("clock", actual.desktop.widgetId)
        assertEquals(emptyList<String>(), actual.desktop.widgetCycleIds)
        assertEquals(WidgetSwitchGesture.OFF, actual.desktop.widgetSwitchGesture)
        assertEquals(mapOf(installed to "custom"), actual.appearance.iconOverrides)
        assertEquals(setOf(installed), actual.appearance.transparentIconTargets)
        assertEquals(90, actual.appearance.wallpaperLandscapeRotation)
        assertEquals(actual, RetiredIntegrations.sanitize(actual))
    }

    /** 旧配置的当前组件为已清退的使用统计时改为关闭，其他有效组件保持。 */
    @Test
    fun `usage widget retires to closed and other widget stays`() {
        val usage = DesktopConfig(widgetId = "usage", widgetCycleIds = listOf("usage", "clock"))
        val closed = RetiredIntegrations.sanitize(LauncherConfig(desktop = usage)).desktop
        assertEquals("", closed.widgetId)
        assertEquals(emptyList<String>(), closed.widgetCycleIds)
        assertEquals(WidgetSwitchGesture.OFF, closed.widgetSwitchGesture)

        val clock = DesktopConfig(widgetId = "clock", widgetCycleIds = listOf("clock", "todo_week"))
        val kept = RetiredIntegrations.sanitize(LauncherConfig(desktop = clock)).desktop
        assertEquals("clock", kept.widgetId)
        assertEquals(emptyList<String>(), kept.widgetCycleIds)
    }
}
