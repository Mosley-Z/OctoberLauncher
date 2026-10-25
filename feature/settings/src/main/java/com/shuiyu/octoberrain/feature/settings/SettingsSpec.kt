package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import com.shuiyu.octoberrain.core.common.LauncherConfig

/**
 * 设置项声明表。
 *
 * 每个设置项以一条 [SettingSpec] 声明：归属分组、标题、说明、控件类型、读取与写入方式、
 * 生效方式、可见性条件。页面只提供动作与自定义行，行控件由 SettingsSpecRenderer 统一生成。
 * 新增一项设置只需在 [SettingsCatalog.specs] 追加一条声明，页面代码不必改动。
 */
internal object SettingsCatalog {
    // ---- 分组标识 ----

    /** 外观页壁纸分组。 */
    const val GROUP_APPEARANCE_WALLPAPER = "appearance.wallpaper"

    /** 外观页主题与图标分组。 */
    const val GROUP_APPEARANCE_THEME = "appearance.theme"

    /** 外观页字体分组。 */
    const val GROUP_APPEARANCE_FONTS = "appearance.fonts"

    /** 外观页文字与密度分组。 */
    const val GROUP_APPEARANCE_TEXT = "appearance.text"

    /** 外观页显示分组。 */
    const val GROUP_APPEARANCE_DISPLAY = "appearance.display"

    /** 外观页图片与缓存分组。 */
    const val GROUP_APPEARANCE_IMAGE = "appearance.image"

    /** 托盘页状态栏分组。 */
    const val GROUP_TRAY_STATUS = "tray.status"

    /** 托盘页自动刷新分组。 */
    const val GROUP_TRAY_REFRESH = "tray.refresh"

    /** 锁屏页锁屏分组。 */
    const val GROUP_LOCK_SECURITY = "lock.security"

    /** 锁屏页行为分组。 */
    const val GROUP_LOCK_BEHAVIOR = "lock.behavior"

    /** 系统设置页连接分组。 */
    const val GROUP_SYSTEM_CONNECTION = "system.connection"

    /** 系统设置页显示与声音分组。 */
    const val GROUP_SYSTEM_DISPLAY = "system.display"

    /** 系统设置页设备分组。 */
    const val GROUP_SYSTEM_DEVICE = "system.device"

    /** 系统设置页系统分组。 */
    const val GROUP_SYSTEM_SYSTEM = "system.system"

    /** 阅读与数据页微信读书分组。 */
    const val GROUP_BOOKSHELF_WEREAD = "bookshelf.weread"

    /** 阅读与数据页本地目录分组。 */
    const val GROUP_BOOKSHELF_LOCAL = "bookshelf.local"

    // ---- 页面分组与设置项 ----

    /** 外观与主题页的分组，声明位于 [AppearanceSpecs]。 */
    val appearanceGroups: List<SettingsGroupSpec> = AppearanceSpecs.groups

    /** 快捷托盘页的分组，声明位于 [TraySpecs]。 */
    val trayGroups: List<SettingsGroupSpec> = TraySpecs.groups

    /** 锁屏与安全页的分组，声明位于 [LockSpecs]。 */
    val lockGroups: List<SettingsGroupSpec> = LockSpecs.groups

    /** 系统设置页的分组，声明位于 [SystemSpecs]。 */
    val systemGroups: List<SettingsGroupSpec> = SystemSpecs.groups

    /** 阅读与数据页的分组，声明位于 [BookshelfSpecs]。 */
    val bookshelfGroups: List<SettingsGroupSpec> = BookshelfSpecs.groups

    /**
     * 全部设置项，按页面顺序排列。
     *
     * 使用取值函数组合各页面声明，避免对象初始化顺序依赖。
     */
    val specs: List<SettingSpec>
        get() =
            AppearanceSpecs.specs + TraySpecs.specs + LockSpecs.specs +
                SystemSpecs.specs + BookshelfSpecs.specs

    /** 按分组读取设置项，保持声明顺序。 */
    fun specsOf(group: String): List<SettingSpec> = specs.filter { it.group == group }

    /** 按标识查找设置项。 */
    fun specOf(id: String): SettingSpec? = specs.firstOrNull { it.id == id }

    /** 全部分组的文本，供测试与文档核对。 */
    val groups: List<SettingsGroupSpec> =
        appearanceGroups + trayGroups + lockGroups + systemGroups + bookshelfGroups
}

/** 设置分组声明。 */
internal data class SettingsGroupSpec(
    /** 分组标识，页面按标识取用。 */
    val id: String,
    /** 分组标题。 */
    val title: String,
    /** 分组说明，为空时不显示。 */
    val subtitle: String? = null,
    /** 分组之后的说明段落，按顺序追加。 */
    val notes: List<String> = emptyList(),
)

/** 选项声明：值为写入配置的取值，显示文本用于行内摘要与候选列表。 */
internal data class SettingOption(
    val value: Int,
    val label: String,
)

/** 设置项声明。 */
internal data class SettingSpec(
    /** 设置项标识，全表唯一。 */
    val id: String,
    /** 所属分组标识。 */
    val group: String,
    /** 行标题。 */
    val title: String,
    /** 行说明，为空时不显示。 */
    val hint: String? = null,
    /** 控件类型与读写方式。 */
    val control: SettingControl,
    /** 写入后页面需要执行的动作。 */
    val effect: SettingEffect = SettingEffect.PARTIAL,
    /** 显示条件，返回 false 时该行不生成。 */
    val visible: (LauncherConfig) -> Boolean = { true },
    /** 整体写入函数，供按钮类设置项使用；为空时按控件自身的写入方式。 */
    val write: ((LauncherConfig) -> LauncherConfig)? = null,
    /** 写入完成后的附加动作，例如同步刷新调度器间隔。 */
    val afterWrite: (LauncherConfig) -> Unit = {},
)

/** 控件类型声明。 */
internal sealed interface SettingControl {
    /** 开关行。 */
    data class Toggle(
        val read: (LauncherConfig) -> Boolean,
        val write: (LauncherConfig, Boolean) -> LauncherConfig,
    ) : SettingControl

    /** 选项行：点击后弹出候选列表。 */
    data class Choice(
        val options: List<SettingOption>,
        val read: (LauncherConfig) -> Int,
        val write: (LauncherConfig, Int) -> LauncherConfig,
    ) : SettingControl

    /** 步进行：左右加减档位。 */
    data class Stepper(
        val options: List<SettingOption>,
        val read: (LauncherConfig) -> Int,
        val write: (LauncherConfig, Int) -> LauncherConfig,
    ) : SettingControl

    /** 分段滑条行。 */
    data class Slider(
        val values: List<Float>,
        val read: (LauncherConfig) -> Float,
        val write: (LauncherConfig, Float) -> LauncherConfig,
        val format: (Float) -> String,
    ) : SettingControl

    /** 只读信息行。 */
    data class Info(
        val read: (LauncherConfig) -> CharSequence,
        /** 需要上下文时优先于 read 的取值函数，用于读取图片目录、缓存占用等外部状态。 */
        val readWithContext: ((Context, LauncherConfig) -> CharSequence)? = null,
    ) : SettingControl

    /** 跳转动作行，点击行为由页面提供。 */
    data class Action(
        val value: ((LauncherConfig) -> CharSequence?)? = null,
        /** 需要上下文时优先于 value 的取值函数，用于展示随外部状态变化的行摘要。 */
        val valueWithContext: ((Context, LauncherConfig) -> CharSequence?)? = null,
    ) : SettingControl

    /** 按钮行，写入行为由设置的 write 提供。 */
    data class Button(
        val filled: Boolean = false,
    ) : SettingControl

    /** 自定义行，由页面按 key 提供构建函数。 */
    data class Custom(
        val key: String,
    ) : SettingControl
}

/** 设置写入后的生效方式。 */
internal enum class SettingEffect {
    /** 不刷新，交由页面动作自行处理。 */
    NONE,

    /** 请求局部刷新。 */
    PARTIAL,

    /** 请求整屏刷新。 */
    FULL,

    /** 重建设计令牌并整屏刷新。 */
    APPEARANCE,

    /** 重建设计令牌、整屏刷新并重建页面，用于字号与字体变化。 */
    APPEARANCE_REBUILD,

    /** 请求局部刷新并重建页面，用于行数与页结构变化。 */
    REBUILD,

    /** 刷新内容缓存并请求局部重绘，用于微信读书刷新间隔变化。 */
    CONTENT_REFRESH,
}

/** 刷新调度器桥接，避免声明表直接依赖渲染模块的类型路径。 */
internal object RefreshSchedulerBridge {
    /** 同步自动整屏刷新间隔，参数 minutes 为分钟数，0 表示关闭。 */
    fun updateInterval(minutes: Int) {
        runCatching {
            com.shuiyu.octoberrain.core.render.RefreshScheduler
                .updateInterval(minutes)
        }
    }

    /** 内容数据变化后请求一次局部重绘，由渲染层合并同一帧内的多次请求。 */
    fun notifyContentChanged() {
        runCatching {
            com.shuiyu.octoberrain.core.render.RefreshScheduler
                .notifyContentChanged()
        }
    }
}
