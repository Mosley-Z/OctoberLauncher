package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.Serializable

/**
 * 默认不在桌面显示的系统应用。
 *
 * 包含 S4 固件的原生文件管理器与图墨桌面；文件管理由墨盘承担，
 * 用户可在设置的「隐藏应用」中恢复显示。
 */
val DEFAULT_HIDDEN_PACKAGES: Set<String> =
    setOf(
        "com.wetao.storage",
        "com.astraabove.tomo",
    )

/**
 * 默认隐藏的内置入口。
 *
 * 微信读书、文件与书架入口不再显示在桌面：书架由负一屏承担，
 * 文件管理交由墨盘，微信读书阅读应用本身保留在应用列表中。
 */
val DEFAULT_HIDDEN_SPECIALS: Set<String> =
    setOf(
        "special:weread",
        "special:files",
        "special:bookshelf",
        "special:koreader",
    )

/**
 * 桌面形态。
 *
 * GRID 为宫格，LIST 为列表，对应上游的宫格与列表两种桌面。
 */
@Serializable
enum class DesktopKind {
    GRID,
    LIST,
}

/**
 * 宫格横向排布方式。
 *
 * LEFT 贴左、CENTER 居中、RIGHT 贴右；仅在列数由配置固定、
 * 单元总宽小于可用宽度时产生可见差异。
 */
@Serializable
enum class GridAlign {
    LEFT,
    CENTER,
    RIGHT,
}

/**
 * 图标框外形。
 *
 * PILL 为全圆角药丸，ROUNDED 为令牌圆角，SQUARE 为直角。
 */
@Serializable
enum class IconShape {
    PILL,
    ROUNDED,
    SQUARE,
}

/**
 * 负一屏内容来源。
 *
 * WEREAD_SHELF 为微信读书书架，LOCAL_BOOKSHELF 为本地书架扫描结果。
 */
@Serializable
enum class NegativePageKind {
    WEREAD_SHELF,
    LOCAL_BOOKSHELF,
}

/**
 * 负一屏默认模块，顺序即首次安装时的上下排列顺序。
 *
 * 依次为书架、最近图片、墨单与阅读资产；后两项沿用微读墨记墨台的模块标识
 * bill 与 rank，便于负一屏与墨台共用同一份渲染与外观配置。阅读指纹 fp 默认关闭，
 * 由用户在负一屏设置页开启后追加，取值与原版 DEFAULT_ON_MASK 一致。
 * 模块实现按标识注册，未注册的标识在负一屏渲染时跳过。
 */
val DEFAULT_NEGATIVE_MODULES: List<String> =
    listOf("bookshelf", "negative_files", "bill", "rank")

/**
 * 第八版负一屏默认模块，仅用于 v7 到 v8 的迁移。
 *
 * 保留旧标识是为了让升级链路各段语义独立：v7 到 v8 仍按旧的五项展开，
 * v8 到 v9 再统一改写为微读墨记的模块标识。
 */
val V8_DEFAULT_NEGATIVE_MODULES: List<String> =
    listOf("bookshelf", "negative_files", "weread_bill", "weread_rank", "weread_fingerprint")

/**
 * 旧负一屏模块标识到微读墨记墨台标识的映射。
 *
 * weread_assets 为旧版分项时长卡片，墨台没有同义分区，合并进阅读资产；
 * weread_bill、weread_rank、weread_fingerprint 分别对应墨单、阅读资产与阅读指纹。
 */
val NEGATIVE_MODULE_ID_ALIASES: Map<String, String> =
    mapOf(
        "weread_bill" to "bill",
        "weread_rank" to "rank",
        "weread_fingerprint" to "fp",
        "weread_assets" to "rank",
    )

/**
 * 负一屏配置。
 *
 * 参数 enabled 为是否启用负一屏；kind 为内容来源；
 * limit 为单屏展示条目上限；showProgress 表示是否绘制进度条；
 * showCover 表示是否绘制封面缩略图；shelfRows 为书架分区的展示行数；
 * shelfSource 为书架分区展示的书源，取值 weread 或 local。
 */
@Serializable
data class NegativePageConfig(
    val enabled: Boolean = true,
    val kind: NegativePageKind = NegativePageKind.WEREAD_SHELF,
    val limit: Int = 9,
    val showProgress: Boolean = true,
    val showCover: Boolean = true,
    /**
     * 启用的负一屏模块 id，按列表顺序上下排列。
     *
     * 默认依次为上架书架、最近图片、墨单、排行与指纹五张卡片；
     * 新增模块注册后在此追加即可显示。
     */
    val modules: List<String> = DEFAULT_NEGATIVE_MODULES,
    /** 墨台设置的完整顺序，保留关闭模块的位置；空值兼容旧配置。 */
    val moduleOrder: List<String> = emptyList(),
    /**
     * 可切换形态的模块当前形态编号。
     *
     * 键为模块 id，值为形态编号，缺省时由模块自身决定默认形态；
     * 整体卡片组件以 weread_board 为键保存本周、本月、本书、本记与待办五形态。
     */
    val forms: Map<String, Int> = emptyMap(),
    /** 是否已按新版默认顺序初始化过模块顺序，避免升级后重复重排。 */
    val cardOrderInitialized: Boolean = false,
    /**
     * 书架分区的展示行数，每行两本。
     *
     * 旧配置缺少该字段时取 [SHELF_ROWS_DEFAULT]；越界值在读取时由 [effectiveShelfRows]
     * 夹取到 [SHELF_ROWS_MIN] 与 [SHELF_ROWS_MAX] 之间，写回配置的始终是夹取后的值。
     */
    val shelfRows: Int = SHELF_ROWS_DEFAULT,
    /**
     * 书架分区展示的书源，取值 weread 或 local。
     *
     * 旧配置缺少该字段时取 [SHELF_SOURCE_DEFAULT]；未知值、空串与大小写差值
     * 在读取时由 [effectiveShelfSource] 归一为默认书源。
     */
    val shelfSource: String = SHELF_SOURCE_DEFAULT,
) {
    /** 归一后的书架书源：仅识别 local，其余一律按微信读书处理。 */
    val effectiveShelfSource: String
        get() = if (shelfSource == SHELF_SOURCE_LOCAL) SHELF_SOURCE_LOCAL else SHELF_SOURCE_WEREAD

    /** 夹取到合法区间的书架行数。 */
    val effectiveShelfRows: Int
        get() = shelfRows.coerceIn(SHELF_ROWS_MIN, SHELF_ROWS_MAX)

    /** 书架分区本次展示的条目上限，等于有效行数与每行本数之积。 */
    val shelfVisibleLimit: Int
        get() = effectiveShelfRows * SHELF_COLUMNS

    companion object {
        /** 书架行数下限。 */
        const val SHELF_ROWS_MIN: Int = 1

        /** 书架行数上限。 */
        const val SHELF_ROWS_MAX: Int = 5

        /** 书架行数默认值。 */
        const val SHELF_ROWS_DEFAULT: Int = 2

        /** 书架分区每行的卡片数。 */
        const val SHELF_COLUMNS: Int = 2

        /** 书架分区的取书上限，等于行数上限与每行本数之积。 */
        const val SHELF_ITEM_LIMIT: Int = SHELF_ROWS_MAX * SHELF_COLUMNS

        /** 书架书源：微信读书。 */
        const val SHELF_SOURCE_WEREAD: String = "weread"

        /** 书架书源：本地目录。 */
        const val SHELF_SOURCE_LOCAL: String = "local"

        /** 书架书源默认值。 */
        const val SHELF_SOURCE_DEFAULT: String = SHELF_SOURCE_WEREAD
    }
}

/** 应用排序方式。 */
@Serializable
enum class SortMode {
    /** 首次安装时间降序，上游默认行为。 */
    INSTALL_TIME,

    /** 标签拼音升序。 */
    LABEL,

    /** 包名升序。 */
    PACKAGE,

    /** 用户手动顺序。 */
    MANUAL,
}

/**
 * 桌面配置。
 *
 * 参数 kind 为桌面形态；widgetId 为空表示不启用组件；
 * iconOnly 为仅展示图标；order 为用户排序后的条目键；
 * hiddenPackages 为隐藏应用包名。
 *
 * 版面覆盖项取 0 或负值时沿用设计令牌推导结果：
 * columnSpacingDp 为列间距、rowSpacingDp 为行间距、iconSizeDp 为图标外框边长、
 * labelTextSp 为应用名字号、firstPageRows 为首页应用行数。
 *
 * 长按分两级：longPressMenuMs 内抬起弹出单条菜单，继续按住到 editHoldMs 进入整理模式。
 * negativePage 配置左滑进入的负一屏。
 */
@Serializable
data class DesktopConfig(
    val kind: DesktopKind = DesktopKind.GRID,
    val widgetId: String = "clock",
    val iconOnly: Boolean = false,
    val sortMode: SortMode = SortMode.INSTALL_TIME,
    val hiddenPackages: Set<String> = DEFAULT_HIDDEN_PACKAGES,
    val order: List<String> = emptyList(),
    val pageLimit: Int = 15,
    val columns: Int = 0,
    val rows: Int = 0,
    val showPageIndicator: Boolean = true,
    val longPressEditMs: Int = 2000,
    val swipeMinDp: Int = 48,
    /** 自定义图片组件的图片路径，空表示使用占位图。 */
    val widgetImagePath: String? = null,
    /** 图片组件多图轮播的切换间隔，单位秒，取值见 [IMAGE_CAROUSEL_OPTIONS]。 */
    val imageCarouselSeconds: Int = IMAGE_CAROUSEL_DEFAULT,
    /**
     * 微读整体卡片可切换的形态位掩码。
     *
     * 第零位本周、第一位本月、第二位本书、第三位本记、第四位待办，默认五形态全开。
     */
    val wereadBoardPoolMask: Int = WEREAD_BOARD_POOL_DEFAULT,
    val columnSpacingDp: Int = 0,
    val rowSpacingDp: Int = 0,
    val iconSizeDp: Int = 0,
    val labelTextSp: Float = 0f,
    val gridAlign: GridAlign = GridAlign.CENTER,
    val firstPageRows: Int = 0,
    val longPressMenuMs: Int = 400,
    val editHoldMs: Int = 1200,
    val negativePage: NegativePageConfig = NegativePageConfig(),
    /** 桌面隐藏的内置入口键，例如 special:weread。 */
    val hiddenSpecials: Set<String> = DEFAULT_HIDDEN_SPECIALS,
    /** 首次启用桌面时是否已按安装时间固定过一次顺序。 */
    val installOrderInitialized: Boolean = false,
)

/**
 * 新版删除的微信读书类组件标识。
 *
 * 这些组件由整体卡片 weread_board 承载五种形态，桌面迁移时统一改写为整体卡片。
 */
val REMOVED_WEREAD_WIDGET_IDS: Set<String> =
    setOf(
        "weread_covers",
        "weread_month",
        "weread_mark",
        "weread_note",
        "weread_stats",
        "weread_bill",
        "weread_rank",
        "weread_fingerprint",
    )

/**
 * 新版删除的非微信读书类组件标识。
 *
 * 这些组件与保留的钟面、日历、图片与使用统计重复，桌面迁移时统一改写为钟面。
 */
val REMOVED_OTHER_WIDGET_IDS: Set<String> =
    setOf(
        "clock_compact",
        "covers",
        "koreader_month",
        "reading",
        "weather",
    )

/**
 * 把已被删除的组件标识改写为保留组件。
 *
 * 微信读书类组件改写为整体卡片 weread_board，其余改写为钟面 clock；
 * 未命中删除清单的组件标识保持原值，空标识表示不启用组件，同样保持原值。
 *
 * @param desktop 需要迁移的桌面配置。
 * @return 组件标识已按删除清单改写的桌面配置。
 */
fun rewriteRemovedWidgetIds(desktop: DesktopConfig): DesktopConfig {
    val current = desktop.widgetId
    val rewritten =
        when {
            current in REMOVED_WEREAD_WIDGET_IDS -> WEREAD_BOARD_WIDGET_ID
            current in REMOVED_OTHER_WIDGET_IDS -> "clock"
            else -> current
        }
    return if (rewritten == current) desktop else desktop.copy(widgetId = rewritten)
}

/** 整体卡片组件标识，承载微信读书五种形态。 */
const val WEREAD_BOARD_WIDGET_ID: String = "weread_board"

/** 图片组件多图轮播间隔的候选值，单位秒。 */
val IMAGE_CAROUSEL_OPTIONS: List<Int> = listOf(5, 10, 15, 30, 60)

/** 图片组件多图轮播间隔的默认值，单位秒。 */
const val IMAGE_CAROUSEL_DEFAULT: Int = 15

/**
 * 图片组件多图轮播间隔的生效值。
 *
 * 配置值落在 [IMAGE_CAROUSEL_OPTIONS] 之外时回退 [IMAGE_CAROUSEL_DEFAULT]，
 * 旧版本配置缺少该字段时同样取默认值。
 */
val DesktopConfig.effectiveImageCarouselSeconds: Int
    get() = imageCarouselSeconds.takeIf { it in IMAGE_CAROUSEL_OPTIONS } ?: IMAGE_CAROUSEL_DEFAULT

/** 微读整体卡片形态位的默认取值，五形态全开。 */
const val WEREAD_BOARD_POOL_DEFAULT: Int = 0b11111

/** 微读整体卡片形态位的取值范围上限，低五位有效。 */
const val WEREAD_BOARD_POOL_LIMIT: Int = 0b11111

/** 微读整体卡片当前可用的形态位，零值时退回全部形态。 */
val DesktopConfig.effectiveWereadBoardPoolMask: Int
    get() = (wereadBoardPoolMask and WEREAD_BOARD_POOL_LIMIT).takeIf { it != 0 } ?: WEREAD_BOARD_POOL_DEFAULT

/**
 * 外观配置。
 *
 * 参数 wallpaperPath 为壁纸文件路径，空表示纯白；
 * wallpaperWhiteOverlay 表示叠加半透明白层；
 * labelFontPath 为应用名 TTF 路径；clockFont 为钟面字体标识；
 * twelveHour 为十二小时制；themeId 为主题包标识；textScale 为整体字号缩放。
 *
 * iconShape 为图标框外形；iconContrast 为图标二值化档位，0 表示不做处理，
 * 1 到 3 逐级提高阈值，用于让彩色图标在墨水屏上黑白分明；
 * clockFace 为钟面样式标识；themeTokensJson 为主题令牌覆盖串，空表示使用内置令牌；
 * invertIsland 为文字岛颜色反转；
 * wallpaperQuantize 为桌面壁纸量化档位，lockWallpaperPath 为锁屏壁纸文件路径，
 * lockWallpaperQuantize 为锁屏壁纸量化档位；量化档位 0 表示关闭，1、2、3 分别对应
 * 二级灰、四级灰、八级灰，锁屏默认四级灰。
 */
@Serializable
data class AppearanceConfig(
    val wallpaperPath: String? = null,
    val wallpaperWhiteOverlay: Boolean = false,
    val labelFontPath: String? = null,
    val clockFont: String = "default",
    val twelveHour: Boolean = false,
    val themeId: String = "ink",
    val iconPackId: String = "builtin",
    val textScale: Float = 1f,
    val showLabels: Boolean = true,
    val highContrast: Boolean = true,
    val statusBarHeightDp: Int = 0,
    val iconShape: IconShape = IconShape.ROUNDED,
    val iconContrast: Int = 0,
    val clockFace: String = "default",
    val themeTokensJson: String = "",
    val invertIsland: Boolean = false,
    val wallpaperDim: Float = 0f,
    /** 桌面壁纸量化档位，0 关闭、1 二级灰、2 四级灰、3 八级灰。 */
    val wallpaperQuantize: Int = 0,
    /** 锁屏壁纸文件路径，空串表示未设置锁屏壁纸。 */
    val lockWallpaperPath: String = "",
    /** 锁屏壁纸量化档位，语义与桌面壁纸量化一致，默认四级灰。 */
    val lockWallpaperQuantize: Int = 2,
    /**
     * 单应用图标覆盖。
     *
     * 键为应用包名，值为应用私有目录内图片的绝对路径；
     * 图标加载时优先使用覆盖图片，清除后回退系统图标。
     */
    val iconOverrides: Map<String, String> = emptyMap(),
    /** 缩略图质量档位：0 省内存、1 平衡、2 清晰；默认平衡，与既有 256 长边一致。 */
    val thumbnailQuality: Int = THUMBNAIL_QUALITY_DEFAULT,
    /** 缩略图写入磁盘前是否量化为四档灰；默认开启，与既有行为一致。 */
    val thumbnailDither: Boolean = true,
) {
    /** 缩略图质量的有效档位，越界值回退默认档。 */
    val effectiveThumbnailQuality: Int
        get() = thumbnailQuality.takeIf { it in THUMBNAIL_QUALITY_OPTIONS } ?: THUMBNAIL_QUALITY_DEFAULT

    companion object {
        /** 缩略图质量默认档，平衡。 */
        const val THUMBNAIL_QUALITY_DEFAULT: Int = 1

        /** 缩略图质量可选档位：省内存、平衡、清晰。 */
        val THUMBNAIL_QUALITY_OPTIONS: List<Int> = listOf(0, 1, 2)
    }
}

/**
 * 快捷托盘配置。
 *
 * 参数 brightnessCold 与 brightnessWarm 为前光两通道档位，取值 0 到 10；
 * notificationLimit 为托盘内通知条数上限；overlayEnabled 为悬浮层开关；
 * autoRefreshMinutes 为定时整屏刷新间隔，0 表示关闭；
 * refreshOnAppExit 表示从其他应用回到桌面后执行一次整屏刷新，
 * refreshOnAppExitDelayMs 为该次刷新的延迟毫秒数，用于等待窗口合成完成。
 */
@Serializable
data class TrayConfig(
    val enabled: Boolean = true,
    val notificationLimit: Int = 4,
    val brightnessCold: Int = 5,
    val brightnessWarm: Int = 5,
    val mediaEnabled: Boolean = true,
    val overlayEnabled: Boolean = false,
    val showWifi: Boolean = true,
    val showBluetooth: Boolean = true,
    val showBatteryPercent: Boolean = true,
    val autoRefreshMinutes: Int = 10,
    val volumeKeyFlip: Boolean = false,
    val refreshOnAppExit: Boolean = false,
    val refreshOnAppExitDelayMs: Int = 100,
)

/**
 * 通知展示配置。
 *
 * 参数 foldByApp 表示同一应用的多条通知在托盘内折叠为一组；
 * mergeConversation 表示按会话合并同一聊天应用的连续通知；
 * stripSummary 表示丢弃系统汇总类通知的证据文本；
 * badgeMax 为桌面角标显示的最大数值，超出显示为加号；
 * showAppLabel 表示通知条目是否附带应用名。
 */
@Serializable
data class NotificationConfig(
    val foldByApp: Boolean = false,
    val mergeConversation: Boolean = true,
    val stripSummary: Boolean = true,
    val badgeMax: Int = 99,
    val showAppLabel: Boolean = true,
    val badgeEnabled: Boolean = true,
)

/**
 * 锁屏配置。
 *
 * 参数 pinHash 与 salt 为 PIN 摘要与盐；iterations 为 PBKDF2 轮数，
 * 低于 [PinHasher.DEFAULT_ITERATIONS] 的旧值在计算时统一提升；
 * enabled 为是否启用锁屏；autoLock 表示熄屏后立即上锁；
 * emergencyResetEnabled 表示锁屏界面长按删除键满十秒后是否允许应急清除密码，
 * 旧配置缺少该字段时按开启解析；failedAttempts 为连续校验失败次数；
 * lockoutUntilMillis 为退避锁定截止时间。
 */
@Serializable
data class LockConfig(
    val pinHash: String? = null,
    val salt: String? = null,
    val iterations: Int = PinHasher.DEFAULT_ITERATIONS,
    val enabled: Boolean = false,
    val autoLock: Boolean = true,
    val emergencyResetEnabled: Boolean = true,
    val failedAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L,
)

/**
 * 墨单卡片的配置。
 *
 * 参数取值与微读墨记 cfg 偏好一一对应：unit 为时长单位，取值 h 或 m；
 * topN 为榜单条数，取值 3、5、8；minSeconds 为入选最短时长，单位秒；
 * dropOffShelf 表示过滤已下架书籍；noteSource 为笔记来源，
 * 取值 auto、manual、auto_first；excerptMode 为摘录模式，
 * 取值 mine、hot_fill、hot_only、any；footer 为页脚类型，
 * 取值 none、note、barcode、barcode_note；blocksMask 为块显示位，
 * 位 1 展示票据头、位 2 展示合计行、位 4 展示整单备注，0 合法；
 * view 为视图形态，0 摘录菜单、1 读书菜单。
 */
@Serializable
data class WereadMenuConfig(
    val title: String = TITLE_DEFAULT,
    val unit: String = UNIT_DEFAULT,
    val topN: Int = TOP_N_DEFAULT,
    val minSeconds: Int = 0,
    val dropOffShelf: Boolean = true,
    val noteSource: String = NOTE_SOURCE_DEFAULT,
    val excerptMode: String = EXCERPT_DEFAULT,
    val showAuthor: Boolean = true,
    val showDuration: Boolean = true,
    val showProgress: Boolean = false,
    val footer: String = FOOTER_DEFAULT,
    val blocksMask: Int = BLOCKS_MASK_DEFAULT,
    val view: Int = VIEW_DEFAULT,
) {
    /** 校验后的时长单位，非法值回退默认值。 */
    val effectiveUnit: String
        get() = unit.takeIf { it in UNIT_OPTIONS } ?: UNIT_DEFAULT

    /** 校验后的榜单条数，非法值回退默认值。 */
    val effectiveTopN: Int
        get() = topN.takeIf { it in TOP_N_OPTIONS } ?: TOP_N_DEFAULT

    /** 校验后的笔记来源，非法值回退默认值。 */
    val effectiveNoteSource: String
        get() = noteSource.takeIf { it in NOTE_SOURCE_OPTIONS } ?: NOTE_SOURCE_DEFAULT

    /** 校验后的摘录模式，非法值回退默认值。 */
    val effectiveExcerptMode: String
        get() = excerptMode.takeIf { it in EXCERPT_OPTIONS } ?: EXCERPT_DEFAULT

    /** 校验后的页脚类型，非法值回退默认值。 */
    val effectiveFooter: String
        get() = footer.takeIf { it in FOOTER_OPTIONS } ?: FOOTER_DEFAULT

    /** 校验后的块显示位，只保留低三位，其余位丢弃。 */
    val effectiveBlocksMask: Int
        get() = blocksMask and BLOCKS_MASK_LIMIT

    /** 校验后的视图形态，非法值回退摘录菜单。 */
    val effectiveView: Int
        get() = view.takeIf { it in VIEW_OPTIONS } ?: VIEW_DEFAULT

    /** 页眉标题的实际展示文本，空串回退默认标题。 */
    val effectiveTitle: String
        get() = title.ifBlank { TITLE_DEFAULT }

    /** 入选最短时长的生效秒数，负值按零处理。 */
    val effectiveMinSeconds: Int
        get() = minSeconds.coerceAtLeast(0)

    companion object {
        /** 默认页眉标题。 */
        const val TITLE_DEFAULT: String = "墨单"

        /** 时长单位候选值，h 为小时、m 为分钟。 */
        val UNIT_OPTIONS: List<String> = listOf("h", "m")

        /** 默认时长单位。 */
        const val UNIT_DEFAULT: String = "h"

        /** 榜单条数候选值。 */
        val TOP_N_OPTIONS: List<Int> = listOf(3, 5, 8)

        /** 默认榜单条数。 */
        const val TOP_N_DEFAULT: Int = 5

        /** 笔记来源候选值。 */
        val NOTE_SOURCE_OPTIONS: List<String> = listOf("auto", "manual", "auto_first")

        /** 默认笔记来源。 */
        const val NOTE_SOURCE_DEFAULT: String = "auto_first"

        /** 摘录模式候选值。 */
        val EXCERPT_OPTIONS: List<String> = listOf("mine", "hot_fill", "hot_only", "any")

        /** 默认摘录模式。 */
        const val EXCERPT_DEFAULT: String = "mine"

        /** 页脚类型候选值。 */
        val FOOTER_OPTIONS: List<String> = listOf("none", "note", "barcode", "barcode_note")

        /** 默认页脚类型。 */
        const val FOOTER_DEFAULT: String = "barcode_note"

        /** 默认块显示位，票据头、合计行与整单备注全部展示。 */
        const val BLOCKS_MASK_DEFAULT: Int = 7

        /** 块显示位的取值范围上限，低三位有效。 */
        const val BLOCKS_MASK_LIMIT: Int = 0b111

        /** 视图形态候选值，0 摘录菜单、1 读书菜单。 */
        val VIEW_OPTIONS: List<Int> = listOf(0, 1)

        /** 默认视图形态。 */
        const val VIEW_DEFAULT: Int = 0
    }
}

/**
 * 微读墨记墨台的分区顺序与开关配置。
 *
 * 参数 order 为分区标识顺序，取值 bill 墨单、rank 阅读资产、fp 阅读指纹；
 * onMask 为显示位掩码，位 1 墨单、位 2 阅读资产、位 4 阅读指纹，
 * 与微读墨记 PagePrefs 的取值一致。负一屏三项卡片与桌面整体卡片共用该配置。
 */
@Serializable
data class WereadDeskConfig(
    val order: List<String> = DEFAULT_ORDER,
    val onMask: Int = ON_MASK_DEFAULT,
    val syncedAtMillis: Long = 0,
    /** 阅读资产只统计书架内书目。 */
    val assetShelfOnly: Boolean = false,
    /** 阅读资产只统计读完的书目。 */
    val assetFinishedOnly: Boolean = false,
    /** 已移出书架的条目标识，对应原版墨台的隐藏书目。 */
    val hiddenIds: List<String> = emptyList(),
    /** 阅读指纹字号，取值见 [FP_FONT_OPTIONS]。 */
    val fpFontPx: Int = FP_FONT_DEFAULT,
    /** 阅读指纹选书，按勾选顺序保存，最多 50 本。 */
    val fpBookIds: List<String> = emptyList(),
    /** 区分尚未迁移与用户明确清空。 */
    val fpBooksConfigured: Boolean = false,
) {
    /** 排除空标识与重复项，保留用户选择顺序。 */
    val effectiveFpBookIds: List<String>
        get() =
            fpBookIds
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(FP_BOOK_LIMIT)

    /** 校验后的分区顺序：丢弃未知标识并去重，为空时回退默认顺序。 */
    val effectiveOrder: List<String>
        get() {
            val filtered = order.filter { it in ORDER_OPTIONS }.distinct()
            return filtered.ifEmpty { DEFAULT_ORDER }
        }

    /** 校验后的显示位，只保留低三位。 */
    val effectiveOnMask: Int
        get() = onMask and ON_MASK_LIMIT

    /** 指定分区是否可见。 */
    fun isVisible(id: String): Boolean {
        val bit =
            when (id) {
                "bill" -> BIT_BILL
                "rank" -> BIT_RANK
                "fp" -> BIT_FP
                else -> 0
            }
        return bit != 0 && effectiveOnMask and bit != 0
    }

    companion object {
        /** 分区标识，依次为墨单、阅读资产、阅读指纹。 */
        val ORDER_OPTIONS: List<String> = listOf("bill", "rank", "fp")

        /** 默认分区顺序。 */
        val DEFAULT_ORDER: List<String> = ORDER_OPTIONS

        /** 墨单分区的显示位。 */
        const val BIT_BILL: Int = 1

        /** 阅读资产分区的显示位。 */
        const val BIT_RANK: Int = 2

        /** 阅读指纹分区的显示位。 */
        const val BIT_FP: Int = 4

        /** 默认显示位：墨单与阅读资产开启，阅读指纹关闭。 */
        const val ON_MASK_DEFAULT: Int = BIT_BILL or BIT_RANK

        /** 显示位的取值范围上限，低三位有效。 */
        const val ON_MASK_LIMIT: Int = BIT_BILL or BIT_RANK or BIT_FP

        /** 阅读指纹字号档，与原版墨台一致。 */
        val FP_FONT_OPTIONS: List<Int> = listOf(16, 17, 18, 19)

        /** 阅读指纹默认字号。 */
        const val FP_FONT_DEFAULT: Int = 18

        /** 原版指纹选书上限。 */
        const val FP_BOOK_LIMIT: Int = 50
    }

    /** 校验后的指纹字号：不在档位内时回退默认值。 */
    val effectiveFpFontPx: Int
        get() = fpFontPx.takeIf { it in FP_FONT_OPTIONS } ?: FP_FONT_DEFAULT
}

/**
 * 外部集成配置。
 *
 * 参数 wereadApiKey 为微信读书密钥，默认空值表示未配置，由用户在设置页填写；
 * booksDir 为书架根目录名；weatherLat 与 weatherLon 为空时按 IP 定位；
 * updateUrl 为更新清单地址，空表示使用内置地址。
 */
@Serializable
data class IntegrationsConfig(
    val wereadApiKey: String = "",
    val wereadEnabled: Boolean = true,
    val koreaderEnabled: Boolean = true,
    /**
     * 书架是否收录本地目录中的书籍。
     *
     * 兼容字段：书架书源已迁移到 [NegativePageConfig.shelfSource]，V11 迁移读取本字段后
     * 恢复默认值，界面不再读写，保留仅为解析旧配置。
     */
    val bookshelfLocalEnabled: Boolean = false,
    /** 兼容字段：书架是否收录微信读书书架内容，语义同上。 */
    val bookshelfWereadEnabled: Boolean = true,
    /** 兼容字段：旧版书架每页展示行数，V11 迁移后恢复默认值。 */
    val bookshelfRows: Int = 3,
    val booksDir: String = "Books",
    val weatherEnabled: Boolean = true,
    val weatherCity: String = "",
    val weatherLat: Double? = null,
    val weatherLon: Double? = null,
    val usageEnabled: Boolean = false,
    /** 微信读书数据刷新间隔，单位分钟，取值 30、60、180、360，其余按 60 处理。 */
    val wereadRefreshMinutes: Int = WEREAD_REFRESH_DEFAULT,
    /** 微信读书统计默认范围，取值 month、year、overall，其余按 month 处理。 */
    val wereadStatsRange: String = WEREAD_STATS_DEFAULT,
    val updateChannel: String = "stable",
    val updateUrl: String = "",
    /** 墨单卡片的外观与筛选配置，同时作用于负一屏墨单与桌面整体卡片的墨单形态。 */
    val wereadMenu: WereadMenuConfig = WereadMenuConfig(),
    /** 微读墨记墨台的分区顺序与开关，负一屏三项与桌面整体卡片共用。 */
    val wereadDesk: WereadDeskConfig = WereadDeskConfig(),
    /** 微信读书数据来源：synced 通过官方接口更新，local 仅使用本机缓存。 */
    val wereadSource: String = WEREAD_SOURCE_DEFAULT,
) {
    /** 校验微信读书刷新间隔，非法值按默认六十分钟处理。 */
    val effectiveWereadRefreshMinutes: Int
        get() =
            wereadRefreshMinutes.takeIf { it in WEREAD_REFRESH_OPTIONS }
                ?: WEREAD_REFRESH_DEFAULT

    /** 校验微信读书统计范围，非法值按默认月统计处理。 */
    val effectiveWereadStatsRange: String
        get() =
            wereadStatsRange.takeIf { it in WEREAD_STATS_OPTIONS }
                ?: WEREAD_STATS_DEFAULT

    /** 旧 imported 与未知来源统一使用官方同步，保留已有缓存。 */
    val effectiveWereadSource: String
        get() =
            wereadSource.takeIf { it in WEREAD_SOURCE_OPTIONS }
                ?: WEREAD_SOURCE_DEFAULT

    companion object {
        /** 微信读书刷新间隔默认值，单位分钟。 */
        const val WEREAD_REFRESH_DEFAULT: Int = 60

        /** 微信读书刷新间隔可选值，单位分钟。 */
        val WEREAD_REFRESH_OPTIONS: List<Int> = listOf(30, 60, 180, 360)

        /** 微信读书统计范围默认值。 */
        const val WEREAD_STATS_DEFAULT: String = "month"

        /** 微信读书统计范围可选值，依次为月、年与全部。 */
        val WEREAD_STATS_OPTIONS: List<String> = listOf("month", "year", "overall")

        /** 默认通过官方接口同步。 */
        const val WEREAD_SOURCE_DEFAULT: String = "synced"

        /** 数据来源可选值：官方同步与仅本地缓存。 */
        val WEREAD_SOURCE_OPTIONS: List<String> = listOf("synced", "local")
    }
}

/** 首次引导配置。 */
@Serializable
data class OnboardingConfig(
    val completed: Boolean = false,
    val createdAt: Long = 0L,
)

/**
 * 微信读书数据迁移状态。
 *
 * 参数 sourceVersionCode 为迁移来源版本号；importedAtMillis 为导入完成时间；
 * manifestDigest 为迁移清单摘要，摘要一致时跳过重复导入；complete 表示迁移已完整结束。
 */
@Serializable
data class WereadMigrationState(
    val sourceVersionCode: Int = 0,
    val importedAtMillis: Long = 0L,
    val manifestDigest: String = "",
    val complete: Boolean = false,
)

/**
 * 微信读书成就配置。
 *
 * rawJson 保留原版 achievement.json 的原始文本，用于原样展示与回写；
 * updatedAtMillis 为最后一次写入时间。字段结构由原版决定，不在此处拆解。
 */
@Serializable
data class WereadAchievementConfig(
    val rawJson: String = "",
    val updatedAtMillis: Long = 0L,
)

/**
 * 全部配置的聚合根。
 *
 * 参数 version 用于迁移；各 section 独立演进。
 */
@Serializable
data class LauncherConfig(
    val version: Int = CURRENT_VERSION,
    val desktop: DesktopConfig = DesktopConfig(),
    val appearance: AppearanceConfig = AppearanceConfig(),
    val tray: TrayConfig = TrayConfig(),
    val lock: LockConfig = LockConfig(),
    val integrations: IntegrationsConfig = IntegrationsConfig(),
    val onboarding: OnboardingConfig = OnboardingConfig(),
    val notifications: NotificationConfig = NotificationConfig(),
    /** 微信读书数据迁移状态，未迁移时保持默认值。 */
    val wereadMigration: WereadMigrationState = WereadMigrationState(),
    /** 微信读书成就配置，未迁移时保持空值。 */
    val wereadAchievement: WereadAchievementConfig = WereadAchievementConfig(),
) {
    companion object {
        /** 当前配置结构版本，读取旧配置时据此执行迁移。 */
        const val CURRENT_VERSION = 13
    }
}

/**
 * 返回剔除本机凭据的配置副本，供备份导出使用。
 *
 * 微信读书密钥、锁屏摘要与盐属于本机凭据，写入备份文件会使凭据随文件外流，
 * 导出前统一置空；导入端沿用本机已保存的凭据，不读取备份中的这三个字段。
 *
 * @return 凭据字段已清空的配置副本，其余字段保持原值。
 */
fun LauncherConfig.withoutLocalCredentials(): LauncherConfig =
    copy(
        integrations = integrations.copy(wereadApiKey = ""),
        lock = lock.copy(pinHash = null, salt = null),
    )

/**
 * 已安装应用的元数据。
 *
 * 参数 packageName 为包名；label 为显示名；activityName 为启动 Activity；
 * firstInstallTime 为首次安装时间；isSystem 表示系统应用。
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val activityName: String?,
    val firstInstallTime: Long,
    val isSystem: Boolean,
)

/** 桌面入口类型。 */
enum class EntryType {
    APP,
    SPECIAL,
}

/** 内置特殊入口。 */
enum class SpecialEntry {
    BOOKSHELF,
    FILES,
    KOREADER,
    SEARCH,
    SETTINGS,
    WEREAD,
}

/**
 * 桌面上的一个入口。
 *
 * 参数 key 为持久化键，应用使用 `app:<包名>`，特殊入口使用 `special:<名称>`；
 * iconKey 用于图标缓存；special 仅在 EntryType.SPECIAL 时有效。
 */
data class LauncherEntry(
    val key: String,
    val type: EntryType,
    val label: String,
    val packageName: String? = null,
    val special: SpecialEntry? = null,
) {
    /** 条目是否为应用入口。 */
    val isApp: Boolean get() = type == EntryType.APP
}

/** 能力标识，用于组件与设置的权限提示。 */
enum class Capability {
    WIFI,
    BLUETOOTH,
    FRONT_LIGHT,
    NOTIFICATIONS,
    USAGE_ACCESS,
    LOCATION,
    STORAGE,
    OVERLAY,
    WEREAD,
    KOREADER,
    MEDIA,
    WEATHER,
    BATTERY,
}

/** 电池状态。 */
data class BatteryState(
    val percent: Int,
    val charging: Boolean,
)

/** WiFi 简化状态。 */
data class WifiState(
    val enabled: Boolean,
    val connected: Boolean,
    val ssid: String,
    val rssi: Int,
) {
    /** 信号档位，0 到 3。 */
    val level: Int
        get() =
            when {
                !connected -> 0
                rssi >= -55 -> 3
                rssi >= -70 -> 2
                else -> 1
            }
}

/** 蓝牙简化状态。 */
data class BluetoothState(
    val enabled: Boolean,
    val connectedName: String?,
)

/** 前光状态，两个通道各 0 到 10 档。 */
data class FrontLightState(
    val cold: Int,
    val warm: Int,
    val available: Boolean,
)

/** 媒体播放状态。 */
data class MediaState(
    val active: Boolean,
    val playing: Boolean,
    val title: String,
    val artist: String,
    val packageName: String?,
) {
    companion object {
        /** 空状态。 */
        val IDLE = MediaState(false, false, "", "", null)
    }
}

/**
 * 通知条目。
 *
 * 参数 key 为去重键；postedAt 为投递时间；
 * appLabel 为应用显示名，供托盘与桌面角标展示；
 * groupKey 为会话归并键，同一会话的连续通知共享该键；
 * conversationTitle 为会话名或群名，普通通知为空；
 * isSummary 表示系统汇总通知，isOngoing 表示常驻通知；
 * isGroup 表示该条目为按应用折叠后的代表条目，count 为折叠数量。
 */
data class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val appLabel: String = "",
    val groupKey: String = "",
    val conversationTitle: String? = null,
    val isSummary: Boolean = false,
    val isOngoing: Boolean = false,
    val isGroup: Boolean = false,
    val count: Int = 1,
)

/**
 * 阅读快照，覆盖 KOReader 与本地书架。
 *
 * 参数 source 为来源标识；progress 为 0 到 1 的阅读进度；
 * todayMinutes 与 monthMinutes 为阅读时长。
 */
data class ReadingSnapshot(
    val source: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val progress: Float,
    val todayMinutes: Int,
    val monthMinutes: Int,
    val lastReadAt: Long,
)

/** 天气信息，温度单位摄氏度。 */
data class WeatherSnapshot(
    val city: String,
    val temperature: Int,
    val condition: String,
    val code: String,
    val high: Int,
    val low: Int,
    val updatedAt: Long,
    val hourly: List<WeatherHour> = emptyList(),
    val daily: List<WeatherDay> = emptyList(),
)

/** 逐时天气。 */
data class WeatherHour(
    val hourLabel: String,
    val temperature: Int,
    val code: String,
)

/** 逐日天气。 */
data class WeatherDay(
    val label: String,
    val high: Int,
    val low: Int,
    val code: String,
)

/** 使用统计条目。 */
data class UsageEntry(
    val label: String,
    val packageName: String,
    val minutes: Int,
)

/** 书架条目。 */
@Serializable
data class BookItem(
    val path: String,
    val title: String,
    val author: String,
    val format: String,
    val coverPath: String?,
    val sizeBytes: Long,
    val addedAt: Long,
)

/** 微信读书书架条目。 */
@Serializable
data class WereadShelfItem(
    val bookId: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val progress: Float,
    val deepLink: String?,
)

/** 微信读书月度阅读统计。 */
data class WereadMonth(
    val monthMinutes: Int,
    val lastMonthMinutes: Int,
    val days: List<Int>,
)

/** 微信读书摘录。 */
data class WereadHighlight(
    val text: String,
    val bookTitle: String,
    val createdAt: Long,
)

/** KOReader 月热力数据。 */
data class KoreaderHeat(
    val days: List<Int>,
    val maxMinutes: Int,
)

/** 书架合集。 */
data class BookCollection(
    val id: String,
    val name: String,
    val bookPaths: List<String>,
)

/**
 * 主题保存范围。
 *
 * HOME 只记录首页应用与图标覆盖；ALL 记录全部页面应用。
 */
@Serializable
enum class ThemeScope {
    HOME,
    ALL,
}

/**
 * 主题中保存的桌面布局参数。
 *
 * 与应用顺序、图标覆盖一起保存，应用主题时写回桌面配置；
 * 值为 0 的字段表示使用设备自适应值。
 */
@Serializable
data class ThemeLayout(
    val columns: Int = 0,
    val rows: Int = 0,
    val columnSpacingDp: Int = 0,
    val rowSpacingDp: Int = 0,
    val iconSizeDp: Int = 0,
    val labelTextSp: Float = 0f,
    val firstPageRows: Int = 0,
    val gridAlign: GridAlign = GridAlign.CENTER,
)

/**
 * 美化主题。
 *
 * 记录桌面顺序与单应用图标覆盖，用于整体保存、复用、导出与恢复。
 * 参数 id 为稳定标识；name 为用户可见名称；iconOverrides 的键为包名，
 * 值为主题目录内图片的绝对路径；order 为 LauncherEntry.key 顺序。
 */
@Serializable
data class BeautifyTheme(
    val id: String,
    val name: String,
    val createdAt: Long,
    val iconOverrides: Map<String, String> = emptyMap(),
    val order: List<String> = emptyList(),
    val layout: ThemeLayout? = null,
    val scope: ThemeScope = ThemeScope.ALL,
)
