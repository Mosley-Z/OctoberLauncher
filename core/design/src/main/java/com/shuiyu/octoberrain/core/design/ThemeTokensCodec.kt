package com.shuiyu.octoberrain.core.design

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.IconShape
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * 主题令牌快照。
 *
 * 保存一组配色令牌以及在同一主题下需要固化的外观字段，序列化后写入
 * `AppearanceConfig.themeTokensJson`。可空字段表示该主题不约束对应外观项，
 * 恢复时沿用当前配置。
 *
 * @property palette 配色令牌。
 * @property textScale 字号缩放，取值 0.85 到 1.6。
 * @property iconShape 图标形状枚举名，对应 [IconShape]。
 * @property iconContrast 图标二值化档位，取值 0 到 3。
 * @property invertIsland 是否反转按钮岛。
 * @property wallpaperDim 壁纸变暗系数，取值 0 到 1。
 * @property twelveHour 是否使用十二小时制。
 * @property showLabels 是否显示应用名称。
 * @property highContrast 是否启用高对比度。
 */
data class ThemeTokens(
    val palette: ThemePalette,
    val textScale: Float? = null,
    val iconShape: String? = null,
    val iconContrast: Int? = null,
    val invertIsland: Boolean? = null,
    val wallpaperDim: Float? = null,
    val twelveHour: Boolean? = null,
    val showLabels: Boolean? = null,
    val highContrast: Boolean? = null,
)

/**
 * 主题令牌编解码。
 *
 * 采用手写 JSON 结构，不依赖序列化代码生成插件，因此 core/design 无需引入
 * kotlinx.serialization 编译器插件。编码结果稳定可读，颜色以 `#AARRGGBB`
 * 字符串形式保存，便于备份文件人工核对。
 *
 * 结构示例：
 * ```json
 * {
 *   "version": 1,
 *   "palette": { "paper": "#FFFFFFFF", "ink": "#FF000000", "...": "..." },
 *   "appearance": { "textScale": 1.0, "iconShape": "ROUNDED" }
 * }
 * ```
 */
object ThemeTokensCodec {
    /** 当前令牌结构版本。 */
    private const val VERSION = 1

    private const val KEY_VERSION = "version"
    private const val KEY_PALETTE = "palette"
    private const val KEY_APPEARANCE = "appearance"

    /**
     * 仅编码配色令牌。
     *
     * 参数 palette 为配色令牌；返回的 JSON 仅包含 palette 段，外观字段留空。
     */
    fun encode(palette: ThemePalette): String = encode(ThemeTokens(palette))

    /**
     * 编码完整主题令牌。
     *
     * 参数 tokens 为主题令牌；返回紧凑 JSON 字符串，可写入 `themeTokensJson`。
     */
    fun encode(tokens: ThemeTokens): String {
        val root =
            buildJsonObject {
                put(KEY_VERSION, VERSION)
                put(KEY_PALETTE, encodePalette(tokens.palette))
                put(
                    KEY_APPEARANCE,
                    buildJsonObject {
                        tokens.textScale?.let { put("textScale", it) }
                        tokens.iconShape?.let { put("iconShape", it) }
                        tokens.iconContrast?.let { put("iconContrast", it) }
                        tokens.invertIsland?.let { put("invertIsland", it) }
                        tokens.wallpaperDim?.let { put("wallpaperDim", it) }
                        tokens.twelveHour?.let { put("twelveHour", it) }
                        tokens.showLabels?.let { put("showLabels", it) }
                        tokens.highContrast?.let { put("highContrast", it) }
                    },
                )
            }
        return root.toString()
    }

    /**
     * 解析主题令牌。
     *
     * 参数 raw 为 JSON 字符串，允许直接是配色对象（无 palette 包裹）以兼容早期
     * 手写的令牌片段。解析失败、根节点不是对象、颜色字段非法时返回 null，由调用方
     * 回退默认预设。
     */
    fun decode(raw: String): ThemeTokens? {
        if (raw.isBlank()) return null
        val root =
            runCatching { AppJson.format.parseToJsonElement(raw) }.getOrNull() as? JsonObject
                ?: return null
        val paletteObject = (root[KEY_PALETTE] as? JsonObject) ?: root
        val fallback = ThemePalette.DEFAULT
        val palette =
            ThemePalette(
                paper = readColor(paletteObject, "paper", fallback.paper) ?: return null,
                ink = readColor(paletteObject, "ink", fallback.ink) ?: return null,
                inkSoft = readColor(paletteObject, "inkSoft", fallback.inkSoft) ?: return null,
                inkFaint = readColor(paletteObject, "inkFaint", fallback.inkFaint) ?: return null,
                inkGhost = readColor(paletteObject, "inkGhost", fallback.inkGhost) ?: return null,
                invertPaper = readColor(paletteObject, "invertPaper", fallback.invertPaper) ?: return null,
                invertInk = readColor(paletteObject, "invertInk", fallback.invertInk) ?: return null,
                accentLine = readColor(paletteObject, "accentLine", fallback.accentLine) ?: return null,
            )
        val appearance = root[KEY_APPEARANCE] as? JsonObject
        return ThemeTokens(
            palette = palette,
            textScale = appearance?.floatOrNull("textScale"),
            iconShape = appearance?.stringOrNull("iconShape"),
            iconContrast = appearance?.intOrNull("iconContrast"),
            invertIsland = appearance?.booleanOrNull("invertIsland"),
            wallpaperDim = appearance?.floatOrNull("wallpaperDim"),
            twelveHour = appearance?.booleanOrNull("twelveHour"),
            showLabels = appearance?.booleanOrNull("showLabels"),
            highContrast = appearance?.booleanOrNull("highContrast"),
        )
    }

    /**
     * 将当前外观配置与配色组合为令牌快照。
     *
     * 参数 appearance 为当前外观配置；参数 palette 为当前生效配色。
     * 返回的快照可用于导出主题或写入 `themeTokensJson`。
     */
    fun capture(
        appearance: AppearanceConfig,
        palette: ThemePalette,
    ): ThemeTokens =
        ThemeTokens(
            palette = palette,
            textScale = appearance.textScale,
            iconShape = appearance.iconShape.name,
            iconContrast = appearance.iconContrast,
            invertIsland = appearance.invertIsland,
            wallpaperDim = appearance.wallpaperDim,
            twelveHour = appearance.twelveHour,
            showLabels = appearance.showLabels,
            highContrast = appearance.highContrast,
        )

    /**
     * 把令牌快照合并回外观配置。
     *
     * 参数 tokens 为已解析的令牌；参数 appearance 为当前外观配置。
     * 快照中为 null 的字段沿用当前配置，数值字段按各自合法区间收敛；
     * 返回合并后的新配置，所有字段均为非空值。
     */
    fun merge(
        tokens: ThemeTokens,
        appearance: AppearanceConfig,
    ): AppearanceConfig {
        val shape =
            tokens.iconShape
                ?.let { name -> runCatching { IconShape.valueOf(name) }.getOrNull() }
                ?: appearance.iconShape
        return appearance.copy(
            textScale = (tokens.textScale ?: appearance.textScale).coerceIn(0.85f, 1.6f),
            iconShape = shape,
            iconContrast = (tokens.iconContrast ?: appearance.iconContrast).coerceIn(0, 3),
            invertIsland = tokens.invertIsland ?: appearance.invertIsland,
            wallpaperDim = (tokens.wallpaperDim ?: appearance.wallpaperDim).coerceIn(0f, 1f),
            twelveHour = tokens.twelveHour ?: appearance.twelveHour,
            showLabels = tokens.showLabels ?: appearance.showLabels,
            highContrast = tokens.highContrast ?: appearance.highContrast,
        )
    }

    /** 编码配色令牌对象。 */
    private fun encodePalette(palette: ThemePalette): JsonObject =
        buildJsonObject {
            put("paper", encodeColor(palette.paper))
            put("ink", encodeColor(palette.ink))
            put("inkSoft", encodeColor(palette.inkSoft))
            put("inkFaint", encodeColor(palette.inkFaint))
            put("inkGhost", encodeColor(palette.inkGhost))
            put("invertPaper", encodeColor(palette.invertPaper))
            put("invertInk", encodeColor(palette.invertInk))
            put("accentLine", encodeColor(palette.accentLine))
        }

    /** 将 ARGB 整型编码为 `#AARRGGBB` 十六进制文本。 */
    private fun encodeColor(value: Int): String = String.format(Locale.US, "#%08X", value)

    /**
     * 读取颜色字段。
     *
     * 参数 object 为配色对象；key 为字段名；default 为字段缺失时的取值。
     * 字段存在但不是合法十六进制颜色时返回 null，用于整体判定解析失败。
     */
    private fun readColor(
        objectValue: JsonObject,
        key: String,
        default: Int,
    ): Int? {
        val element = objectValue[key] ?: return default
        val primitive = element as? JsonPrimitive ?: return null
        return parseColor(primitive.content) ?: return null
    }

    /** 解析 `#AARRGGBB` 或 `#RRGGBB` 文本，非法输入返回 null。 */
    private fun parseColor(raw: String): Int? {
        val text = raw.trim().removePrefix("#")
        if (text.length != 6 && text.length != 8) return null
        val value = text.toLongOrNull(16) ?: return null
        return if (text.length == 6) (0xFF000000L or value).toInt() else value.toInt()
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.floatOrNull(key: String): Float? = (this[key] as? JsonPrimitive)?.content?.toFloatOrNull()

    private fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonObject.booleanOrNull(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
}
