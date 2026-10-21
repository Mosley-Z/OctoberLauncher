package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_NEGATIVE_CARDS

/**
 * 负一屏页的设置项声明。
 *
 * 桌面偏好页只保留一条入口项，点击后进入 [NegativeSettingsScreen]；
 * 卡片开关与上下顺序集中在该页维护，避免同一设置出现两处写入位置。
 * 模块列表的增删与顺序调整由 [NegativeSettingsLogic] 以纯函数完成。
 */
internal object NegativeSpecs {
    /** 书架卡片对应的模块标识，与内容层注册值一致。 */
    const val MODULE_BOOKSHELF: String = "bookshelf"

    /** 最近图片卡片对应的模块标识，与文件层注册值一致。 */
    const val MODULE_FILES: String = "negative_files"

    /** 桌面偏好页入口项标识，由页面提供跳转行为。 */
    const val SPEC_SETTINGS_ENTRY: String = "negative.settings"

    val groups: List<SettingsGroupSpec> =
        listOf(
            SettingsGroupSpec(
                id = GROUP_NEGATIVE_CARDS,
                title = "负一屏",
                subtitle = "卡片开关、上下顺序与墨单样式",
                notes = listOf("卡片开关与顺序在负一屏设置页统一维护，关闭的卡片不加载数据。"),
            ),
        )

    val specs: List<SettingSpec> =
        listOf(
            SettingSpec(
                id = SPEC_SETTINGS_ENTRY,
                group = GROUP_NEGATIVE_CARDS,
                title = "卡片开关与顺序",
                hint = "逐个开关卡片，并调整负一屏中的上下顺序",
                control = SettingControl.Action(value = { "打开" }),
                effect = SettingEffect.NONE,
            ),
        )

    /**
     * 按开关状态增删模块标识。
     *
     * 参数 modules 为当前列表，moduleId 为待处理的标识，enabled 为期望状态。
     * 开启时追加到末尾并去除重复项（保留首次出现的位置），关闭时移除全部同名标识；
     * 其余标识的顺序与内容保持不变。
     */
    internal fun applyNegativeModule(
        modules: List<String>,
        moduleId: String,
        enabled: Boolean,
    ): List<String> =
        if (enabled) {
            (modules + moduleId).distinct()
        } else {
            modules.filterNot { it == moduleId }
        }

    /** 读取配置中指定模块是否启用。 */
    internal fun moduleEnabled(
        config: LauncherConfig,
        moduleId: String,
    ): Boolean = NegativeSettingsLogic.enabled(config, moduleId)
}
