package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Typeface
import com.shuiyu.octoberrain.core.design.DesignRuntime

/** 自定义钟面字体优先；未指定时使用设计稿对应的数字字体骨架。 */
internal fun clockTypeface(
    face: ClockFace,
    fontId: String,
): Typeface {
    if (fontId != "default") return DesignRuntime.typeface(fontId)
    return when (face) {
        ClockFace.BOOK_PAGE, ClockFace.STUDY_DIAL, ClockFace.TIME_DATE, ClockFace.CHINESE -> Typeface.SERIF
        ClockFace.DIVIDED -> Typeface.MONOSPACE
        else -> DesignRuntime.typeface(fontId)
    }
}

/** 仅默认A/B/F数字横向加宽，用户主动指定的字体按其原始字形显示。 */
internal fun clockNumberScale(
    face: ClockFace,
    fontId: String,
): Float =
    if (fontId == "default" && face in setOf(ClockFace.BOOK_PAGE, ClockFace.DIVIDED, ClockFace.TIME_DATE)) 1.18f else 1f
