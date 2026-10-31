package com.shuiyu.octoberrain.feature.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.common.immersivePortraitBalance
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 沉浸常用栏与可辨识应用抽屉；只持有临时页码，不改变普通桌面顺序。 */
internal class ImmersiveHome {
    var drawerOpen = false
        private set
    private var drawerPage = 0
    private var downX = 0f
    private var downY = 0f
    private val hits = mutableListOf<Pair<RectF, (() -> Unit)>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        val extent = (if (landscape) rail.height() else rail.width()) / count
        for (index in 0 until count) {
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
            val top = cell.centerY() - (size + textGap + textHeight) / 2f
            val icon = RectF(cell.centerX() - size / 2, top, cell.centerX() + size / 2, top + size)
            val labelBaseline = icon.bottom + textGap - metrics.ascent
            if (entry != null) {
                drawIcon(canvas, icon, entry, false)
                if (showLabels) {
                    dockLabel(canvas, entry.label, cell, labelBaseline, t.textSmall)
                }
                hits += cell to { launch(entry) }
            } else {
                centerText(canvas, "»", icon.centerX(), icon.bottom, size)
                dockLabel(
                    canvas,
                    "全部应用",
                    cell,
                    labelBaseline,
                    minOf(t.textSmall, cell.width() / 4.2f),
                )
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
        val now = Date()
        val clock = SimpleDateFormat("HH:mm", Locale.CHINA).format(now)
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
        text(canvas, value, x, y, size)
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
        text(canvas, "应用", edge, edge + t.textTitle, t.textTitle)
        val back = RectF(width / 2f, edge, width - edge, edge + line)
        text(canvas, "返回本书", back.left, back.centerY(), t.textBody)
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
            text(canvas, entry.label, row.left + line, row.centerY(), t.textBody)
            canvas.restore()
            hits += row to { launch(entry) }
        }
        val bottom = height - edge - line
        val previous = RectF(edge, bottom, width / 3f, height - edge)
        val next = RectF(width / 3f, bottom, width * 2 / 3f, height - edge)
        val leave = RectF(width * 2 / 3f, bottom, width - edge, height - edge)
        text(canvas, "上一页", previous.left, previous.centerY(), t.textSmall)
        text(canvas, "下一页", next.left, next.centerY(), t.textSmall)
        text(canvas, "退出沉浸", leave.left, leave.centerY(), t.textSmall)
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

    private fun text(
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
