package com.shuiyu.octoberrain.feature.widgets.kernel

/** Shared canvas and hit geometry, in the existing widget's logical coordinates. */
internal data class LandscapeRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(
        x: Float,
        y: Float,
    ): Boolean = x >= left && x < right && y >= top && y < bottom
}

internal data class LandscapeCardGeometry(
    val width: Float,
    val height: Float,
    val unit: Float,
    val title: LandscapeRect,
    val bookmark: LandscapeRect,
    val refresh: LandscapeRect,
    val refreshInk: LandscapeRect,
    val meta: LandscapeRect,
    val body: LandscapeRect,
    val footer: List<LandscapeRect>,
    val chrome: LandscapeChromeGeometry,
) {
    companion object {
        fun compute(
            width: Int,
            height: Int,
            period: Boolean = false,
            minimumTouch: Float = 44f,
            referenceWidth: Int = width,
        ): LandscapeCardGeometry {
            val w = width.coerceAtLeast(1).toFloat()
            val h = height.coerceAtLeast(1).toFloat()
            // Reference is the paper itself, rather than the surrounding 768px desktop.
            val u = (referenceWidth / 464f).coerceIn(.65f, 1.4f)
            val titleWidth = maxOf((if (period) 68f else 50f) * u, minimumTouch)
            val left = maxOf((if (period) 76f else 62f) * u, titleWidth + 8f * u)
            val right = (w - (if (period) 24f else 14f) * u).coerceAtLeast(left + 1f)
            val top = (if (period) 18f else 6f) * u
            val metaBottom = top + 38f * u
            val headerBottom = top + maxOf(44f * u, minimumTouch)
            val chrome = LandscapeChromeGeometry.compute(titleWidth, top, h, u, minimumTouch, period)
            val refresh = chrome.refreshTouch
            val refreshInk = chrome.refreshIcon
            val footerBottom = (h - 8f * u).coerceAtLeast(metaBottom + 54f * u)
            val footerTop = footerBottom - maxOf(44f * u, minimumTouch)
            val gap = 7f * u
            val buttonWidth = ((right - left - gap) / 2f).coerceAtLeast(1f)
            val footer =
                (0..1).map { index ->
                    val x = left + index * (buttonWidth + gap)
                    LandscapeRect(x, footerTop, x + buttonWidth, footerBottom)
                }
            return LandscapeCardGeometry(
                w,
                h,
                u,
                chrome.switchTouch,
                if (period) {
                    LandscapeRect(23f * u, top + 7f * u, 25f * u, minOf(top + 237f * u, h - 12f * u))
                } else {
                    LandscapeRect(7f * u, top + 8f * u, 9f * u, top + 88f * u)
                },
                refresh,
                refreshInk,
                LandscapeRect(left, top, right, metaBottom),
                LandscapeRect(left, maxOf(metaBottom, headerBottom) + 9f * u, right, footerTop - 19f * u),
                footer,
                chrome,
            )
        }
    }
}

/** Left controls never overlap; short slots compress typography instead of moving the body. */
internal data class LandscapeChromeGeometry(
    val switchTouch: LandscapeRect,
    val switchIcon: LandscapeRect,
    val titleInk: LandscapeRect,
    val refreshTouch: LandscapeRect,
    val refreshIcon: LandscapeRect,
    val updatedInk: LandscapeRect,
) {
    companion object {
        fun compute(
            width: Float,
            top: Float,
            height: Float,
            u: Float,
            minimumTouch: Float,
            period: Boolean,
        ): LandscapeChromeGeometry {
            val touch = maxOf(44f * u, minimumTouch)
            val gap = 3f * u
            val available = (height - top - 8f * u - 2f * touch - 3f * gap).coerceAtLeast(1f)
            val factor = minOf(1f, available / (72f * u + 112f * u))
            val titleHeight = 72f * u * factor
            val center = (if (period) 50f else 35.5f) * u
            val switchSize = ((if (period) 26f else 27f) * u * .62f).coerceIn(11.78f, 20f)
            val switch =
                LandscapeRect(
                    center - switchSize / 2f,
                    top + (touch - switchSize) / 2f,
                    center + switchSize / 2f,
                    top + (touch + switchSize) / 2f,
                )
            val titleTop = top + touch + gap
            val refreshTop = titleTop + titleHeight + gap
            val refresh = LandscapeRect(0f, refreshTop, width, refreshTop + touch)
            val iconSize = 20f * u
            val icon =
                LandscapeRect(
                    center - iconSize / 2f,
                    refresh.centerY - iconSize / 2f,
                    center + iconSize / 2f,
                    refresh.centerY + iconSize / 2f,
                )
            return LandscapeChromeGeometry(
                LandscapeRect(0f, top, width, titleTop + titleHeight),
                switch,
                LandscapeRect(2f * center - width, titleTop, width, titleTop + titleHeight),
                refresh,
                icon,
                LandscapeRect(2f * center - width, refresh.bottom + gap, width, height - 8f * u),
            )
        }
    }
}

/** Refresh icon and the entire updated label share one continuous target; local todos have none. */
internal fun landscapeRefreshZone(
    form: String,
    geometry: LandscapeCardGeometry,
): LandscapeRect? =
    if (form ==
        "todo"
    ) {
        null
    } else {
        LandscapeRect(
            geometry.refresh.left,
            geometry.refresh.top,
            geometry.refresh.right,
            geometry.chrome.updatedInk.bottom,
        )
    }

internal fun landscapeUpdatedLabel(label: String?): String {
    val time = Regex("(?<![0-9])([01]?[0-9]|2[0-3]):([0-5][0-9])(?![0-9])").find(label.orEmpty())
    return if (time == null) "更新于--:--" else "更新于${time.groupValues[1].padStart(2, '0')}:${time.groupValues[2]}"
}

internal fun landscapeUpdatedRows(label: String?): List<String> {
    val time = landscapeUpdatedLabel(label).removePrefix("更新于").split(':')
    return listOf("更", "新", "于", time[0], ":", time[1])
}
