package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 六款已确认钟面，统一负责时间与日期的比例，所有颜色和字体由调用方提供。 */
internal object ClockDesignFaces {
    fun draw(
        face: ClockFace,
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        source: Paint,
    ) {
        if (area.width() <= 0f || area.height() <= 0f) return
        val saved = canvas.save()
        canvas.clipRect(area)
        val paint =
            Paint(source).apply {
                style = Paint.Style.FILL
                textAlign = Paint.Align.LEFT
            }
        when {
            face == ClockFace.TIME_DATE -> drawTimeDate(canvas, area, time, paint)
            face == ClockFace.CHINESE -> drawChinese(canvas, area, time, paint)
            face == ClockFace.STUDY_DIAL && area.width() > area.height() * 2.4f -> {
                val split = area.left + area.width() * .56f
                drawDial(canvas, RectF(area.left, area.top, split, area.bottom), time, paint)
                dateLines(canvas, RectF(split, area.top, area.right, area.bottom), time, paint)
            }
            else -> drawWithDate(face, canvas, area, time, paint)
        }
        canvas.restoreToCount(saved)
    }

    /** 窄槽位将日期换为三行，保持字号，不把整段日期压成一条微小文字。 */
    private fun drawWithDate(
        face: ClockFace,
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
    ) {
        val dateSize = min(maxOf(12f, area.width() * .041f), area.height() * .15f).coerceAtLeast(1f)
        paint.textSize = dateSize
        val datePaint = Paint(paint).apply { textScaleX = 1f }
        datePaint.textSize = dateSize
        val multiline = datePaint.measureText(time.date.fullText) > area.width()
        val dateHeight = dateSize * if (multiline) 4.5f else 1.6f
        val dateArea = RectF(area.left, area.bottom - dateHeight, area.right, area.bottom)
        val clockArea = RectF(area.left, area.top, area.right, dateArea.top - dateSize * .4f)
        drawTime(face, canvas, clockArea, time, paint)
        if (multiline) {
            dateLines(
                canvas,
                dateArea,
                time,
                datePaint,
            )
        } else {
            drawClockText(canvas, time.date.fullText, dateArea, datePaint, dateSize)
        }
    }

    private fun dateLines(
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
    ) {
        val size = min(maxOf(12f, area.width() * .075f), area.height() / 4.5f)
        val row = min(area.height() / 3, size * 1.5f)
        val top = area.centerY() - row * 1.5f
        time.date.lines.forEachIndexed { index, line ->
            drawClockText(
                canvas,
                line,
                RectF(area.left, top + index * row, area.right, top + (index + 1) * row),
                paint,
                size,
            )
        }
    }

    private fun drawTime(
        face: ClockFace,
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
    ) {
        when (face) {
            ClockFace.BOOK_PAGE -> drawClockText(canvas, time.timeText, area, paint, area.width() * .235f)
            ClockFace.DIVIDED -> drawDivided(canvas, area, time, paint)
            ClockFace.DOT_MATRIX -> drawDots(canvas, area, time, paint)
            ClockFace.STUDY_DIAL -> drawDial(canvas, area, time, paint)
            ClockFace.CHINESE -> drawChinese(canvas, area, time, paint)
            else -> Unit
        }
    }

    private fun drawDivided(
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
    ) {
        val middle = area.centerX()
        val gap = area.width() * .06f
        drawClockText(
            canvas,
            time.hourText,
            RectF(area.left, area.top, middle - gap, area.bottom),
            paint,
            area.width() * .235f,
        )
        drawClockText(
            canvas,
            time.minuteText,
            RectF(middle + gap, area.top, area.right, area.bottom),
            paint,
            area.width() * .235f,
        )
        val height = min(area.height() * .66f, area.width() * .23f)
        paint.strokeWidth = maxOf(1f, area.width() * .003f)
        canvas.drawLine(middle, area.centerY() - height / 2, middle, area.centerY() + height / 2, paint)
    }

    private fun drawDots(
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
    ) {
        val glyphs = time.timeText.map { DOTS.getValue(it) }
        val columns = glyphs.sumOf { it.first().length + 1 } - 1
        val step = min(area.width() * .84f / columns, area.height() * .74f / 5)
        var x = area.centerX() - (columns - 1) * step / 2
        val top = area.centerY() - step * 2
        glyphs.forEach { rows ->
            rows.forEachIndexed { row, cells ->
                cells.forEachIndexed { column, value ->
                    if (value == '1') canvas.drawCircle(x + column * step, top + row * step, step * .33f, paint)
                }
            }
            x += (rows.first().length + 1) * step
        }
    }

    private fun drawDial(
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
    ) {
        val radius = min(area.width(), area.height()) * .46f
        val centerX = area.centerX()
        val centerY = area.centerY()
        for (hour in 1..12) {
            val angle = Math.toRadians(hour * 30.0 - 90.0)
            val x = centerX + cos(angle).toFloat() * radius * .94f
            val y = centerY + sin(angle).toFloat() * radius * .94f
            val size = min(maxOf(12f, radius * .23f), radius * .31f)
            drawClockText(canvas, hour.toString(), RectF(x - size, y - size, x + size, y + size), paint, size)
        }
        hand(canvas, area, radius * .47f, (time.hour24 % 12 + time.minute / 60.0) * 30 - 90, radius * .055f, paint)
        hand(canvas, area, radius * .66f, time.minute * 6.0 - 90, radius * .026f, paint)
        canvas.drawCircle(centerX, centerY, maxOf(1f, radius * .045f), paint)
    }

    private fun hand(
        canvas: Canvas,
        area: RectF,
        length: Float,
        angle: Double,
        width: Float,
        paint: Paint,
    ) {
        val radians = Math.toRadians(angle)
        paint.strokeWidth = maxOf(1f, width)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(
            area.centerX(),
            area.centerY(),
            area.centerX() + cos(radians).toFloat() * length,
            area.centerY() + sin(radians).toFloat() * length,
            paint,
        )
    }

    private fun drawChinese(
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
    ) {
        val dateSize = min(maxOf(12f, area.width() * .0575f), area.height() * .15f).coerceAtLeast(1f)
        val datePaint = Paint(paint).apply { textSize = dateSize }
        val multiline = datePaint.measureText(time.date.fullText) > area.width()
        val dateHeight = dateSize * if (multiline) 4.5f else 1.6f
        val gap = min(area.height() * .08f, dateSize * 2f)
        val dateArea = RectF(area.left, area.centerY() - dateHeight / 2, area.right, area.centerY() + dateHeight / 2)
        val hourArea = RectF(area.left, area.top, area.right, dateArea.top - gap)
        val minuteArea = RectF(area.left, dateArea.bottom + gap, area.right, area.bottom)
        val lines = time.chineseLines
        val fitting = min(hourArea.height() / lines[0].length, minuteArea.height() / lines[1].length)
        val size = min(area.width() * .115f, fitting * .78f).coerceAtLeast(1f)
        drawClockColumn(canvas, lines[0], hourArea, paint, size)
        if (multiline) {
            dateLines(canvas, dateArea, time, datePaint)
        } else {
            drawClockText(canvas, time.date.fullText, dateArea, datePaint, dateSize)
        }
        drawClockColumn(canvas, lines[1], minuteArea, paint, size)
    }

    private fun drawTimeDate(
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
    ) {
        val height = min(area.height() * .76f, area.width() * .54f)
        val block =
            RectF(
                area.left + area.width() * .16f,
                area.centerY() - height / 2,
                area.right - area.width() * .12f,
                area.centerY() + height / 2,
            )
        val middle = block.left + block.width() * .48f
        val digitsRight = middle - block.width() * .06f
        val size = block.width() * .33f
        drawClockText(canvas, time.hourText, RectF(block.left, block.top, digitsRight, block.centerY()), paint, size)
        drawClockText(
            canvas,
            time.minuteText,
            RectF(block.left, block.centerY(), digitsRight, block.bottom),
            paint,
            size,
        )
        paint.strokeWidth = maxOf(1f, area.width() * .003f)
        canvas.drawLine(middle, block.top + height * .06f, middle, block.bottom - height * .06f, paint)
        val datePaint = Paint(paint).apply { textScaleX = 1f }
        time.date.lines.forEachIndexed { index, line ->
            val row = height / 3
            val dateArea =
                RectF(
                    middle + block.width() * .09f,
                    block.top + index * row,
                    block.right,
                    block.top + (index + 1) * row,
                )
            drawClockText(canvas, line, dateArea, datePaint, maxOf(12f, block.width() * .075f), Paint.Align.LEFT)
        }
    }

    private val DOTS =
        mapOf(
            '0' to listOf("111", "101", "101", "101", "111"),
            '1' to listOf("010", "110", "010", "010", "111"),
            '2' to listOf("111", "001", "111", "100", "111"),
            '3' to listOf("111", "001", "111", "001", "111"),
            '4' to listOf("101", "101", "111", "001", "001"),
            '5' to listOf("111", "100", "111", "001", "111"),
            '6' to listOf("111", "100", "111", "101", "111"),
            '7' to listOf("111", "001", "001", "001", "001"),
            '8' to listOf("111", "101", "111", "101", "111"),
            '9' to listOf("111", "101", "111", "001", "111"),
            ':' to listOf("0", "1", "0", "1", "0"),
        )
}
