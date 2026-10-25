package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.TrayConfig

// 设置项构造辅助。
//
// 各页面声明文件通过这些函数把分区读写折叠为统一的控件声明，
// 避免每条声明重复书写外观、托盘或整合配置的拷贝逻辑。

/** 系统设置页的跳转动作声明。 */
internal fun systemAction(
    id: String,
    group: String,
    title: String,
    hint: String,
): SettingSpec =
    SettingSpec(
        id = id,
        group = group,
        title = title,
        hint = hint,
        control = SettingControl.Action(),
        effect = SettingEffect.NONE,
    )

/** 外观开关控件。 */
internal fun toggleAppearance(
    read: (AppearanceConfig) -> Boolean,
    write: (AppearanceConfig, Boolean) -> AppearanceConfig,
): SettingControl =
    SettingControl.Toggle(
        read = { read(it.appearance) },
        write = { config, value -> config.copy(appearance = write(config.appearance, value)) },
    )

/** 外观选项控件，选项下标直接传给 write。 */
internal fun choiceAppearance(
    options: List<String>,
    read: (AppearanceConfig) -> Int,
    write: (AppearanceConfig, Int) -> AppearanceConfig,
): SettingControl =
    choiceControl(options, { read(it.appearance) }) { config, index ->
        config.copy(appearance = write(config.appearance, index))
    }

/** 外观滑条控件。 */
internal fun sliderAppearance(
    values: List<Float>,
    read: (AppearanceConfig) -> Float,
    write: (AppearanceConfig, Float) -> AppearanceConfig,
    format: (Float) -> String,
): SettingControl =
    SettingControl.Slider(
        values = values,
        read = { read(it.appearance) },
        write = { config, value -> config.copy(appearance = write(config.appearance, value)) },
        format = format,
    )

/** 托盘开关控件。 */
internal fun toggleTray(
    read: (TrayConfig) -> Boolean,
    write: (TrayConfig, Boolean) -> TrayConfig,
): SettingControl =
    SettingControl.Toggle(
        read = { read(it.tray) },
        write = { config, value -> config.copy(tray = write(config.tray, value)) },
    )

/** 托盘选项控件，选项下标直接传给 write。 */
internal fun choiceTray(
    options: List<String>,
    read: (TrayConfig) -> Int,
    write: (TrayConfig, Int) -> TrayConfig,
): SettingControl =
    choiceControl(options, { read(it.tray) }) { config, index ->
        config.copy(tray = write(config.tray, index))
    }

/** 托盘滑条控件。 */
internal fun sliderTray(
    values: List<Float>,
    read: (TrayConfig) -> Float,
    write: (TrayConfig, Float) -> TrayConfig,
    format: (Float) -> String,
): SettingControl =
    SettingControl.Slider(
        values = values,
        read = { read(it.tray) },
        write = { config, value -> config.copy(tray = write(config.tray, value)) },
        format = format,
    )

/** 整合配置开关控件。 */
internal fun toggleIntegrations(
    read: (IntegrationsConfig) -> Boolean,
    write: (IntegrationsConfig, Boolean) -> IntegrationsConfig,
): SettingControl =
    SettingControl.Toggle(
        read = { read(it.integrations) },
        write = { config, value -> config.copy(integrations = write(config.integrations, value)) },
    )

/** 整合配置选项控件，选项下标直接传给 write。 */
internal fun choiceIntegrations(
    options: List<String>,
    read: (IntegrationsConfig) -> Int,
    write: (IntegrationsConfig, Int) -> IntegrationsConfig,
): SettingControl =
    choiceControl(options, { read(it.integrations) }) { config, index ->
        config.copy(integrations = write(config.integrations, index))
    }

/** 整合配置步进控件。 */
internal fun stepperIntegrations(
    options: List<String>,
    read: (IntegrationsConfig) -> Int,
    write: (IntegrationsConfig, Int) -> IntegrationsConfig,
): SettingControl =
    SettingControl.Stepper(
        options = optionList(options),
        read = { read(it.integrations) },
        write = { config, index -> config.copy(integrations = write(config.integrations, index)) },
    )

/** 通用选项控件，write 接收选项下标。 */
internal fun choiceControl(
    options: List<String>,
    read: (LauncherConfig) -> Int,
    write: (LauncherConfig, Int) -> LauncherConfig,
): SettingControl =
    SettingControl.Choice(
        options = optionList(options),
        read = { read(it).coerceIn(0, (options.size - 1).coerceAtLeast(0)) },
        write = write,
    )

/** 把显示文本转换为等值选项，值等于下标。 */
internal fun optionList(labels: List<String>): List<SettingOption> =
    labels.mapIndexed { index, label -> SettingOption(index, label) }

/** 应用名称字体显示名。 */
internal fun labelFontLabel(config: LauncherConfig): CharSequence {
    val path = config.appearance.labelFontPath ?: return "系统默认"
    val fonts =
        runCatching {
            com.shuiyu.octoberrain.core.common.AppServices.fonts
                .fonts()
        }.getOrDefault(emptyList())
    return fonts.firstOrNull { it.path == path }?.label
        ?: runCatching { java.io.File(path).name }.getOrDefault("已导入字体")
}
