package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/** 以实际文字宽度和字体高度适配钟面中的一行文字，保留调用方颜色与字体。 */
internal fun drawClockText(
    canvas: Canvas,
    value: String,
    area: RectF,
    paint: Paint,
    desired: Float,
    align: Paint.Align = Paint.Align.CENTER,
) {
    if (area.width() <= 0f || area.height() <= 0f) return
    paint.textSize = min(desired, area.height() * .82f).coerceAtLeast(1f)
    val width = paint.measureText(value)
    if (width > area.width()) paint.textSize *= area.width() / width
    paint.textAlign = align
    val metrics = paint.fontMetrics
    val x = if (align == Paint.Align.LEFT) area.left else area.centerX()
    canvas.drawText(value, x, area.centerY() - (metrics.ascent + metrics.descent) / 2, paint)
}

/** 汉字逐字从上到下绘制，字形保持正向；高度不足时由调用方统一适配时、分字号。 */
internal fun drawClockColumn(
    canvas: Canvas,
    value: String,
    area: RectF,
    paint: Paint,
    size: Float,
) {
    if (value.isEmpty() || area.height() <= 0f) return
    val cell = min(area.height() / value.length, size * 1.35f)
    val top = area.centerY() - cell * value.length / 2
    value.forEachIndexed { index, character ->
        drawClockText(
            canvas,
            character.toString(),
            RectF(area.left, top + index * cell, area.right, top + (index + 1) * cell),
            paint,
            size,
        )
    }
}
