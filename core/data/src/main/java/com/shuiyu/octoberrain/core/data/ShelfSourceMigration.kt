package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.NegativePageConfig
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** 是否显式保存书源；区分旧配置缺失与用户已选默认来源。 */
internal fun hasShelfSource(element: JsonElement): Boolean {
    val root = element as? JsonObject
    val desktop = root?.get("desktop") as? JsonObject
    val negative = desktop?.get("negativePage") as? JsonObject
    return negative?.containsKey("shelfSource") == true
}

/** 从旧书架开关迁移展示来源，保留已保存的新来源和负一屏行数。 */
internal fun migrateShelfSource(
    config: LauncherConfig,
    present: Boolean,
): LauncherConfig {
    val integrations = config.integrations
    val source =
        when {
            present -> config.desktop.negativePage.effectiveShelfSource
            integrations.bookshelfLocalEnabled && !integrations.bookshelfWereadEnabled ->
                NegativePageConfig.SHELF_SOURCE_LOCAL
            else -> NegativePageConfig.SHELF_SOURCE_WEREAD
        }
    return config.copy(
        version = 11,
        desktop = config.desktop.copy(negativePage = config.desktop.negativePage.copy(shelfSource = source)),
        integrations =
            integrations.copy(
                bookshelfLocalEnabled = false,
                bookshelfWereadEnabled = true,
                bookshelfRows = 3,
            ),
    )
}
