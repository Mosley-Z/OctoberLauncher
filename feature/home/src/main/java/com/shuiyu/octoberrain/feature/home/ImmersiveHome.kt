package com.shuiyu.octoberrain.feature.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import com.shuiyu.octoberrain.core.common.ImmersiveLandscapeGeometry
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.common.immersivePortraitBalance
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 沉浸常用栏与可辨识应用抽屉；只持有临时页码，不改变普通桌面顺序。 */
internal class ImmersiveHome {
    var twelveHour = false
    var landscapeGeometry: ImmersiveLandscapeGeometry? = null
    var drawerOpen = false
        private set
    private var drawerPage = 0
    private var downX = 0f
    private var downY = 0f
    private val hits = mutableListOf<Pair<RectF, (() -> Unit)>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clockPainter = LandscapeClockPainter(paint)

    fun admitsLandscapeGeometry(
        candidate: ImmersiveLandscapeGeometry,
        width: Int,
    ): Boolean {
        val t = DesignRuntime.tokens
        val defaults = InkTokens(t.device)
        if (t.textTitle > defaults.textTitle ||
            t.textBody > defaults.textBody ||
            t.textCaption > defaults.textCaption
        ) {
            return false
        }
        return clockPainter.admits(candidate, width, twelveHour)
    }

    fun closeDrawer(): Boolean {
        if (!drawerOpen) return false
        drawerOpen = false
        return true
    }

    fun bookBounds(
        width: Int,
        height: Int,
    ): RectF {
        val t = DesignRuntime.tokens
        val edge = if (width > height) t.pageGutter.toFloat() else portraitEdge(width, height)
        val rail = t.dp(88f).toFloat()
        return if (width > height) {
            val inset = landscapeInset(width, height)
            RectF(inset + edge + t.dp(12f), edge, width - inset - rail - edge - 20f, height - edge)
        } else {
            paint.typeface = DesignRuntime.typeface()
            paint.textSize = t.textBody
            val balance = portraitBalance(width, height)
            val clockSize = minOf(t.dp(52f).toFloat(), width * .16f) * (1f + .30f * balance)
            val dateBottom = edge + height * .041f * balance + clockSize + t.textBody * 1.7f + paint.fontMetrics.descent
            val top = maxOf(edge + t.dp(110f), dateBottom + t.dp(4f))
            RectF(edge, top, width - edge, height - rail - edge - height * .037f * balance)
        }
    }

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        entries: List<LauncherEntry>,
        favorites: List<LauncherEntry>,
        showLabels: Boolean,
        drawIcon: (Canvas, RectF, LauncherEntry, Boolean) -> Unit,
        launch: (LauncherEntry) -> Unit,
        exit: () -> Unit,
        redraw: () -> Unit,
    ) {
        val t = DesignRuntime.tokens
        val edge = if (width > height) t.pageGutter.toFloat() else portraitEdge(width, height)
        hits.clear()
        if (drawerOpen) {
            drawDrawer(canvas, width, height, entries, drawIcon, launch, exit, redraw)
            return
        }

        val landscape = width > height
        drawClock(canvas, width, height)
        val bounds = bookBounds(width, height)
        val rail =
            if (landscape) {
                RectF(bounds.right + edge, edge, width - landscapeInset(width, height) - edge - 20f, height - edge)
            } else {
                RectF(
                    edge,
                    bounds.bottom + edge,
                    width - edge,
                    height - edge - height * .037f * portraitBalance(width, height),
                )
            }
        landscapeGeometry?.takeIf { it.weight > 0f }?.let { geometry ->
            val half = rail.width() / 2f
            val center = geometry.blend(rail.centerX(), geometry.railCenter)
            rail.offset(center - rail.centerX(), 0f)
            rail.left = maxOf(0f, center - half)
            rail.right = minOf(width.toFloat(), center + half)
        }
        drawRail(canvas, rail, landscape, favorites, showLabels, drawIcon, launch, redraw)
    }

    private fun drawRail(
        canvas: Canvas,
        rail: RectF,
        landscape: Boolean,
        favorites: List<LauncherEntry>,
        showLabels: Boolean,
        drawIcon: (Canvas, RectF, LauncherEntry, Boolean) -> Unit,
        launch: (LauncherEntry) -> Unit,
        redraw: () -> Unit,
    ) {
        val t = DesignRuntime.tokens
        val gap = t.dp(4f).toFloat()
        val padding = t.dp(4f).toFloat()
        val visibleFavorites = railFavorites(paint, rail, landscape, favorites)
        val count = visibleFavorites.size + 1
        val railGeometry =
            landscapeGeometry?.takeIf {
                landscape &&
                    visibleFavorites.size == 4 &&
                    it.weight > 0f &&
                    rail.height() / 5 >= t.dpF(40f) + t.textSmall * 1.5f + gap + padding * 2
            }
        val extent = (if (landscape) rail.height() else rail.width()) / count
        for (index in 0 until count) {
            val cell = railCell(rail, landscape, index, extent, railGeometry)
            val entry = visibleFavorites.getOrNull(index)
            val hasLabel = entry == null || showLabels
            val fontSize = if (entry == null) minOf(t.textSmall, cell.width() / 4.2f) else t.textSmall
            paint.textSize = fontSize
            val metrics = paint.fontMetrics
            val textHeight = if (hasLabel) metrics.descent - metrics.ascent else 0f
            val textGap = if (hasLabel) gap else 0f
            val size =
                minOf(
                    t.dp(40f).toFloat(),
                    cell.width() * .65f,
                    (cell.height() - padding * 2 - textGap - textHeight).coerceAtLeast(0f),
                )
            val oldTop = rail.top + (index + .5f) * extent - (size + textGap + textHeight) / 2f
            val top =
                railGeometry?.let {
                    it.blend(oldTop, it.railIconYs[index] - size / 2)
                } ?: (cell.centerY() - (size + textGap + textHeight) / 2f)
            val icon = RectF(cell.centerX() - size / 2, top, cell.centerX() + size / 2, top + size)
            val enlargedFont = enlargeRailIcon(paint, icon, cell, fontSize, hasLabel, railGeometry)
            paint.textSize = enlargedFont
            val oldLabelBaseline = icon.bottom + textGap - paint.fontMetrics.ascent
            val labelBaseline = railLabelBaseline(oldLabelBaseline, entry == null, railGeometry)
            if (entry != null) {
                drawIcon(canvas, icon, entry, false)
                if (showLabels) {
                    dockLabel(canvas, entry.label, cell, labelBaseline, enlargedFont)
                }
                hits += cell to { launch(entry) }
            } else {
                centerText(canvas, "»", icon.centerX(), railArrowBaseline(paint, icon, size, railGeometry), size)
                dockLabel(canvas, "全部应用", cell, labelBaseline, enlargedFont)
                hits += cell to {
                    drawerOpen = true
                    redraw()
                }
            }
        }
    }

    private fun dockLabel(
        canvas: Canvas,
        label: String,
        cell: RectF,
        y: Float,
        size: Float,
    ) {
        paint.textSize = size
        val maxWidth = cell.width() - 4f
        val value =
            if (paint.measureText(label) <= maxWidth) {
                label
            } else {
                val length = paint.breakText(label, true, (maxWidth - paint.measureText("…")).coerceAtLeast(0f), null)
                label.take(length) + "…"
            }
        centerText(canvas, value, cell.centerX(), y, size)
    }

    private fun drawClock(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        val t = DesignRuntime.tokens
        val edge = if (width > height) t.pageGutter.toFloat() else portraitEdge(width, height)
        val landscape = width > height
        val geometry = landscapeGeometry?.takeIf { it.weight > 0f }
        if (geometry != null) {
            clockPainter.draw(canvas, geometry, bookBounds(width, height), width, twelveHour)
            return
        }
        val now = Date()
        val clock = immersiveClockText(now, twelveHour)
        val date = SimpleDateFormat(if (landscape) "M月d日　EEEE" else "EEEE，M月d日", Locale.CHINA).format(now)
        val balance = if (landscape) 0f else portraitBalance(width, height)
        val offset = height * .041f * balance
        val size = minOf(t.dp(if (landscape) 50f else 52f).toFloat(), width * .16f) * (1f + .30f * balance)
        val book = bookBounds(width, height)
        val center = if (landscape) book.left + book.width() * .14f + 20f else book.centerX()
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        centerText(canvas, clock, center, edge + offset + size, size)
        paint.typeface = DesignRuntime.typeface()
        val dateSize = if (landscape) maxOf(20f, t.textCaption) else t.textBody
        if (landscape) {
            val left = book.left + 20f
            val right = book.left + book.width() * .30f
            dockLabel(canvas, date, RectF(left, 0f, right, 0f), edge + size + dateSize * 1.7f, dateSize)
        } else {
            centerText(canvas, date, center, edge + offset + size + dateSize * 1.7f, dateSize)
        }
    }

    private fun centerText(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
    ) {
        paint.textAlign = Paint.Align.CENTER
        outlinedHomeText(paint, canvas, value, x, y, size)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawDrawer(
        canvas: Canvas,
        width: Int,
        height: Int,
        entries: List<LauncherEntry>,
        drawIcon: (Canvas, RectF, LauncherEntry, Boolean) -> Unit,
        launch: (LauncherEntry) -> Unit,
        exit: () -> Unit,
        redraw: () -> Unit,
    ) {
        val t = DesignRuntime.tokens
        val edge = t.pageGutter.toFloat()
        val line = t.dp(56f).toFloat()
        paint.color = InkColors.PAPER
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        outlinedHomeText(paint, canvas, "应用", edge, edge + t.textTitle, t.textTitle)
        val back = RectF(width / 2f, edge, width - edge, edge + line)
        outlinedHomeText(paint, canvas, "返回本书", back.left, back.centerY(), t.textBody)
        hits += back to {
            drawerOpen = false
            redraw()
        }
        val startY = edge + line
        val capacity = ((height - startY - line - edge) / line).toInt().coerceAtLeast(1)
        val pages = ((entries.size + capacity - 1) / capacity).coerceAtLeast(1)
        drawerPage = drawerPage.coerceIn(0, pages - 1)
        entries.drop(drawerPage * capacity).take(capacity).forEachIndexed { index, entry ->
            val row = RectF(edge, startY + index * line, width - edge, startY + (index + 1) * line)
            val size = line * .62f
            drawIcon(
                canvas,
                RectF(row.left, row.centerY() - size / 2, row.left + size, row.centerY() + size / 2),
                entry,
                true,
            )
            canvas.save()
            canvas.clipRect(row)
            outlinedHomeText(paint, canvas, entry.label, row.left + line, row.centerY(), t.textBody)
            canvas.restore()
            hits += row to { launch(entry) }
        }
        val bottom = height - edge - line
        val previous = RectF(edge, bottom, width / 3f, height - edge)
        val next = RectF(width / 3f, bottom, width * 2 / 3f, height - edge)
        val leave = RectF(width * 2 / 3f, bottom, width - edge, height - edge)
        outlinedHomeText(paint, canvas, "上一页", previous.left, previous.centerY(), t.textSmall)
        outlinedHomeText(paint, canvas, "下一页", next.left, next.centerY(), t.textSmall)
        outlinedHomeText(paint, canvas, "退出沉浸", leave.left, leave.centerY(), t.textSmall)
        hits += previous to {
            drawerPage = (drawerPage - 1).coerceAtLeast(0)
            redraw()
        }
        hits += next to {
            drawerPage = (drawerPage + 1).coerceAtMost(pages - 1)
            redraw()
        }
        hits += leave to exit
    }

    fun onTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
        }
        if (event.actionMasked == MotionEvent.ACTION_UP && abs(event.x - downX) < 12 && abs(event.y - downY) < 12) {
            hits
                .firstOrNull {
                    it.first.contains(
                        event.x,
                        event.y,
                    ) &&
                        it.first.contains(downX, downY)
                }?.second
                ?.invoke()
        }
        return true
    }
}

private fun landscapeInset(
    width: Int,
    height: Int,
): Float {
    val contentWidth = minOf(width.toFloat(), DesignRuntime.tokens.dpF(720f), height * 1.85f)
    return (width - contentWidth) / 2f
}

private fun railFavorites(
    paint: Paint,
    rail: RectF,
    landscape: Boolean,
    favorites: List<LauncherEntry>,
): List<LauncherEntry> {
    val t = DesignRuntime.tokens
    paint.typeface = DesignRuntime.typeface()
    paint.textSize = t.textSmall
    val labelMetrics = paint.fontMetrics
    val gap = t.dp(4f).toFloat()
    val padding = t.dp(4f).toFloat()
    val requiredHeight =
        maxOf(
            t.dp(48f).toFloat(),
            minOf(t.dp(40f).toFloat(), rail.width() * .65f) +
                gap + labelMetrics.descent - labelMetrics.ascent + padding * 2,
        )
    return if (landscape) {
        val slots = (rail.height() / requiredHeight).toInt().coerceAtLeast(1)
        favorites.take((slots - 1).coerceAtLeast(0))
    } else {
        favorites
    }
}

/** 竖屏富余空间优先用于上下留白，小高度退回基础页边距。 */
private fun portraitEdge(
    width: Int,
    height: Int,
): Float {
    val t = DesignRuntime.tokens
    val base = t.pageGutter.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.typeface = DesignRuntime.typeface()
    paint.textSize = t.textBody
    val dateHeight = minOf(t.dpF(52f), width * .16f) + t.textBody * 1.7f + paint.fontMetrics.descent
    val spare = height - dateHeight - t.dpF(88f) - t.dpF(200f) - base * 2
    return base + (spare / 2).coerceIn(0f, (t.dpF(28f) - base).coerceAtLeast(0f))
}

/** 文字和应用栏优先，紧高度下逐渐撤回视觉微调。 */
private fun portraitBalance(
    width: Int,
    height: Int,
): Float {
    val t = DesignRuntime.tokens
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = DesignRuntime.typeface()
            textSize = t.textBody
        }
    val clock = minOf(t.dpF(52f), width * .16f)
    val spare =
        height - clock - t.textBody * 1.7f - paint.fontMetrics.descent - t.dpF(288f) - portraitEdge(width, height) * 2
    val needed = height * .078f + clock * .30f
    return immersivePortraitBalance(width.toFloat(), height.toFloat()) *
        (spare / needed.coerceAtLeast(1f)).coerceIn(0f, 1f)
}

private class LandscapeClockPainter(
    private val paint: Paint,
) {
    fun admits(
        geometry: ImmersiveLandscapeGeometry,
        width: Int,
        twelveHour: Boolean,
    ): Boolean {
        val book = geometry.originalBook
        val now = Date()
        return drawLandscapeDate(
            null,
            geometry,
            RectF(book.left, book.top, book.right, book.bottom),
            now,
            minOf(DesignRuntime.tokens.dpF(50f), width * .16f),
            immersiveClockText(now, twelveHour),
        )
    }

    fun draw(
        canvas: Canvas,
        geometry: ImmersiveLandscapeGeometry,
        book: RectF,
        width: Int,
        twelveHour: Boolean,
    ) {
        val t = DesignRuntime.tokens
        val oldSize = minOf(t.dpF(50f), width * .16f)
        val oldCenter = book.left + book.width() * .14f + 20f
        val now = Date()
        val clock = immersiveClockText(now, twelveHour)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = oldSize
        val glyph = Rect()
        paint.getTextBounds(clock, 0, clock.length, glyph)
        val targetSize = oldSize * geometry.window.height * .062f / glyph.height().coerceAtLeast(1)
        val size = geometry.blend(oldSize, targetSize)
        paint.textSize = size
        val left = geometry.blend(oldCenter - paint.measureText(clock) / 2, geometry.clockLeft)
        paint.textAlign = Paint.Align.LEFT
        outlinedHomeText(
            paint,
            canvas,
            clock,
            left,
            geometry.blend(t.pageGutter + oldSize, geometry.clockBaseline),
            size,
        )
        drawLandscapeDate(
            canvas,
            geometry,
            book,
            now,
            oldSize,
            clock,
        )
    }

    private fun drawLandscapeDate(
        canvas: Canvas?,
        geometry: ImmersiveLandscapeGeometry,
        book: RectF,
        now: Date,
        oldClockSize: Float,
        clock: String,
    ): Boolean {
        val t = DesignRuntime.tokens
        val clockInkBottom = landscapeClockInkBottom(paint, geometry, clock, oldClockSize)
        paint.typeface = DesignRuntime.typeface()
        val oldSize = maxOf(20f, t.textCaption)
        paint.textSize = oldSize
        val date = SimpleDateFormat("M月d日　EEEE", Locale.CHINA).format(now)
        val oldLeft = book.left + 20f
        val oldRight = book.left + book.width() * .30f
        val oldWidth = oldRight - oldLeft - 4f
        val oldLength = paint.breakText(date, true, (oldWidth - paint.measureText("…")).coerceAtLeast(0f), null)
        val oldShown = if (paint.measureText(date) <= oldWidth) date else date.take(oldLength) + "…"
        val oldTextLeft = (oldLeft + oldRight - paint.measureText(oldShown)) / 2
        val glyph = Rect()
        paint.getTextBounds("日", 0, 1, glyph)
        val defaultSize = maxOf(20f, t.dpF(14f))
        val target =
            ImmersiveLandscapeGeometry.magnifiedGlyphSize(
                oldSize * geometry.window.height * .023f / glyph.height().coerceAtLeast(1),
                oldSize,
                defaultSize,
            )
        paint.textSize = target
        val clockBottom = geometry.blend(t.pageGutter + oldClockSize, geometry.clockBaseline)
        val statsTop =
            minOf(
                geometry.homeY(.339f),
                geometry.originalBook.top + geometry.originalBook.height * .35f,
            )
        val size = geometry.blend(oldSize, target)
        paint.textSize = size
        val left = geometry.blend(oldTextLeft, geometry.clockLeft)
        val available =
            geometry.blend(oldWidth, geometry.homeX(.289f) - t.dpF(8f) - geometry.clockLeft).coerceAtLeast(1f)
        val compact = date.replace("　", " ")
        val lines = dateLines(compact, available)
        val metrics = paint.fontMetrics
        val baseline =
            maxOf(
                geometry.blend(t.pageGutter + oldClockSize + oldSize * 1.7f, geometry.dateBaseline),
                clockBottom + t.dpF(4f) - metrics.ascent,
            )
        val lineHeight = metrics.descent - metrics.ascent
        if (canvas == null) {
            val (firstInkTop, lastInkBottom) = LandscapeDateInk.bounds(paint, lines, lineHeight, glyph)
            return ImmersiveLandscapeGeometry.dateInkFits(
                clockBottom + clockInkBottom,
                baseline + firstInkTop,
                baseline + lastInkBottom,
                statsTop,
                t.dpF(4f),
            )
        }
        lines.forEachIndexed { index, line ->
            outlinedHomeText(paint, canvas, line, left, baseline + index * lineHeight, size)
        }
        return true
    }

    private fun dateLines(
        date: String,
        available: Float,
    ): List<String> {
        if (paint.measureText(date) <= available) return listOf(date)
        val parts = date.split(' ')
        return if (parts.size == 2 && parts.all { paint.measureText(it) <= available }) {
            parts
        } else {
            val lines = mutableListOf<String>()
            var remaining = date
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, available, null).coerceAtLeast(1)
                lines += remaining.take(count).trim()
                remaining = remaining.drop(count).trimStart()
            }
            lines
        }
    }
}

private object LandscapeDateInk {
    fun bounds(
        paint: Paint,
        lines: List<String>,
        lineHeight: Float,
        glyph: Rect,
    ): Pair<Float, Float> {
        var firstInkTop = Float.POSITIVE_INFINITY
        var lastInkBottom = Float.NEGATIVE_INFINITY
        lines.forEachIndexed { index, line ->
            paint.getTextBounds(line, 0, line.length, glyph)
            firstInkTop = minOf(firstInkTop, index * lineHeight + glyph.top)
            lastInkBottom = maxOf(lastInkBottom, index * lineHeight + glyph.bottom)
        }
        return firstInkTop to lastInkBottom
    }
}

private fun landscapeClockInkBottom(
    paint: Paint,
    geometry: ImmersiveLandscapeGeometry,
    clock: String,
    oldClockSize: Float,
): Float {
    paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    paint.textSize = oldClockSize
    val clockGlyph = Rect()
    paint.getTextBounds(clock, 0, clock.length, clockGlyph)
    paint.textSize =
        geometry.blend(
            oldClockSize,
            oldClockSize * geometry.window.height * .062f / clockGlyph.height().coerceAtLeast(1),
        )
    paint.getTextBounds(clock, 0, clock.length, clockGlyph)
    return clockGlyph.bottom.toFloat()
}

private fun outlinedHomeText(
    paint: Paint,
    canvas: Canvas,
    value: String,
    x: Float,
    y: Float,
    size: Float,
) {
    paint.textSize = size
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeJoin = Paint.Join.ROUND
    paint.strokeWidth = maxOf(DesignRuntime.tokens.dpF(1f), size * .06f)
    canvas.drawText(value, x, y, paint)
    paint.style = Paint.Style.FILL
    paint.color = InkColors.INK
    canvas.drawText(value, x, y, paint)
}

private fun railCell(
    rail: RectF,
    landscape: Boolean,
    index: Int,
    extent: Float,
    railGeometry: ImmersiveLandscapeGeometry?,
): RectF {
    val cell =
        if (landscape) {
            RectF(
                rail.left,
                rail.top + index * extent,
                rail.right,
                rail.top + (index + 1) * extent,
            )
        } else {
            RectF(rail.left + index * extent, rail.top, rail.left + (index + 1) * extent, rail.bottom)
        }
    railGeometry?.let { geometry ->
        val centers = geometry.railIconYs
        val targetTop = if (index == 0) rail.top else (centers[index - 1] + centers[index]) / 2
        val targetBottom = if (index == 4) rail.bottom else (centers[index] + centers[index + 1]) / 2
        cell.top = geometry.blend(cell.top, targetTop)
        cell.bottom = geometry.blend(cell.bottom, targetBottom)
    }
    return cell
}

private fun enlargeRailIcon(
    paint: Paint,
    icon: RectF,
    cell: RectF,
    fontSize: Float,
    hasLabel: Boolean,
    geometry: ImmersiveLandscapeGeometry?,
): Float {
    if (geometry == null) return fontSize
    val t = DesignRuntime.tokens
    val target = (geometry.window.height * .0736f).coerceIn(t.dpF(40f), t.dpF(48f))
    val size = geometry.blend(icon.width(), target)
    val font = geometry.blend(fontSize, maxOf(fontSize, t.textSmall * 1.20f))
    paint.textSize = font
    val labelHeight = if (hasLabel) paint.fontMetrics.descent - paint.fontMetrics.ascent + t.dpF(4f) else 0f
    val top = icon.centerY() - size / 2
    val bottom = icon.centerY() + size / 2
    val drawerBottom =
        if (icon.centerY() >= geometry.railIconYs.last() - 1f) {
            geometry.blend(bottom + labelHeight, geometry.railLabelBaseline + paint.fontMetrics.descent)
        } else {
            bottom + labelHeight
        }
    return if (size > cell.width() - t.dpF(8f) ||
        top < cell.top + t.dpF(4f) ||
        maxOf(bottom + labelHeight, drawerBottom) > cell.bottom - t.dpF(4f)
    ) {
        fontSize
    } else {
        icon.set(icon.centerX() - size / 2, top, icon.centerX() + size / 2, bottom)
        font
    }
}

private fun railLabelBaseline(
    oldLabelBaseline: Float,
    isDrawer: Boolean,
    railGeometry: ImmersiveLandscapeGeometry?,
): Float =
    if (isDrawer && railGeometry != null) {
        railGeometry.blend(oldLabelBaseline, railGeometry.railLabelBaseline)
    } else {
        oldLabelBaseline
    }

private fun railArrowBaseline(
    paint: Paint,
    icon: RectF,
    size: Float,
    geometry: ImmersiveLandscapeGeometry?,
): Float {
    if (geometry == null) return icon.bottom
    paint.textSize = size
    val bounds = Rect()
    paint.getTextBounds("»", 0, 1, bounds)
    val baseline = icon.centerY() - (bounds.top + bounds.bottom) / 2f
    return geometry.blend(icon.bottom, baseline)
}
