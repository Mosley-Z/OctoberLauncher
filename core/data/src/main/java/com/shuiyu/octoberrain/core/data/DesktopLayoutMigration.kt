package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.DesktopKind
import com.shuiyu.octoberrain.core.common.LauncherConfig
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** v14独立首页列数与列表容量；退役间距不再影响任何布局。 */
internal object DesktopLayoutMigration {
    fun presentFields(root: JsonElement): Set<String> =
        ((root as? JsonObject)?.get("desktop") as? JsonObject)?.keys.orEmpty()

    fun migrate(
        config: LauncherConfig,
        fields: Set<String>,
    ): LauncherConfig {
        if (config.version >= 14) return config
        val old = config.desktop
        val desktop =
            old.copy(
                firstPageColumns = if ("firstPageColumns" in fields) old.firstPageColumns else old.columns,
                listRows =
                    if ("listRows" in fields) {
                        old.listRows
                    } else if (old.kind == DesktopKind.LIST) {
                        old.rows
                    } else {
                        0
                    },
                listFirstRows =
                    if ("listFirstRows" in fields) {
                        old.listFirstRows
                    } else if (old.kind == DesktopKind.LIST) {
                        old.firstPageRows
                    } else {
                        0
                    },
            )
        return config.copy(
            version = 14,
            desktop = desktop.copy(columnSpacingDp = 0, rowSpacingDp = 0, iconOnly = false),
            appearance = config.appearance.copy(showLabels = config.appearance.showLabels && !old.iconOnly),
        )
    }
}
