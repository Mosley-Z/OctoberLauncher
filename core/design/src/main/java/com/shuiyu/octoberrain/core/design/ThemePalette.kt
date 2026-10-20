package com.shuiyu.octoberrain.core.design

/**
 * 主题配色令牌。
 *
 * 所有颜色均使用 ARGB 整型值，编码方式与 `android.graphics.Color` 一致。
 * 该类型不引用 Android 框架类，可在纯 JVM 单元测试中直接构造与比较。
 *
 * @property paper 纸面底色，用于页面、卡片与弹窗背景。
 * @property ink 主墨色，用于标题、正文与主要图形。
 * @property inkSoft 次级墨色，用于同一行内的数值与次要强调。
 * @property inkFaint 弱化墨色，用于说明文字、未选中项与次级图标。
 * @property inkGhost 最浅墨色，用于分隔线、描边底纹与禁用状态。
 * @property invertPaper 反白块底色，用于按钮岛、选中行与浮层。
 * @property invertInk 反白块上的文字与图形颜色。
 * @property accentLine 强调描边颜色，用于焦点框、进度条与选中标记。
 */
data class ThemePalette(
    val paper: Int = WHITE,
    val ink: Int = BLACK,
    val inkSoft: Int = 0xFF3C3C3C.toInt(),
    val inkFaint: Int = 0xFFA8A8A8.toInt(),
    val inkGhost: Int = 0xFFD8D8D8.toInt(),
    val invertPaper: Int = BLACK,
    val invertInk: Int = WHITE,
    val accentLine: Int = BLACK,
) {
    companion object {
        /** 纯白。 */
        private val WHITE = 0xFFFFFFFF.toInt()

        /** 纯黑。 */
        private val BLACK = 0xFF000000.toInt()

        /** 墨：白底黑线，默认主题。 */
        val INK = ThemePalette()

        /** 纸：米白底暖黑线，长时间阅读更柔和。 */
        val PAPER =
            ThemePalette(
                paper = 0xFFF6F1E7.toInt(),
                ink = 0xFF2B2B2B.toInt(),
                inkSoft = 0xFF4A463E.toInt(),
                inkFaint = 0xFF8C8678.toInt(),
                inkGhost = 0xFFD9D3C6.toInt(),
                invertPaper = 0xFF2B2B2B.toInt(),
                invertInk = 0xFFF6F1E7.toInt(),
                accentLine = 0xFF2B2B2B.toInt(),
            )

        /** 夜：深底白线，适合弱光环境。 */
        val NIGHT =
            ThemePalette(
                paper = 0xFF121212.toInt(),
                ink = 0xFFEDEDED.toInt(),
                inkSoft = 0xFFC4C4C4.toInt(),
                inkFaint = 0xFF7A7A7A.toInt(),
                inkGhost = 0xFF3A3A3A.toInt(),
                invertPaper = 0xFFEDEDED.toInt(),
                invertInk = 0xFF121212.toInt(),
                accentLine = 0xFFEDEDED.toInt(),
            )

        /** 高对比：纯黑白，取消中间灰阶，便于强光下辨识。 */
        val HIGH_CONTRAST =
            ThemePalette(
                paper = WHITE,
                ink = BLACK,
                inkSoft = BLACK,
                inkFaint = 0xFF666666.toInt(),
                inkGhost = 0xFFBBBBBB.toInt(),
                invertPaper = BLACK,
                invertInk = WHITE,
                accentLine = BLACK,
            )

        /** 默认配色，对应“墨”主题。 */
        val DEFAULT: ThemePalette = INK

        /** 内置主题预设列表，顺序即设置页展示顺序。 */
        val PRESETS: List<ThemePreset> =
            listOf(
                ThemePreset(
                    id = "ink",
                    label = "墨",
                    description = "白底黑线，默认主题",
                    palette = INK,
                ),
                ThemePreset(
                    id = "paper",
                    label = "纸",
                    description = "米白底暖黑线，长时间阅读更柔和",
                    palette = PAPER,
                ),
                ThemePreset(
                    id = "night",
                    label = "夜",
                    description = "深色底白色线，适合弱光环境",
                    palette = NIGHT,
                ),
                ThemePreset(
                    id = "highcontrast",
                    label = "高对比",
                    description = "纯黑白，取消中间灰阶",
                    palette = HIGH_CONTRAST,
                ),
            )

        /**
         * 按标识查找内置预设。
         *
         * @param id 主题标识，例如 `ink`、`paper`、`night`、`highcontrast`。
         * @return 命中的预设，标识不存在时返回 null。
         */
        fun preset(id: String): ThemePreset? = PRESETS.firstOrNull { it.id == id }

        /**
         * 按标识取出配色，未知标识回退默认配色。
         *
         * @param id 主题标识。
         * @return 对应的配色令牌，找不到时返回 [DEFAULT]。
         */
        fun forId(id: String): ThemePalette = preset(id)?.palette ?: DEFAULT
    }
}

/**
 * 内置主题预设。
 *
 * @property id 主题标识，写入 `AppearanceConfig.themeId`。
 * @property label 展示名称。
 * @property description 一句话说明，显示在主题选择页。
 * @property palette 该预设对应的配色令牌。
 */
data class ThemePreset(
    val id: String,
    val label: String,
    val description: String,
    val palette: ThemePalette,
)
