package com.shuiyu.octoberrain.feature.tray

/** Keep the preferred clock slot when it fits, then consume the gap before scaling only the clock. */
internal data class StatusTimeFit(
    val minimumWidth: Int,
    val scaleX: Float,
)

internal fun statusTimeFit(
    naturalWidth: Float,
    preferredWidth: Int,
    availableWidth: Int,
): StatusTimeFit {
    val available = availableWidth.coerceAtLeast(0)
    return StatusTimeFit(
        minimumWidth = preferredWidth.coerceIn(0, available),
        scaleX = if (naturalWidth > available && naturalWidth > 0f) available / naturalWidth else 1f,
    )
}
