package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors

/** 沉浸阅读区：书堆是主角，时间和应用栏交给桌面宿主。 */
internal class WereadImmersivePainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun layout(
        width: Int,
        height: Int,
        landscape: Boolean = width > height * 1.35f,
    ): WereadBoardFrame {
        val w = width.toFloat()
        val h = height.toFloat()
        val coverH = minOf(h * if (landscape) .58f else .48f, w * if (landscape) .43f else .63f)
        val coverY = h * if (landscape) .65f else .27f
        val titleY = if (landscape) h * .015f else coverY + coverH / 2 + h * .015f
        val size = if (landscape) minOf(w * .044f, h * .065f) else minOf(w * .065f, h * .052f)
        val barY = if (landscape) h * .245f else titleY + size * 3.6f
        return WereadBoardFrame.of(w, h, 0f).copy(
            coverArea =
                BoardBounds(
                    if (landscape) w * .30f else 0f,
                    if (landscape) h * .32f else 0f,
                    w,
                    if (landscape) h else titleY,
                ),
            coverCenterX = if (landscape) w * .64f else w / 2,
            coverCenterY = coverY,
            coverHeight = coverH,
            coverWidth = coverH / 1.45f,
            expandedCoverRings = true,
            titleTop = titleY,
            titleSize = size,
            barBounds =
                BoardBounds(
                    if (landscape) w * .38f else w * .22f,
                    barY,
                    if (landscape) w * .97f - DesignRuntime.tokens.dpF(44f) else w * .78f,
                    barY + 4f,
                ),
        )
    }

    fun refreshBounds(
        width: Int,
        height: Int,
        landscape: Boolean,
    ): BoardBounds {
        val frame = layout(width, height, landscape)
        val size = DesignRuntime.tokens.dpF(44f)
        val right = if (landscape) width * .98f else frame.barBounds.right + size
        return BoardBounds(right - size, frame.barBounds.centerY - size / 2, right, frame.barBounds.centerY + size / 2)
    }

    fun draw(
        canvas: Canvas,
        scene: WereadBoardScene,
        frame: WereadBoardFrame,
        width: Int,
        height: Int,
        metrics: ImmersiveReadingStats = ImmersiveReadingStats(),
        landscape: Boolean = width > height * 1.35f,
        busy: Boolean = false,
    ) {
        drawCovers(canvas, scene, frame)
        drawReadingInfo(canvas, scene, frame, width.toFloat(), height.toFloat(), landscape, metrics)
        if (landscape && scene.books.isNotEmpty()) {
            val size = DesignRuntime.tokens.textCaption
            val y = minOf(frame.coverCenterY + frame.coverHeight / 2 + size * 1.5f, height * .99f)
            text(canvas, "${scene.selectedIndex + 1} / ${scene.books.size}", frame.coverCenterX, y, size)
        }
        drawRefresh(canvas, width, height, landscape, busy)
    }

    fun drawRefresh(
        canvas: Canvas,
        width: Int,
        height: Int,
        landscape: Boolean,
        busy: Boolean,
    ) {
        val refresh = refreshBounds(width, height, landscape)
        val cx = refresh.centerX
        val cy = refresh.centerY
        val radius = DesignRuntime.tokens.dpF(7f)
        paint.color = InkColors.INK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = DesignRuntime.tokens.dpF(1.4f)
        canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 35f, 295f, false, paint)
        canvas.drawLine(cx + radius, cy - radius, cx + radius, cy, paint)
        canvas.drawLine(cx + radius, cy, cx, cy, paint)
        paint.style = Paint.Style.FILL
        if (busy) canvas.drawCircle(cx, cy, DesignRuntime.tokens.dpF(2f), paint)
    }

    private fun drawCovers(
        canvas: Canvas,
        scene: WereadBoardScene,
        frame: WereadBoardFrame,
    ) {
        scene.slots.forEach { slot ->
            val book = scene.books.getOrNull(slot.index) ?: return@forEach
            val box = coverBoundsOf(frame, slot)
            val rect = RectF(box.left, box.top, box.right, box.bottom)
            paint.style = Paint.Style.FILL
            paint.color = InkColors.PAPER
            canvas.drawRect(rect, paint)
            val bitmap = scene.cover(book.bookId)?.takeUnless { it.isRecycled }
            if (bitmap != null) {
                val scale = minOf(rect.width() / bitmap.width, rect.height() / bitmap.height)
                val bw = bitmap.width * scale
                val bh = bitmap.height * scale
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(
                        rect.centerX() - bw / 2,
                        rect.centerY() - bh / 2,
                        rect.centerX() + bw / 2,
                        rect.centerY() + bh / 2,
                    ),
                    paint,
                )
            } else {
                canvas.save()
                canvas.clipRect(rect)
                paint.textSize = frame.titleSize * .8f
                paint.typeface = DesignRuntime.typeface()
                val coverLines = InkDraw.wrap(book.title, paint, rect.width() * .82f, 3)
                coverLines.forEachIndexed { index, line ->
                    text(
                        canvas,
                        line,
                        rect.centerX(),
                        rect.centerY() + (index - (coverLines.size - 1) / 2f) * paint.textSize * 1.25f,
                        frame.titleSize * .8f,
                    )
                }
                canvas.restore()
            }
            paint.color = InkColors.INK_3
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRect(rect, paint)
        }
    }

    private fun drawReadingInfo(
        canvas: Canvas,
        scene: WereadBoardScene,
        frame: WereadBoardFrame,
        w: Float,
        h: Float,
        landscape: Boolean,
        metrics: ImmersiveReadingStats,
    ) {
        val current = scene.current ?: return
        val cx = if (landscape) w * .68f else w / 2
        val textWidth = if (landscape) w * .60f else w * .88f
        paint.textSize = frame.titleSize
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val lines = InkDraw.wrap(immersiveWorkTitle(current.title), paint, textWidth, 2)
        lines.forEachIndexed { i, line ->
            text(
                canvas,
                line,
                if (landscape) w * .38f else cx,
                frame.titleTop + frame.titleSize * (i + 1) * 1.12f,
                frame.titleSize,
                heading = true,
                leftAligned = landscape,
            )
        }
        val authorY = frame.titleTop + frame.titleSize * (lines.size * 1.12f + .95f) + DesignRuntime.tokens.dp(4f)
        val detailSize = maxOf(frame.titleSize * .64f, DesignRuntime.tokens.textCaption)
        paint.textSize = detailSize
        paint.typeface = DesignRuntime.typeface()
        val author = InkDraw.wrap(current.author, paint, textWidth, 1).firstOrNull().orEmpty()
        text(canvas, author, if (landscape) w * .38f else cx, authorY, detailSize, leftAligned = landscape)
        val barY = frame.barBounds.top
        val barWidth = frame.barBounds.width
        val barCenter = frame.barBounds.centerX
        paint.color = InkColors.INK_3
        canvas.drawRect(barCenter - barWidth / 2, barY, barCenter + barWidth / 2, barY + 4f, paint)
        paint.color = InkColors.INK
        canvas.drawRect(
            barCenter - barWidth / 2,
            barY,
            barCenter - barWidth / 2 + barWidth * current.progressPercent.coerceIn(0, 100) / 100f,
            barY + 4f,
            paint,
        )
        text(
            canvas,
            "已读 ${current.progressPercent.coerceIn(0, 100)}% · 累计 ${immersiveReadingDuration(current.readingSeconds)}",
            barCenter,
            barY + frame.titleSize * 1.05f,
            detailSize,
        )
        if (landscape) {
            drawVerticalStats(canvas, metrics, frame, w, h)
        } else {
            drawStats(canvas, metrics, frame, cx, h, w * .92f)
        }
    }

    private fun drawVerticalStats(
        canvas: Canvas,
        metrics: ImmersiveReadingStats,
        frame: WereadBoardFrame,
        w: Float,
        h: Float,
    ) {
        val values = listOf(metrics.todayMinutes, metrics.finishedBooks, metrics.streakDays)
        val labels = listOf("今日阅读", "读完书籍", "连续阅读")
        val units = listOf("分钟", "本", "天")
        val left = w * .01f + 20f
        val right = w * .26f + 20f
        values.forEachIndexed { index, value ->
            val top = h * (.35f + index * .21f)
            paint.color = InkColors.INK_3
            paint.strokeWidth = 1f
            canvas.drawLine(left, top, right, top, paint)
            drawStatValue(
                canvas,
                value?.toString() ?: "—",
                if (value == null) "" else units[index],
                left,
                top + h * .09f,
                frame.titleSize * 1.25f,
                leftAligned = true,
            )
            text(canvas, labels[index], left, top + h * .155f, DesignRuntime.tokens.textCaption, leftAligned = true)
        }
    }

    private fun drawStats(
        canvas: Canvas,
        metrics: ImmersiveReadingStats,
        frame: WereadBoardFrame,
        cx: Float,
        h: Float,
        statsWidth: Float,
    ) {
        val top = h * .81f
        val bottom = h * .98f
        val gap = statsWidth * .025f
        val cardWidth = (statsWidth - gap * 2) / 3
        val values =
            listOf(
                metrics.todayMinutes?.toString() ?: "—",
                metrics.finishedBooks?.toString() ?: "—",
                metrics.streakDays?.toString() ?: "—",
            )
        val labels = listOf("今日阅读", "读完书籍", "连续阅读")
        val units =
            listOf(
                if (metrics.todayMinutes ==
                    null
                ) {
                    ""
                } else {
                    "分钟"
                },
                "",
                if (metrics.streakDays == null) "" else "天",
            )
        values.forEachIndexed { i, value ->
            val left = cx - statsWidth / 2 + (cardWidth + gap) * i
            paint.style = Paint.Style.FILL
            paint.color = 0xffeeeeee.toInt()
            val radius = DesignRuntime.tokens.dp(8f).toFloat()
            canvas.drawRoundRect(RectF(left, top, left + cardWidth, bottom), radius, radius, paint)
            val x = left + cardWidth / 2
            drawStatValue(
                canvas,
                value,
                units[i],
                x,
                top + (bottom - top) * .43f,
                maxOf(frame.titleSize * .98f, DesignRuntime.tokens.textTitle),
            )
            text(
                canvas,
                labels[i],
                x,
                top + (bottom - top) * .79f,
                maxOf(frame.titleSize * .55f, DesignRuntime.tokens.textCaption),
            )
        }
    }

    private fun drawStatValue(
        canvas: Canvas,
        value: String,
        unit: String,
        x: Float,
        y: Float,
        size: Float,
        leftAligned: Boolean = false,
    ) {
        paint.typeface = DesignRuntime.typeface()
        paint.textSize = size
        val numberWidth = paint.measureText(value)
        val unitSize = DesignRuntime.tokens.textCaption
        paint.textSize = unitSize
        val unitWidth = paint.measureText(unit)
        val gap = if (unit.isEmpty()) 0f else DesignRuntime.tokens.dp(3f).toFloat()
        val left = if (leftAligned) x else x - (numberWidth + gap + unitWidth) / 2
        text(canvas, value, left + numberWidth / 2, y, size)
        text(canvas, unit, left + numberWidth + gap + unitWidth / 2, y, unitSize)
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        heading: Boolean = false,
        leftAligned: Boolean = false,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = InkColors.INK
        paint.textSize = size
        paint.typeface = if (heading) Typeface.create(Typeface.SERIF, Typeface.BOLD) else DesignRuntime.typeface()
        paint.textAlign = if (leftAligned) Paint.Align.LEFT else Paint.Align.CENTER
        canvas.drawText(value, x, y, paint)
    }
}
