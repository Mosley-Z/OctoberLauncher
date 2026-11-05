package com.shuiyu.octoberrain.feature.widgets.kernel

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import com.inkread.weekread.kernel.CardState
import com.inkread.weekread.kernel.PeriodCalendar
import com.inkread.weekread.kernel.PeriodSnapshot
import com.inkread.weekread.kernel.render.CardLayout
import com.inkread.weekread.kernel.render.HostPeriodChart
import kotlin.math.roundToInt

/** Monthly landscape composition; the portrait renderer and weekly composition remain independent. */
internal object LandscapeMonthRenderer {
    fun draw(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        state: CardState,
        stats: PeriodSnapshot,
    ) {
        val u = g.unit
        val paint = ink.paint
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 14f * u
        val count = stats.dayCount.takeIf { it in 1..31 } ?: 31
        val offset = PeriodCalendar.firstWeekdayIndex(stats.baseTime)
        val rows = (offset + count + 6) / 7
        val comparison =
            stats.compare?.takeUnless { it.isNaN() }?.let {
                "较上月 ${if (it >= 0) "↑" else "↓"}${Math.round(kotlin.math.abs(it) * 100)}%"
            }
        val summary =
            listOfNotNull("阅读 ${stats.readDays}/${count}天", "日均 ${CardLayout.fmtTotal(stats.avgSec)}", comparison)
        val summaryLines = wrap(summary.joinToString(" · "), paint, g.body.width).toMutableList()
        state.achievementText()?.takeIf { it.isNotBlank() && stats.isCurrentPeriod() }?.let {
            summaryLines += wrap(it, paint, g.body.width)
        }
        val metrics = paint.fontMetrics
        val lineHeight = metrics.descent - metrics.ascent
        val step = lineHeight + 4f * u
        val total = CardLayout.fmtTotal(stats.totalSec)
        val totalSize = 27f * u
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = totalSize
        val fittedTotalSize = minOf(totalSize, totalSize * g.body.width / paint.measureText(total).coerceAtLeast(1f))
        paint.textSize = fittedTotalSize
        val totalHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent
        val headerHeight = lineHeight + 6f * u
        val layout =
            MonthLandscapeBudget.compute(
                g.body.top - 2f * u,
                g.height - 20f * u,
                g.body.width,
                rows,
                headerHeight,
                totalHeight,
                lineHeight + (summaryLines.size - 1) * step,
                u,
            )
        val save = ink.canvas.save()
        ink.canvas.clipRect(g.body.left, layout.top, g.body.right, layout.bottom)
        ink.rule(g.body.left, layout.top, g.body.right, layout.top, Color.BLACK, 2f)
        calendar(ink, g, state, stats, layout, headerHeight)
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = fittedTotalSize
        text(ink, total, g.body.left, layout.totalTop)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 14f * u
        summaryLines.forEachIndexed { index, line -> text(ink, line, g.body.left, layout.summaryTop + index * step) }
        ink.canvas.restoreToCount(save)
        state.setMonthScrollMax(0f)
    }

    private fun calendar(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        state: CardState,
        stats: PeriodSnapshot,
        layout: MonthLandscapeBudget,
        headerHeight: Float,
    ) {
        if (layout.cell <= 0f) return
        val paint = ink.paint
        val u = g.unit
        val count = stats.dayCount.takeIf { it in 1..31 } ?: 31
        val offset = PeriodCalendar.firstWeekdayIndex(stats.baseTime)
        val gap = layout.gap
        val gridWidth = 7 * layout.cell + 6 * gap
        val left = g.body.left + (g.body.width - gridWidth) / 2f
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 12f * u
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, name ->
            text(
                ink,
                name,
                left + index * (layout.cell + gap) + (layout.cell - paint.measureText(name)) / 2f,
                layout.chartTop,
            )
        }
        val today = if (stats.isCurrentPeriod()) stats.todayIndex() else count - 1
        for (day in 0 until count) {
            if (day > today) continue
            val slot = offset + day
            val x = left + slot % 7 * (layout.cell + gap)
            val y = layout.chartTop + headerHeight + slot / 7 * (layout.cell + gap)
            val seconds = stats.daySec?.getOrElse(day) { 0 } ?: 0
            if (state.monthHeatmap()) {
                heatCell(ink, x, y, layout.cell, HostPeriodChart.heatLevel(seconds))
            } else {
                ink.box(LandscapeRect(x, y, x + layout.cell, y + layout.cell), Color.WHITE, Color.BLACK)
                if (seconds > 0) checkmark(ink, x, y, layout.cell)
            }
        }
    }

    /** A proportional stroked mark stays inside even the smallest allocated date cell. */
    private fun checkmark(
        ink: LandscapeInk,
        x: Float,
        y: Float,
        size: Float,
    ) {
        val paint = ink.paint
        val saved = ink.canvas.save()
        ink.canvas.clipRect(x + size * .15f, y + size * .15f, x + size * .85f, y + size * .85f)
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * .08f
        val path =
            Path().apply {
                moveTo(x + size * .25f, y + size * .48f)
                lineTo(x + size * .44f, y + size * .68f)
                lineTo(x + size * .76f, y + size * .30f)
            }
        ink.canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        ink.canvas.restoreToCount(saved)
    }

    private fun text(
        ink: LandscapeInk,
        value: String,
        left: Float,
        top: Float,
    ) {
        ink.paint.style = Paint.Style.FILL
        ink.paint.color = Color.BLACK
        ink.canvas.drawText(value, left, top - ink.paint.fontMetrics.ascent, ink.paint)
    }

    private fun wrap(
        value: String,
        paint: Paint,
        width: Float,
    ): List<String> {
        val lines = mutableListOf<String>()
        var rest = value
        while (rest.isNotEmpty()) {
            val length = paint.breakText(rest, true, width.coerceAtLeast(1f), null).coerceAtLeast(1)
            val boundary =
                if (length <
                    rest.length
                ) {
                    rest.lastIndexOf(' ', length - 1).takeIf { it > 0 } ?: length
                } else {
                    length
                }
            lines += rest.take(boundary).trim()
            rest = rest.drop(boundary).trimStart()
        }
        return lines
    }

    /** Patterns use the same stable 2x2 Bayer ordering and coverage as portrait, at physical pixel size. */
    private fun heatCell(
        ink: LandscapeInk,
        x: Float,
        y: Float,
        size: Float,
        level: Int,
    ) {
        val matrixValues = FloatArray(9)

        @Suppress("DEPRECATION")
        val matrix = ink.canvas.matrix
        matrix.getValues(matrixValues)
        val scale = kotlin.math.hypot(matrixValues[Matrix.MSCALE_X], matrixValues[Matrix.MSKEW_Y]).coerceAtLeast(.01f)
        val pixels = (size * scale).roundToInt().coerceAtLeast(3)
        val bitmap = MonthHeatPattern.bitmap(pixels, level)
        ink.paint.isAntiAlias = false
        ink.paint.isFilterBitmap = false
        ink.canvas.drawBitmap(bitmap, null, RectF(x, y, x + pixels / scale, y + pixels / scale), ink.paint)
        ink.paint.isAntiAlias = true
    }
}

internal object MonthHeatPattern {
    // Byte budget includes all sizes and tiers; eviction releases references without recycling in-use images.
    private val patterns by lazy {
        object : LruCache<Pair<Int, Int>, Bitmap>(64 * 1024) {
            override fun sizeOf(
                key: Pair<Int, Int>,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }
    }

    fun bitmap(
        size: Int,
        level: Int,
    ): Bitmap =
        patterns.get(size to level)
            ?: Bitmap.createBitmap(pixels(size, level), size, size, Bitmap.Config.ARGB_8888).also {
                patterns.put(size to level, it)
            }

    fun pixels(
        size: Int,
        level: Int,
    ): IntArray {
        val result = IntArray(size * size) { Color.WHITE }
        val inner = (size - 2).coerceAtLeast(0)
        val ranks = intArrayOf(0, 2, 3, 1)
        val order = (0 until inner * inner).sortedBy { ranks[(it / inner % 2) * 2 + it % inner % 2] }
        val black = (inner * inner * level.coerceIn(0, 4) / 4f).roundToInt()
        order.take(black).forEach { result[(it / inner + 1) * size + it % inner + 1] = Color.BLACK }
        for (i in 0 until size) {
            result[i] = Color.BLACK
            result[(size - 1) * size + i] = Color.BLACK
            result[i * size] = Color.BLACK
            result[i * size + size - 1] = Color.BLACK
        }
        return result
    }
}
