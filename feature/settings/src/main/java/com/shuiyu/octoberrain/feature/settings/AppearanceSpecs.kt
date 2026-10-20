package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.IconShape
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_APPEARANCE_DISPLAY
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_APPEARANCE_FONTS
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_APPEARANCE_IMAGE
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_APPEARANCE_TEXT
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_APPEARANCE_THEME
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_APPEARANCE_WALLPAPER

/**
 * 外观与主题页的设置项声明。
 *
 * 分组与条目顺序与界面显示顺序一致，页面文件只提供动作与自定义行。
 */
internal object AppearanceSpecs {
    /** 字号缩放档位。 */
    private val TEXT_SCALES: List<Float> =
        listOf(0.85f, 0.90f, 0.95f, 1.00f, 1.05f, 1.10f, 1.15f, 1.20f, 1.25f, 1.30f)

    /** 壁纸变暗档位，单位百分比。 */
    private val DIM_STEPS: List<Float> = (0..100 step 10).map { it.toFloat() }

    /** 状态栏高度档位，0 表示按设备自动计算。 */
    private val STATUS_BAR_VALUES: List<Int> = listOf(0, 28, 32, 36, 40)

    /** 状态栏高度选项文本。 */
    private val STATUS_BAR_OPTIONS: List<String> =
        STATUS_BAR_VALUES.map { if (it == 0) "自动" else "$it dp" }

    /** 图标二值化档位选项文本，下标即档位值。 */
    private val ICON_CONTRAST_OPTIONS: List<String> = listOf("关闭", "轻", "中", "强")

    /** 壁纸量化档位选项文本，下标即配置中的量化档位。 */
    private val QUANTIZE_OPTIONS: List<String> = listOf("关闭", "二级灰", "四级灰", "八级灰")

    val groups: List<SettingsGroupSpec> =
        listOf(
            SettingsGroupSpec(
                id = GROUP_APPEARANCE_WALLPAPER,
                title = "壁纸",
                subtitle = "桌面与锁屏壁纸分别设置，文件保存在应用私有目录，更换后立即重绘",
            ),
            SettingsGroupSpec(
                id = GROUP_APPEARANCE_THEME,
                title = "主题与图标",
                subtitle = "主题决定纸面配色、线条灰度与图标映射",
            ),
            SettingsGroupSpec(
                id = GROUP_APPEARANCE_FONTS,
                title = "字体",
                subtitle = "字体文件导入后保存在应用私有目录",
            ),
            SettingsGroupSpec(
                id = GROUP_APPEARANCE_TEXT,
                title = "文字与密度",
                subtitle = "字号缩放会整体重建界面",
            ),
            SettingsGroupSpec(
                id = GROUP_APPEARANCE_IMAGE,
                title = "图片与缓存",
                subtitle = "图片目录用于美化模式选图，缓存占用随缩略图质量变化",
                notes =
                    listOf(
                        "清理缓存只删除本应用生成的缩略图，不影响原始图片；" +
                            "清理后再次浏览会按当前质量档重新生成。",
                    ),
            ),
            SettingsGroupSpec(
                id = GROUP_APPEARANCE_DISPLAY,
                title = "显示",
                subtitle = "对比度、时间制式与状态栏",
                notes =
                    listOf(
                        "字号与状态栏调整后界面会立即重建，滚动位置回到顶部。" +
                            "壁纸与图标包只影响桌面与预览，不改变设置页样式。",
                        "壁纸、字体与主题文件通过系统文件选择器读取；系统选择器不可用时回退到内置文件扫描，" +
                            "此时需先在权限与授权页面授予存储访问权限。",
                    ),
            ),
        )

    val specs: List<SettingSpec> =
        listOf(
            SettingSpec(
                id = "appearance.wallpaper.pick",
                group = GROUP_APPEARANCE_WALLPAPER,
                title = "选择壁纸",
                hint = "系统选择器优先，缺少文档界面时从 Pictures、Download 等目录选取",
                control =
                    SettingControl.Action(value = { config ->
                        val path = config.appearance.wallpaperPath
                        if (path.isNullOrBlank()) {
                            "纯白"
                        } else {
                            runCatching { java.io.File(path).name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "已设置"
                        }
                    }),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.wallpaper.overlay",
                group = GROUP_APPEARANCE_WALLPAPER,
                title = "白色压层",
                hint = "在壁纸上叠加白色半透明层，提升图标与文字对比度",
                control =
                    toggleAppearance(
                        read = { it.wallpaperWhiteOverlay },
                        write = { appearance, value -> appearance.copy(wallpaperWhiteOverlay = value) },
                    ),
                effect = SettingEffect.APPEARANCE,
            ),
            SettingSpec(
                id = "appearance.wallpaper.clear",
                group = GROUP_APPEARANCE_WALLPAPER,
                title = "清除壁纸",
                control =
                    SettingControl.Action(value = { config ->
                        if (config.appearance.wallpaperPath.isNullOrBlank()) "无" else "当前已设置"
                    }),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.wallpaper.dim",
                group = GROUP_APPEARANCE_WALLPAPER,
                title = "壁纸变暗",
                hint = "在壁纸上叠加黑色遮罩，0 为原始亮度，100 为全黑",
                control =
                    sliderAppearance(
                        values = DIM_STEPS,
                        read = { it.wallpaperDim * 100f },
                        write = { appearance, value ->
                            appearance.copy(wallpaperDim = (value / 100f).coerceIn(0f, 1f))
                        },
                        format = { "${it.toInt()}%" },
                    ),
                effect = SettingEffect.APPEARANCE,
            ),
            SettingSpec(
                id = "appearance.wallpaper.quantize",
                group = GROUP_APPEARANCE_WALLPAPER,
                title = "桌面壁纸量化",
                hint = "按档位压成灰阶并保留抖动纹理，关闭时使用原始壁纸",
                control =
                    choiceAppearance(
                        options = QUANTIZE_OPTIONS,
                        read = { it.wallpaperQuantize.coerceIn(0, 3) },
                        write = { appearance, index -> appearance.copy(wallpaperQuantize = index.coerceIn(0, 3)) },
                    ),
                effect = SettingEffect.APPEARANCE,
            ),
            SettingSpec(
                id = "appearance.lockWallpaper.pick",
                group = GROUP_APPEARANCE_WALLPAPER,
                title = "选择锁屏壁纸",
                hint = "锁屏密码界面背景，选择后在后台裁剪并量化",
                control =
                    SettingControl.Action(value = { config ->
                        val path = config.appearance.lockWallpaperPath
                        if (path.isBlank()) {
                            "纸色"
                        } else {
                            runCatching { java.io.File(path).name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "已设置"
                        }
                    }),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.lockWallpaper.clear",
                group = GROUP_APPEARANCE_WALLPAPER,
                title = "清除锁屏壁纸",
                control =
                    SettingControl.Action(value = { config ->
                        if (config.appearance.lockWallpaperPath.isBlank()) "无" else "当前已设置"
                    }),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.lockWallpaper.quantize",
                group = GROUP_APPEARANCE_WALLPAPER,
                title = "锁屏壁纸量化",
                hint = "锁屏壁纸的灰阶档位，默认四级灰以突出键盘与提示文字",
                control =
                    choiceAppearance(
                        options = QUANTIZE_OPTIONS,
                        read = { it.lockWallpaperQuantize.coerceIn(0, 3) },
                        write = { appearance, index -> appearance.copy(lockWallpaperQuantize = index.coerceIn(0, 3)) },
                    ),
                effect = SettingEffect.APPEARANCE,
            ),
            SettingSpec(
                id = "appearance.theme.open",
                group = GROUP_APPEARANCE_THEME,
                title = "主题模板",
                hint = "内置墨、纸、夜与高对比四套令牌，可追加主题文件",
                control = SettingControl.Action(value = { SettingsUi.themeLabel(it.appearance.themeId) }),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.iconPack.open",
                group = GROUP_APPEARANCE_THEME,
                title = "图标包",
                hint = "内置线稿图标，可追加主题包中的图标映射",
                control = SettingControl.Action(value = { SettingsUi.iconPackLabel(it.appearance.iconPackId) }),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.iconShape",
                group = GROUP_APPEARANCE_THEME,
                title = "图标形状",
                hint = "只影响桌面图标外框，不改变图标内容",
                control =
                    choiceAppearance(
                        options = listOf("胶囊", "圆角", "方形"),
                        read = { IconShape.entries.indexOf(it.iconShape).coerceAtLeast(0) },
                        write = { appearance, index ->
                            appearance.copy(
                                iconShape = IconShape.entries.getOrElse(index) { IconShape.ROUNDED },
                            )
                        },
                    ),
                effect = SettingEffect.APPEARANCE,
            ),
            SettingSpec(
                id = "appearance.iconContrast",
                group = GROUP_APPEARANCE_THEME,
                title = "图标二值化",
                hint = "按档位把图标压成纯黑白，档位越高细节越少",
                control =
                    choiceAppearance(
                        options = ICON_CONTRAST_OPTIONS,
                        read = { it.iconContrast.coerceIn(0, 3) },
                        write = { appearance, index -> appearance.copy(iconContrast = index.coerceIn(0, 3)) },
                    ),
                effect = SettingEffect.APPEARANCE,
            ),
            SettingSpec(
                id = "appearance.font.label",
                group = GROUP_APPEARANCE_FONTS,
                title = "应用名称字体",
                hint = "影响桌面图标下方的应用名称",
                control = SettingControl.Action(value = ::labelFontLabel),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.font.clock",
                group = GROUP_APPEARANCE_FONTS,
                title = "钟面字体",
                hint = "影响大时钟组件与锁屏时间",
                control = SettingControl.Action(value = ::clockFontLabel),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.text.scale",
                group = GROUP_APPEARANCE_TEXT,
                title = "字号缩放",
                hint = "0.85 至 1.30 倍，所有页面文字同步缩放",
                control =
                    sliderAppearance(
                        values = TEXT_SCALES,
                        read = { it.textScale },
                        write = { appearance, value -> appearance.copy(textScale = value) },
                        format = { SettingsUi.percent(it) },
                    ),
                effect = SettingEffect.APPEARANCE_REBUILD,
            ),
            SettingSpec(
                id = "appearance.text.reset",
                group = GROUP_APPEARANCE_TEXT,
                title = "恢复默认字号",
                control = SettingControl.Button(),
                effect = SettingEffect.APPEARANCE_REBUILD,
                write = { config -> config.copy(appearance = config.appearance.copy(textScale = 1f)) },
            ),
            SettingSpec(
                id = "appearance.labels",
                group = GROUP_APPEARANCE_TEXT,
                title = "显示应用名称",
                hint = "关闭后桌面只显示图标，适合小尺寸屏幕",
                control =
                    toggleAppearance(
                        read = { it.showLabels },
                        write = { appearance, value -> appearance.copy(showLabels = value) },
                    ),
                effect = SettingEffect.APPEARANCE,
            ),
            SettingSpec(
                id = "appearance.highContrast",
                group = GROUP_APPEARANCE_DISPLAY,
                title = "高对比度",
                hint = "强化黑白反差，降低灰度层级",
                control =
                    toggleAppearance(
                        read = { it.highContrast },
                        write = { appearance, value -> appearance.copy(highContrast = value) },
                    ),
                effect = SettingEffect.APPEARANCE,
            ),
            SettingSpec(
                id = "appearance.twelveHour",
                group = GROUP_APPEARANCE_DISPLAY,
                title = "十二小时制",
                hint = "关闭后使用二十四小时制",
                control =
                    toggleAppearance(
                        read = { it.twelveHour },
                        write = { appearance, value -> appearance.copy(twelveHour = value) },
                    ),
                effect = SettingEffect.APPEARANCE,
            ),
            SettingSpec(
                id = "appearance.invertIsland",
                group = GROUP_APPEARANCE_DISPLAY,
                title = "反转按钮岛",
                hint = "把按钮岛改为黑底白字，适合高亮度环境",
                control =
                    toggleAppearance(
                        read = { it.invertIsland },
                        write = { appearance, value -> appearance.copy(invertIsland = value) },
                    ),
                effect = SettingEffect.APPEARANCE,
            ),
            SettingSpec(
                id = "appearance.image.directory",
                group = GROUP_APPEARANCE_IMAGE,
                title = "图片目录",
                hint = "美化模式从该目录读取图片，可随时更改",
                control =
                    SettingControl.Info(
                        read = { "Pictures" },
                        readWithContext = { context, _ -> ImageDirectoryStore.load(context).absolutePath },
                    ),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.image.directory.pick",
                group = GROUP_APPEARANCE_IMAGE,
                title = "更改图片目录",
                hint = "系统目录选择器优先，缺少文档界面时手工输入绝对路径",
                control =
                    SettingControl.Action(
                        value = { "更改" },
                        valueWithContext = { _, _ -> "更改" },
                    ),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.thumbnail.quality",
                group = GROUP_APPEARANCE_IMAGE,
                title = "缩略图质量",
                hint = "档位越高越清晰，占用的内存与磁盘也越多",
                control =
                    choiceAppearance(
                        options = listOf("省内存", "平衡", "清晰"),
                        read = { appearance -> appearance.effectiveThumbnailQuality },
                        write = { appearance, index -> appearance.copy(thumbnailQuality = index) },
                    ),
                afterWrite = { config ->
                    ThumbCacheRegistry.configure(
                        config.appearance.effectiveThumbnailQuality,
                        config.appearance.thumbnailDither,
                    )
                },
            ),
            SettingSpec(
                id = "appearance.thumbnail.dither",
                group = GROUP_APPEARANCE_IMAGE,
                title = "缩略图抖动",
                hint = "开启后缩略图量化为四档灰，关闭保留原始灰阶",
                control =
                    toggleAppearance(
                        read = { it.thumbnailDither },
                        write = { appearance, value -> appearance.copy(thumbnailDither = value) },
                    ),
                afterWrite = { config ->
                    ThumbCacheRegistry.configure(
                        config.appearance.effectiveThumbnailQuality,
                        config.appearance.thumbnailDither,
                    )
                },
            ),
            SettingSpec(
                id = "appearance.image.cache.size",
                group = GROUP_APPEARANCE_IMAGE,
                title = "缓存占用",
                hint = "缩略图缓存位于应用私有缓存目录",
                control =
                    SettingControl.Info(
                        read = { "—" },
                        readWithContext = { context, _ ->
                            formatCacheBytes(ThumbCacheRegistry.diskBytes(context))
                        },
                    ),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.image.cache.clear",
                group = GROUP_APPEARANCE_IMAGE,
                title = "清理缩略图缓存",
                hint = "删除全部缩略图，下次浏览时重新生成",
                control =
                    SettingControl.Action(
                        value = { "清理" },
                        valueWithContext = { _, _ -> "清理" },
                    ),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "appearance.statusBar",
                group = GROUP_APPEARANCE_DISPLAY,
                title = "状态栏高度",
                hint = "自动档位按设备状态栏实际高度计算",
                control =
                    choiceAppearance(
                        options = STATUS_BAR_OPTIONS,
                        read = { appearance ->
                            STATUS_BAR_VALUES.indexOf(appearance.statusBarHeightDp).coerceAtLeast(0)
                        },
                        write = { appearance, index ->
                            appearance.copy(statusBarHeightDp = STATUS_BAR_VALUES[index])
                        },
                    ),
                effect = SettingEffect.APPEARANCE,
            ),
        )
}

/**
 * 把字节数格式化为缓存占用摘要。
 *
 * 参数 bytes 为字节数，负数按 0 处理；小于 1 MB 时以 KB 显示并保留一位小数，
 * 其余以 MB 显示并保留一位小数。
 */
internal fun formatCacheBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return if (safe < 1024L * 1024L) {
        String.format(java.util.Locale.US, "%.1f KB", safe / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.1f MB", safe / (1024.0 * 1024.0))
    }
}
