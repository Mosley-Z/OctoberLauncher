package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.WidgetSwitchGesture

/**
 * 退役组件与旧交互入口的统一改写。
 *
 * 迁移只处理已删除的十雨内置目标与已停用的组件轮换数据，普通已安装应用不受影响；
 * 正常加载与备份导入共用同一入口，重复执行结果一致。
 */
internal object RetiredIntegrations {
    private const val ENTRY = "special:koreader"
    private const val WIDGET = "koreader_month"

    /** 已删除的使用统计组件标识，旧当前组件迁移为不启用组件。 */
    private const val RETIRED_USAGE_WIDGET = "usage"

    /**
     * 清理退役目标、已删除组件与停用的轮换字段。
     *
     * 当前组件为使用统计组件的配置改写为不启用，其他有效标识保持不变；
     * 组件轮换列表清空、切换手势改为关闭，两者都不再被桌面读取。
     *
     * @param config 需要清理的配置
     * @return 清理后的配置，重复调用不再产生变化
     */
    fun sanitize(config: LauncherConfig): LauncherConfig {
        val old = config.desktop
        val retiredWidgetId = if (old.widgetId == RETIRED_USAGE_WIDGET) "" else old.widgetId
        return config.copy(
            desktop =
                old.copy(
                    order = old.order.filterNot { it == ENTRY },
                    hiddenSpecials = old.hiddenSpecials - ENTRY,
                    favoriteEntryKeys = old.favoriteEntryKeys.filterNot { it == ENTRY },
                    widgetId = if (retiredWidgetId == WIDGET) "clock" else retiredWidgetId,
                    widgetCycleIds = emptyList(),
                    widgetSwitchGesture = WidgetSwitchGesture.OFF,
                ),
            appearance =
                config.appearance.copy(
                    wallpaperWhiteOverlay = false,
                    iconOverrides = config.appearance.iconOverrides.filterKeys { it != ENTRY },
                    transparentIconTargets = config.appearance.transparentIconTargets - ENTRY,
                    wallpaperLandscapeRotation =
                        config.appearance.wallpaperLandscapeRotation
                            .takeIf { it in listOf(0, 90, 180, 270) } ?: 0,
                ),
        )
    }
}
