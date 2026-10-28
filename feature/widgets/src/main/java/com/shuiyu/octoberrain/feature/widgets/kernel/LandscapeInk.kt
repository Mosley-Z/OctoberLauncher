package com.shuiyu.octoberrain.feature.widgets.kernel

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.shuiyu.octoberrain.core.widget.WidgetNoteText

/** Text and rules used by the dedicated landscape layouts. No bitmap intermediate. */
internal class LandscapeInk(
    val canvas: Canvas,
    val unit: Float,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun text(
        value: String,
        x: Float,
        top: Float,
        size: Float,
        width: Float = Float.MAX_VALUE,
        face: Typeface = Typeface.DEFAULT,
        color: Int = Color.BLACK,
    ) {
        configure(size, face, color)
        val count = paint.breakText(value, true, width.coerceAtLeast(0f), null)
        val shown = if (count < value.length && count > 0) value.take(count - 1) + "…" else value.take(count)
        canvas.drawText(shown, x, top - paint.fontMetrics.ascent, paint)
    }

    fun centered(
        value: String,
        box: LandscapeRect,
        size: Float,
        color: Int = Color.BLACK,
    ) {
        configure(size, Typeface.DEFAULT, color)
        val y = box.centerY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        canvas.drawText(value, box.centerX - paint.measureText(value) / 2f, y, paint)
    }

    fun rule(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int = Color.LTGRAY,
        stroke: Float = 1f,
    ) {
        paint.color = color
        paint.strokeWidth = stroke * unit
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    fun box(
        rect: LandscapeRect,
        fill: Int? = null,
        color: Int = Color.DKGRAY,
    ) {
        paint.color = fill ?: color
        paint.style = if (fill == null) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = unit
        canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)
        paint.style = Paint.Style.FILL
    }

    fun lines(
        value: String,
        width: Float,
        size: Float,
        face: Typeface,
    ): List<String> {
        configure(size, face, Color.BLACK)
        return value.split('\n').flatMap { paragraph ->
            val result = mutableListOf<String>()
            var rest = paragraph
            while (rest.isNotEmpty()) {
                val count =
                    WidgetNoteText.fitLength(
                        rest,
                        paint.breakText(rest, true, width.coerceAtLeast(1f), null),
                        wrap = true,
                    )
                result += rest.take(count)
                rest = rest.drop(count)
            }
            result.ifEmpty { listOf("") }
        }
    }

    private fun configure(
        size: Float,
        face: Typeface,
        color: Int,
    ) {
        paint.style = Paint.Style.FILL
        paint.typeface = face
        paint.textSize = maxOf(18f, size * unit)
        paint.color = color
        paint.textAlign = Paint.Align.LEFT
    }
}

internal fun LandscapeRect.androidRect(): RectF = RectF(left, top, right, bottom)
