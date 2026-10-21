package com.shuiyu.octoberrain.core.common

/** 墨台五个模块共用的显示、排序及开关规则。 */
object DeskModulePolicy {
    val moduleIds: List<String> = DEFAULT_NEGATIVE_MODULES + "fp"

    /** 已配置模块保持原顺序；关闭全部时不补回默认模块。 */
    fun visibleOrder(config: LauncherConfig): List<String> =
        configuredOrder(config).filter { id ->
            id !in WereadDeskConfig.ORDER_OPTIONS || config.integrations.wereadDesk.isVisible(id)
        }

    /** 设置页始终保留关闭模块的入口。 */
    fun settingsOrder(config: LauncherConfig): List<String> =
        (config.desktop.negativePage.moduleOrder + configuredOrder(config) + moduleIds)
            .filter { it in moduleIds }
            .distinct()

    fun isEnabled(
        config: LauncherConfig,
        id: String,
    ): Boolean = id in visibleOrder(config)

    fun toggle(
        config: LauncherConfig,
        id: String,
    ): LauncherConfig {
        if (id !in moduleIds) return config
        val enabled = isEnabled(config, id)
        val modules = configuredOrder(config)
        val next =
            when {
                id in WereadDeskConfig.ORDER_OPTIONS -> (modules + id).distinct()
                enabled -> modules - id
                else -> modules + id
            }
        val bit = bitOf(id)
        val mask = config.integrations.wereadDesk.effectiveOnMask
        val order = settingsOrder(config)
        val active = next.toSet()
        return applyOrder(
            config,
            order.filter { it in active },
            if (enabled) mask and bit.inv() else mask or bit,
            order,
        )
    }

    fun move(
        config: LauncherConfig,
        id: String,
        delta: Int,
    ): LauncherConfig {
        val order = settingsOrder(config).toMutableList()
        val index = order.indexOf(id)
        val target = index + delta
        if (index < 0 || target !in order.indices) return config
        order.removeAt(index)
        order.add(target, id)
        val configured = configuredOrder(config).toSet()
        return applyOrder(
            config,
            order.filter { it in configured },
            config.integrations.wereadDesk.effectiveOnMask,
            order,
        )
    }

    private fun configuredOrder(config: LauncherConfig): List<String> =
        config.desktop.negativePage.modules
            .filter { it in moduleIds }
            .distinct()

    private fun applyOrder(
        config: LauncherConfig,
        modules: List<String>,
        mask: Int,
        order: List<String>,
    ): LauncherConfig =
        config.copy(
            desktop =
                config.desktop.copy(
                    negativePage = config.desktop.negativePage.copy(modules = modules, moduleOrder = order),
                ),
            integrations =
                config.integrations.copy(
                    wereadDesk =
                        config.integrations.wereadDesk.copy(
                            order = modules.filter { it in WereadDeskConfig.ORDER_OPTIONS },
                            onMask = mask,
                        ),
                ),
        )

    private fun bitOf(id: String): Int =
        when (id) {
            "bill" -> WereadDeskConfig.BIT_BILL
            "rank" -> WereadDeskConfig.BIT_RANK
            "fp" -> WereadDeskConfig.BIT_FP
            else -> 0
        }
}
