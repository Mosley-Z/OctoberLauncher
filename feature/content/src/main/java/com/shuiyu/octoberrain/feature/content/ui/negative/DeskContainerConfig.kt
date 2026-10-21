package com.shuiyu.octoberrain.feature.content.ui.negative

import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.NegativePageConfig
import com.shuiyu.octoberrain.core.common.WereadDeskConfig
import com.shuiyu.octoberrain.core.common.WereadMenuConfig

/** 只追踪影响墨台装配和外观的配置，普通同步与无关设置变化不重建分区。 */
internal data class DeskContainerConfig(
    val page: NegativePageConfig,
    val desk: WereadDeskConfig,
    val menu: WereadMenuConfig,
) {
    companion object {
        fun from(config: LauncherConfig): DeskContainerConfig =
            DeskContainerConfig(
                config.desktop.negativePage,
                config.integrations.wereadDesk,
                config.integrations.wereadMenu,
            )
    }
}
