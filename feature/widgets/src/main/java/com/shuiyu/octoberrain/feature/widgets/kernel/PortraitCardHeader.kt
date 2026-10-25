package com.shuiyu.octoberrain.feature.widgets.kernel

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** Mirrors the shipped header's 19px title, switch, spacing and height-relative top inset. */
internal fun portraitRefreshBounds(
    width: Int,
    height: Int,
    title: String,
): RectF {
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 19f
            typeface = Typeface.DEFAULT
        }
    val switch = (19f * .62f).coerceIn(10f, 20f)
    val refreshLeft = width * .05f + switch + paint.measureText(title) + 8f
    return RectF(refreshLeft, 0f, width.toFloat(), height * .055f + 23f)
}
