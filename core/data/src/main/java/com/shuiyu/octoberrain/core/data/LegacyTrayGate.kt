package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.TrayConfig
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Preserve the old master switch without retaining the removed menu switch in current config. */
internal fun preserveLegacyTrayGate(
    root: JsonElement,
    tray: TrayConfig,
): TrayConfig {
    val section = (root as? JsonObject)?.get("tray") as? JsonObject
    val enabled = section?.get("enabled") as? JsonPrimitive
    val wasDisabled = enabled != null && !enabled.isString && enabled.booleanOrNull == false
    return if (wasDisabled && tray.overlayEnabled) tray.copy(overlayEnabled = false) else tray
}
