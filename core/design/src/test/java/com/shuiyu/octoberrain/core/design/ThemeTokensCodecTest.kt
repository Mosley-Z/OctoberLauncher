package com.shuiyu.octoberrain.core.design

import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.IconShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题令牌编解码单元测试。
 *
 * 编解码与合并逻辑为纯函数，不依赖 Android 框架；覆盖编码格式、解码容错、
 * 快照捕获与合并时的区间收敛。
 */
class ThemeTokensCodecTest {
    /** 仅编码配色时输出包含版本号与调色板段，解码不携带外观字段。 */
    @Test
    fun `仅编码配色可无损还原调色板`() {
        val encoded = ThemeTokensCodec.encode(ThemePalette.NIGHT)
        assertTrue(encoded.contains("\"version\":1"))
        assertTrue(encoded.contains("\"paper\":\"#FF121212\""))

        val tokens = ThemeTokensCodec.decode(encoded)
        assertNotNull(tokens)
        assertEquals(ThemePalette.NIGHT, tokens?.palette)
        assertNull(tokens?.textScale)
        assertNull(tokens?.iconShape)
        assertNull(tokens?.iconContrast)
        assertNull(tokens?.invertIsland)
        assertNull(tokens?.wallpaperDim)
        assertNull(tokens?.twelveHour)
        assertNull(tokens?.showLabels)
        assertNull(tokens?.highContrast)
    }

    /** 完整令牌编码再解码后各字段保持一致。 */
    @Test
    fun `完整令牌编码解码后字段一致`() {
        val original =
            ThemeTokens(
                palette = ThemePalette.PAPER,
                textScale = 1.25f,
                iconShape = "PILL",
                iconContrast = 2,
                invertIsland = true,
                wallpaperDim = 0.35f,
                twelveHour = true,
                showLabels = false,
                highContrast = false,
            )
        val decoded = ThemeTokensCodec.decode(ThemeTokensCodec.encode(original))
        assertNotNull(decoded)
        assertEquals(ThemePalette.PAPER, decoded?.palette)
        assertEquals(1.25f, decoded?.textScale ?: 0f, 0.0001f)
        assertEquals("PILL", decoded?.iconShape)
        assertEquals(2, decoded?.iconContrast)
        assertEquals(true, decoded?.invertIsland)
        assertEquals(0.35f, decoded?.wallpaperDim ?: 0f, 0.0001f)
        assertEquals(true, decoded?.twelveHour)
        assertEquals(false, decoded?.showLabels)
        assertEquals(false, decoded?.highContrast)
    }

    /** 非法输入一律返回 null，由调用方回退默认预设。 */
    @Test
    fun `非法输入返回空`() {
        assertNull(ThemeTokensCodec.decode(""))
        assertNull(ThemeTokensCodec.decode("   "))
        assertNull(ThemeTokensCodec.decode("不是 JSON"))
        assertNull(ThemeTokensCodec.decode("[1,2,3]"))
        // 颜色字段不是合法十六进制
        assertNull(ThemeTokensCodec.decode("{\"palette\":{\"paper\":\"#GGGGGG\"}}"))
        assertNull(ThemeTokensCodec.decode("{\"palette\":{\"paper\":\"#FFF\"}}"))
        assertNull(ThemeTokensCodec.decode("{\"palette\":{\"paper\":123}}"))
    }

    /** 缺失颜色字段用默认配色补齐，缺少 palette 包裹时按根对象读取。 */
    @Test
    fun `缺失字段用默认值补齐`() {
        val tokens = ThemeTokensCodec.decode("{}")
        assertNotNull(tokens)
        assertEquals(ThemePalette.DEFAULT, tokens?.palette)

        val bare =
            ThemeTokensCodec.decode(
                "{\"paper\":\"#FFF6F1E7\",\"ink\":\"#FF2B2B2B\",\"inkSoft\":\"#FF4A463E\"," +
                    "\"inkFaint\":\"#FF8C8678\",\"inkGhost\":\"#FFD9D3C6\",\"invertPaper\":\"#FF2B2B2B\"," +
                    "\"invertInk\":\"#FFF6F1E7\",\"accentLine\":\"#FF2B2B2B\"}",
            )
        assertNotNull(bare)
        assertEquals(ThemePalette.PAPER, bare?.palette)
    }

    /** 六位颜色按不透明处理，八位颜色保留透明度。 */
    @Test
    fun `颜色文本兼容六位与八位`() {
        val opaque = ThemeTokensCodec.decode("{\"palette\":{\"paper\":\"#F6F1E7\"}}")
        assertEquals(0xFFF6F1E7.toInt(), opaque?.palette?.paper)

        val translucent = ThemeTokensCodec.decode("{\"palette\":{\"paper\":\"#80FFFFFF\"}}")
        assertEquals(0x80FFFFFF.toInt(), translucent?.palette?.paper)
    }

    /** 快照捕获记录当前外观配置中的受约束字段。 */
    @Test
    fun `捕获外观配置为快照`() {
        val appearance =
            AppearanceConfig(
                textScale = 1.3f,
                iconShape = IconShape.PILL,
                iconContrast = 2,
                invertIsland = true,
                wallpaperDim = 0.4f,
                twelveHour = true,
                showLabels = false,
                highContrast = false,
            )
        val tokens = ThemeTokensCodec.capture(appearance, ThemePalette.NIGHT)
        assertEquals(ThemePalette.NIGHT, tokens.palette)
        assertEquals(1.3f, tokens.textScale ?: 0f, 0.0001f)
        assertEquals("PILL", tokens.iconShape)
        assertEquals(2, tokens.iconContrast)
        assertEquals(true, tokens.invertIsland)
        assertEquals(0.4f, tokens.wallpaperDim ?: 0f, 0.0001f)
        assertEquals(true, tokens.twelveHour)
        assertEquals(false, tokens.showLabels)
        assertEquals(false, tokens.highContrast)
    }

    /** 空字段沿用当前配置，已填充字段覆盖当前配置。 */
    @Test
    fun `合并时空字段沿用当前配置`() {
        val appearance = AppearanceConfig()
        val untouched = ThemeTokensCodec.merge(ThemeTokens(palette = ThemePalette.NIGHT), appearance)
        assertEquals(appearance, untouched)

        val overridden =
            ThemeTokensCodec.merge(
                ThemeTokens(
                    palette = ThemePalette.NIGHT,
                    textScale = 1.4f,
                    iconShape = "SQUARE",
                    iconContrast = 3,
                    invertIsland = true,
                    twelveHour = true,
                    showLabels = false,
                    highContrast = false,
                ),
                appearance,
            )
        assertEquals(1.4f, overridden.textScale, 0.0001f)
        assertEquals(IconShape.SQUARE, overridden.iconShape)
        assertEquals(3, overridden.iconContrast)
        assertEquals(true, overridden.invertIsland)
        assertEquals(true, overridden.twelveHour)
        assertEquals(false, overridden.showLabels)
        assertEquals(false, overridden.highContrast)
    }

    /** 数值超出合法区间时收敛到边界，未知图标形状名回退当前配置。 */
    @Test
    fun `合并时收敛数值并忽略非法枚举名`() {
        val appearance = AppearanceConfig(iconShape = IconShape.ROUNDED)
        val merged =
            ThemeTokensCodec.merge(
                ThemeTokens(
                    palette = ThemePalette.DEFAULT,
                    textScale = 9f,
                    iconShape = "TRIANGLE",
                    iconContrast = -2,
                    wallpaperDim = 2f,
                ),
                appearance,
            )
        assertEquals(1.6f, merged.textScale, 0.0001f)
        assertEquals(IconShape.ROUNDED, merged.iconShape)
        assertEquals(0, merged.iconContrast)
        assertEquals(1f, merged.wallpaperDim, 0.0001f)

        val low =
            ThemeTokensCodec.merge(
                ThemeTokens(palette = ThemePalette.DEFAULT, textScale = 0.1f),
                appearance,
            )
        assertEquals(0.85f, low.textScale, 0.0001f)
    }

    /** 捕获后合并回去，区间内的外观配置保持不变。 */
    @Test
    fun `捕获与合并互为逆操作`() {
        val appearance =
            AppearanceConfig(
                textScale = 1.2f,
                iconShape = IconShape.SQUARE,
                iconContrast = 1,
                invertIsland = true,
                wallpaperDim = 0.5f,
                twelveHour = false,
                showLabels = true,
                highContrast = true,
            )
        val restored =
            ThemeTokensCodec.merge(
                ThemeTokensCodec.capture(appearance, ThemePalette.PAPER),
                appearance,
            )
        assertEquals(appearance, restored)
    }
}
