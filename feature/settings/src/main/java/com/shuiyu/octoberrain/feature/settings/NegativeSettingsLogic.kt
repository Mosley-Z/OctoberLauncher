package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.DEFAULT_NEGATIVE_MODULES
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.NegativeModule

/**
 * 负一屏设置页的纯逻辑部分。
 *
 * 负责计算模块展示顺序、维护启用列表与调整上下位置，全部以纯函数实现，
 * 不触碰视图与配置存储，便于桌面负一屏与设置页共用同一套排序规则。
 */
internal object NegativeSettingsLogic {
    /** 模块标识到显示名的回退表，注册表缺少对应模块时使用。 */
    private val LABELS: Map<String, String> =
        linkedMapOf(
            "bookshelf" to "书架",
            "negative_files" to "最近图片",
            "bill" to "墨单",
            "rank" to "阅读资产",
            "fp" to "阅读指纹",
        )

    /** 模块标识到行说明的回退表。 */
    private val HINTS: Map<String, String> =
        linkedMapOf(
            "bookshelf" to "书架、阅读进度与最近阅读",
            "negative_files" to "近期拍摄或保存的图片",
            "bill" to "阅读账单与支出去向",
            "rank" to "文字、有声与视频分项时长",
            "fp" to "累计时长、阅读天数与热力格",
        )

    /**
     * 计算设置页展示的模块顺序。
     *
     * 参数 config 为当前配置，registered 为已注册模块。
     * 配置中已启用的模块保持原有先后顺序排在前面，其余已知模块按 orderHint 追加在后，
     * 因此关闭的模块仍会出现在列表中，只是处于关闭状态。
     */
    fun moduleOrder(
        config: LauncherConfig,
        registered: List<NegativeModule>,
    ): List<String> {
        val configured = config.desktop.negativePage.modules
        val registeredIds = registered.map { it.id }.filter { it.isNotBlank() }
        val known = (DEFAULT_NEGATIVE_MODULES + registeredIds).distinct()
        val leading = configured.filter { it in known }
        val trailing = known.filterNot { it in leading }
        return (leading + trailing).ifEmpty { DEFAULT_NEGATIVE_MODULES }
    }

    /** 读取指定模块是否处于启用状态，即模块标识是否在配置列表中。 */
    fun enabled(
        config: LauncherConfig,
        moduleId: String,
    ): Boolean =
        config.desktop.negativePage.modules
            .contains(moduleId)

    /**
     * 切换指定模块的启用状态。
     *
     * 参数 config 为当前配置，moduleId 为模块标识。
     * 开启时追加到列表末尾，关闭时移除该标识，其余模块的顺序保持不变。
     */
    fun toggle(
        config: LauncherConfig,
        moduleId: String,
    ): LauncherConfig {
        val page = config.desktop.negativePage
        val next = NegativeSpecs.applyNegativeModule(page.modules, moduleId, !enabled(config, moduleId))
        return config.copy(
            desktop = config.desktop.copy(negativePage = page.copy(modules = next)),
        )
    }

    /**
     * 把指定模块上移或下移一格。
     *
     * 参数 config 为当前配置，moduleId 为待移动的模块，delta 为移动方向，负值上移、正值下移。
     * 移动在完整展示顺序上进行，写入配置时只保留已启用模块，并把顺序标记为已初始化，
     * 使负一屏按当前排列渲染，不再追加默认模块。
     */
    fun move(
        config: LauncherConfig,
        moduleId: String,
        delta: Int,
        registered: List<NegativeModule>,
    ): LauncherConfig {
        val order = moduleOrder(config, registered)
        val index = order.indexOf(moduleId)
        val target = if (index < 0) index else (index + delta).coerceIn(0, order.lastIndex)
        if (target < 0 || target == index) return config
        val moved = order.toMutableList()
        moved.add(target, moved.removeAt(index))
        val enabledIds =
            config.desktop.negativePage.modules
                .toSet()
        val page = config.desktop.negativePage
        return config.copy(
            desktop =
                config.desktop.copy(
                    negativePage =
                        page.copy(
                            modules = moved.filter { it in enabledIds },
                            cardOrderInitialized = true,
                        ),
                ),
        )
    }

    /** 读取模块显示名，注册表提供时优先使用注册值。 */
    fun label(
        moduleId: String,
        registered: List<NegativeModule>,
    ): String {
        val registeredLabel = registered.firstOrNull { it.id == moduleId }?.label
        if (!registeredLabel.isNullOrBlank()) return registeredLabel
        return LABELS[moduleId] ?: moduleId
    }

    /** 读取模块行的说明文本。 */
    fun hint(moduleId: String): String = HINTS[moduleId] ?: "负一屏卡片"
}
