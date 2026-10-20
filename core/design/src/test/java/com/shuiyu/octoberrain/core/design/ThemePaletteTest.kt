package com.shuiyu.octoberrain.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ThemePalette 的配色与预设单元测试。
 *
 * 只验证不依赖 Android 框架的纯逻辑：默认配色、内置预设顺序与标识映射。
 */
class ThemePaletteTest {
    /** 默认配色为白底黑线，且反白块与纸面互为反转。 */
    @Test
    fun `默认配色为白底黑线`() {
        val palette = ThemePalette.DEFAULT
        assertEquals(palette, ThemePalette())
        assertEquals(ThemePalette.INK, palette)
        assertEquals(0xFFFFFFFF.toInt(), palette.paper)
        assertEquals(0xFF000000.toInt(), palette.ink)
        assertEquals(palette.ink, palette.invertPaper)
        assertEquals(palette.paper, palette.invertInk)
    }

    /** 内置预设的标识与展示名称按设置页顺序固定。 */
    @Test
    fun `内置预设顺序与名称固定`() {
        assertEquals(
            listOf("ink", "paper", "night", "highcontrast"),
            ThemePalette.PRESETS.map { it.id },
        )
        assertEquals(
            listOf("墨", "纸", "夜", "高对比"),
            ThemePalette.PRESETS.map { it.label },
        )
        assertTrue(ThemePalette.PRESETS.all { it.description.isNotBlank() })
    }

    /** 预设标识对应的配色与直接引用常量一致。 */
    @Test
    fun `预设标识取出对应配色`() {
        assertEquals(ThemePalette.INK, ThemePalette.preset("ink")?.palette)
        assertEquals(ThemePalette.PAPER, ThemePalette.preset("paper")?.palette)
        assertEquals(ThemePalette.NIGHT, ThemePalette.preset("night")?.palette)
        assertEquals(ThemePalette.HIGH_CONTRAST, ThemePalette.preset("highcontrast")?.palette)

        assertEquals(ThemePalette.PAPER, ThemePalette.forId("paper"))
        assertEquals(ThemePalette.NIGHT, ThemePalette.forId("night"))
        assertEquals(ThemePalette.HIGH_CONTRAST, ThemePalette.forId("highcontrast"))
    }

    /** 未知标识既查不到预设，取配色时回退默认值。 */
    @Test
    fun `未知标识回退默认配色`() {
        assertNull(ThemePalette.preset("unknown"))
        assertEquals(ThemePalette.DEFAULT, ThemePalette.forId("unknown"))
        assertEquals(ThemePalette.DEFAULT, ThemePalette.forId(""))
    }

    /** 高对比预设取消中间灰阶，夜预设保持反白块与主墨色互为正反。 */
    @Test
    fun `高对比与夜预设的配色关系`() {
        val highContrast = ThemePalette.HIGH_CONTRAST
        assertEquals(highContrast.ink, highContrast.inkSoft)
        assertEquals(highContrast.paper, highContrast.invertInk)

        val night = ThemePalette.NIGHT
        assertEquals(night.ink, night.invertPaper)
        assertEquals(night.paper, night.invertInk)
    }
}
