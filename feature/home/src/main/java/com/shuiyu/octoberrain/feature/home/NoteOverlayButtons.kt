package com.shuiyu.octoberrain.feature.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** Shared outlined controls; drawing and hit testing use the same rectangles. */
internal class NoteOverlayButtons {
    private val boxes = List(3) { RectF() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        canvas: Canvas,
        area: RectF,
        unit: Float,
        size: Float,
        radius: Float = 6f * unit,
    ) {
        val gap = 7f * unit
        val width = ((area.width() - gap * 2f) / 3f).coerceAtLeast(1f)
        paint.color = Color.GRAY
        paint.strokeWidth = unit
        canvas.drawLine(area.left, area.top - 9f * unit, area.right, area.top - 9f * unit, paint)
        listOf("上一条", "换一条", "收起").forEachIndexed { index, label ->
            val left = area.left + index * (width + gap)
            boxes[index].set(left, area.top, left + width, area.bottom)
            val visual = RectF(boxes[index])
            val half = minOf(34f * unit, area.height()) / 2f
            visual.top = boxes[index].centerY() - half
            visual.bottom = boxes[index].centerY() + half
            paint.color = Color.DKGRAY
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(visual, radius, radius, paint)
            paint.style = Paint.Style.FILL
            paint.typeface = Typeface.DEFAULT
            paint.textSize = size
            val y = boxes[index].centerY() - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
            canvas.drawText(label, boxes[index].centerX() - paint.measureText(label) / 2f, y, paint)
        }
    }

    fun hit(
        x: Float,
        y: Float,
    ): Int = boxes.indexOfFirst { it.contains(x, y) }
}
