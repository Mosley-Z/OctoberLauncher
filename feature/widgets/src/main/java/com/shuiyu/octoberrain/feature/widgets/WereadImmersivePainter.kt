package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.shuiyu.octoberrain.core.common.immersivePortraitBalance
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
        if (!landscape) return portraitLayout(width.toFloat(), height.toFloat())
        val w = width.toFloat()
        val h = height.toFloat()
        var coverH = minOf(h * .58f, w * .43f)
        var coverY = h * .65f
        val titleY = h * .015f
        val size = minOf(w * .044f, h * .065f)
        val (titleMetrics, detailMetrics) = readingFontMetrics(size)
        val gap = DesignRuntime.tokens.dpF(4f)
        val barY =
            titleY + (titleMetrics.descent - titleMetrics.ascent) * 2 +
                (detailMetrics.descent - detailMetrics.ascent) + gap * 2
        val infoBottom = barY + 4f + gap + detailMetrics.descent - detailMetrics.ascent
        coverH = minOf(coverH, (h * .94f - infoBottom - gap).coerceAtLeast(0f))
        coverY = infoBottom + gap + coverH / 2
        return WereadBoardFrame.of(w, h, 0f).copy(
            coverArea =
                BoardBounds(
                    w * .30f,
                    infoBottom + gap,
                    w,
                    h,
                ),
            coverCenterX = w * .64f,
            coverCenterY = coverY,
            coverHeight = coverH,
            coverWidth = coverH / 1.45f,
            expandedCoverRings = true,
            titleTop = titleY,
            titleSize = size,
            infoSize = maxOf(size * .64f, DesignRuntime.tokens.textCaption),
            barBounds =
                BoardBounds(
                    w * .38f,
                    barY,
                    w * .97f - DesignRuntime.tokens.dpF(44f),
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
        val stroke = DesignRuntime.tokens.dpF(1.4f)
        for (outline in listOf(true, false)) {
            paint.color = if (outline) android.graphics.Color.WHITE else InkColors.INK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke + if (outline) DesignRuntime.tokens.dpF(2f) else 0f
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 35f, 295f, false, paint)
            canvas.drawLine(cx + radius, cy - radius, cx + radius, cy, paint)
            canvas.drawLine(cx + radius, cy, cx, cy, paint)
            paint.style = Paint.Style.FILL
            if (busy) canvas.drawCircle(cx, cy, DesignRuntime.tokens.dpF(if (outline) 3f else 2f), paint)
        }
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
        val textWidth = if (landscape) frame.barBounds.width.coerceAtLeast(1f) else w * .88f
        val (_, detailMetrics) = readingFontMetrics(frame.titleSize, frame.infoSize)
        val gap = DesignRuntime.tokens.dpF(4f)
        paint.textSize = frame.titleSize
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val lines = InkDraw.wrap(immersiveWorkTitle(current.title), paint, textWidth, 2)
        val (titleBaseline, titleStep, authorY) = readingBaselines(frame)
        lines.forEachIndexed { i, line ->
            text(
                canvas,
                line,
                if (landscape) w * .38f else cx,
                titleBaseline + i * titleStep,
                frame.titleSize,
                heading = true,
                leftAligned = landscape,
            )
        }
        val detailSize = frame.infoSize
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
        val progress = "${readingProgressText(
            current.progressPercent,
        )} · 累计 ${immersiveReadingDuration(current.readingSeconds)}"
        paint.textSize = detailSize
        val progressText = InkDraw.wrap(progress, paint, textWidth, 1).firstOrNull().orEmpty()
        text(
            canvas,
            progressText,
            if (landscape) frame.barBounds.left else barCenter,
            frame.barBounds.bottom + gap - detailMetrics.ascent,
            detailSize,
            leftAligned = landscape,
        )
        if (landscape) {
            drawVerticalStats(canvas, metrics, w, h)
        } else {
            drawStats(canvas, metrics, frame, cx, h, w * .92f)
        }
    }

    private fun drawVerticalStats(
        canvas: Canvas,
        metrics: ImmersiveReadingStats,
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
            val bounds = RectF(left, top, right, top + h * .21f)
            val gap = DesignRuntime.tokens.dpF(4f)
            val captionSize = DesignRuntime.tokens.textCaption
            paint.typeface = DesignRuntime.typeface()
            paint.textSize = captionSize
            val fm = paint.fontMetrics
            val lineHeight = fm.descent - fm.ascent
            val valueText = "${value ?: "—"}${if (value == null) "" else units[index]}"
            val rows =
                if (lineHeight * 2 + gap * 3 <= bounds.height()) {
                    listOf(valueText, labels[index])
                } else {
                    listOf("$valueText ${labels[index]}")
                }
            val contentHeight = rows.size * lineHeight + (rows.size - 1) * gap
            rows.forEachIndexed { row, content ->
                paint.textSize = captionSize
                val shown = InkDraw.wrap(content, paint, bounds.width(), 1).firstOrNull().orEmpty()
                val baseline = bounds.centerY() - contentHeight / 2 - fm.ascent + row * (lineHeight + gap)
                text(canvas, shown, bounds.left, baseline, captionSize, leftAligned = true)
            }
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
        val geometry = portraitGeometry(frame.coverCenterX * 2, h)
        val top = geometry.statsTop
        val bottom = geometry.statsBottom
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
            drawStatCard(canvas, value + units[i], labels[i], RectF(left, top, left + cardWidth, bottom), frame)
        }
    }

    private fun drawStatCard(
        canvas: Canvas,
        value: String,
        label: String,
        box: RectF,
        frame: WereadBoardFrame,
    ) {
        val padding = DesignRuntime.tokens.dpF(4f)
        val gap = DesignRuntime.tokens.dpF(3f)
        val valueSize = maxOf(frame.titleSize * .98f, DesignRuntime.tokens.textTitle)
        val labelSize = DesignRuntime.tokens.textCaption
        paint.typeface = DesignRuntime.typeface()
        paint.textSize = valueSize
        val valueWidth = paint.measureText(value)
        val valueMetrics = paint.fontMetrics
        paint.textSize = labelSize
        val labelWidth = paint.measureText(label)
        val labelMetrics = paint.fontMetrics
        val lineHeights = valueMetrics.descent - valueMetrics.ascent + labelMetrics.descent - labelMetrics.ascent
        val scale =
            minOf(
                1f,
                (box.width() - padding * 2).coerceAtLeast(1f) / maxOf(valueWidth, labelWidth, 1f),
                (box.height() - padding * 2 - gap).coerceAtLeast(1f) / lineHeights,
            )
        val top = box.centerY() - (lineHeights * scale + gap) / 2
        val valueBaseline = top - valueMetrics.ascent * scale
        val labelBaseline = valueBaseline + valueMetrics.descent * scale + gap - labelMetrics.ascent * scale
        text(canvas, value, box.centerX(), valueBaseline, valueSize * scale)
        text(canvas, label, box.centerX(), labelBaseline, labelSize * scale)
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

private fun readingFontMetrics(
    titleSize: Float,
    detailSize: Float = maxOf(titleSize * .64f, DesignRuntime.tokens.textCaption),
): Pair<Paint.FontMetrics, Paint.FontMetrics> {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    paint.textSize = titleSize
    val title = paint.fontMetrics
    paint.typeface = DesignRuntime.typeface()
    paint.textSize = detailSize
    return title to paint.fontMetrics
}

private fun readingBaselines(frame: WereadBoardFrame): Triple<Float, Float, Float> {
    val (title, detail) = readingFontMetrics(frame.titleSize, frame.infoSize)
    val gap = DesignRuntime.tokens.dpF(4f)
    return Triple(
        frame.titleTop - title.ascent,
        title.descent - title.ascent,
        frame.barBounds.top - gap - detail.descent,
    )
}

/** 竖屏绘制与点击共用一份阅读几何。 */
private data class PortraitReadingGeometry(
    val frame: WereadBoardFrame,
    val statsTop: Float,
    val statsBottom: Float,
)

private fun portraitLayout(
    w: Float,
    h: Float,
): WereadBoardFrame = portraitGeometry(w, h).frame

/** 统计紧随进度，剩余高度分配在内容组上下。 */
private fun portraitGeometry(
    w: Float,
    h: Float,
): PortraitReadingGeometry {
    val t = DesignRuntime.tokens
    val window = t.device
    val balance = immersivePortraitBalance(window.widthPx.toFloat(), window.heightPx.toFloat())
    val gap = t.dpF(4f)
    val coverGap = gap + window.heightPx * .009f * balance
    val initialTitle = minOf(w * .065f, h * .052f)
    val initialDetail = maxOf(initialTitle * .64f, t.textCaption)
    val (tm, dm) = readingFontMetrics(initialTitle, initialDetail)
    val statsPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = DesignRuntime.typeface()
            textSize = maxOf(initialTitle * .98f, t.textTitle)
        }
    val vm = statsPaint.fontMetrics
    statsPaint.textSize = t.textCaption
    val lm = statsPaint.fontMetrics
    val budget =
        portraitReadingBudget(
            h,
            minOf(h * .48f, w * .63f),
            2 * (tm.descent - tm.ascent + dm.descent - dm.ascent),
            vm.descent - vm.ascent + lm.descent - lm.ascent + t.dpF(11f),
            gap * 3 + coverGap + 4f,
            t.dpF(20f) + window.heightPx * .032f * balance,
            gap,
        )
    val titleSize = initialTitle * budget.textScale
    val detailSize = initialDetail * budget.textScale
    val (title, detail) = readingFontMetrics(titleSize, detailSize)
    val coverHeight = budget.coverHeight * (1f - .09f * balance)
    val saved = budget.coverHeight - coverHeight
    val shift = -minOf(window.heightPx * .0125f * balance, budget.leading)
    val coverTop = budget.leading + saved - shift
    val titleTop = coverTop + coverHeight + coverGap
    val barTop = titleTop + 2 * (title.descent - title.ascent) + detail.descent - detail.ascent + gap * 2
    val statsTop = barTop + 4f + gap + detail.descent - detail.ascent + budget.statsGap
    val frame =
        WereadBoardFrame.of(w, h, 0f).copy(
            coverArea = BoardBounds(0f, coverTop, w, titleTop),
            coverCenterX = w / 2,
            coverCenterY = coverTop + coverHeight / 2,
            coverHeight = coverHeight,
            coverWidth = coverHeight / 1.45f,
            expandedCoverRings = true,
            titleTop = titleTop,
            titleSize = titleSize,
            infoSize = detailSize,
            barBounds = BoardBounds(w * .22f, barTop, w * .78f, barTop + 4f),
        )
    return PortraitReadingGeometry(frame, statsTop, statsTop + budget.statsHeight)
}
