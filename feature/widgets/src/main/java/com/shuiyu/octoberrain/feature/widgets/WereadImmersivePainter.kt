package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.shuiyu.octoberrain.core.common.ImmersiveLandscapeGeometry
import com.shuiyu.octoberrain.core.common.immersivePortraitBalance
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkTokens

/** 沉浸阅读区：书堆是主角，时间和应用栏交给桌面宿主。 */
internal class WereadImmersivePainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var geometry: ImmersiveLandscapeGeometry? = null
    var readingTitle: String = ""

    fun statisticsFit(
        candidate: ImmersiveLandscapeGeometry,
        metrics: ImmersiveReadingStats,
    ): Boolean =
        ImmersiveBookPainter(paint, candidate).statisticsFit(
            candidate,
            listOf(metrics.todayMinutes, metrics.finishedBooks, metrics.streakDays),
            listOf("分钟", "本", "天"),
            listOf("今日阅读", "读完书籍", "连续阅读"),
        )

    fun layout(
        width: Int,
        height: Int,
        landscape: Boolean = width > height * 1.35f,
    ): WereadBoardFrame {
        if (!landscape) return portraitLayout(width.toFloat(), height.toFloat())
        val shared = geometry
        return if (shared == null || shared.weight == 0f) {
            landscapeLayout(width.toFloat(), height.toFloat())
        } else {
            val old = landscapeLayout(shared.originalBook.width, shared.originalBook.height)
            landscapeFrame(old, shared)
        }
    }

    private fun landscapeLayout(
        w: Float,
        h: Float,
    ): WereadBoardFrame {
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

    private fun landscapeFrame(
        old: WereadBoardFrame,
        shared: ImmersiveLandscapeGeometry,
    ): WereadBoardFrame {
        val dx = shared.originalBook.left - shared.book.left
        val dy = shared.originalBook.top - shared.book.top
        val area = shared.coverArea
        val targetTitle = maxOf(DesignRuntime.tokens.textTitle, glyphSize(paint, shared.window.height * .051f, true))
        val targetInfo = maxOf(DesignRuntime.tokens.textCaption, glyphSize(paint, shared.window.height * .025f))
        val titleSize = shared.blend(old.titleSize, targetTitle)
        val infoSize = shared.blend(old.infoSize, targetInfo)
        val (titleMetrics, detailMetrics) = readingFontMetrics(titleSize, infoSize)
        val progressSize = maxOf(infoSize, glyphSize(paint, shared.window.height * .028f))
        paint.textSize = progressSize
        val progressMetrics = paint.fontMetrics
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        paint.textSize = titleSize
        val titleLines =
            InkDraw.wrap(immersiveWorkTitle(readingTitle), paint, shared.progress.width, 2).size.coerceAtLeast(1)
        val budget =
            shared.readingBudget(
                titleMetrics.descent - titleMetrics.ascent,
                detailMetrics.descent - detailMetrics.ascent,
                progressMetrics.descent - progressMetrics.ascent,
                DesignRuntime.tokens.dpF(4f),
                titleLines,
            )
        val cover = budget.cover
        val blended =
            old.copy(
                sideCoverWeight = shared.weight,
                coverArea =
                    BoardBounds(
                        shared.blend(old.coverArea.left + dx, area.left),
                        shared.blend(old.coverArea.top + dy, cover.top),
                        shared.blend(old.coverArea.right + dx, area.right),
                        shared.blend(old.coverArea.bottom + dy, area.bottom),
                    ),
                coverCenterX = shared.blend(old.coverCenterX + dx, cover.centerX),
                coverCenterY = shared.blend(old.coverCenterY + dy, cover.centerY),
                coverWidth = shared.blend(old.coverWidth, cover.width),
                coverHeight = shared.blend(old.coverHeight, cover.height),
                titleTop = shared.blend(old.titleTop + dy, shared.titleTop),
                titleSize = titleSize,
                infoSize = infoSize,
                barBounds =
                    BoardBounds(
                        shared.blend(old.barBounds.left + dx, shared.titleLeft),
                        shared.blend(old.barBounds.top + dy, budget.barY),
                        shared.blend(old.barBounds.right + dx, shared.progress.right),
                        shared.blend(old.barBounds.bottom + dy, budget.barY + 4f),
                    ),
            )
        return budgetLandscapeFrame(blended, shared)
    }

    private fun budgetLandscapeFrame(
        blended: WereadBoardFrame,
        shared: ImmersiveLandscapeGeometry,
    ): WereadBoardFrame {
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        paint.textSize = blended.titleSize
        val actualLines =
            InkDraw
                .wrap(
                    immersiveWorkTitle(readingTitle),
                    paint,
                    blended.barBounds.width.coerceAtLeast(1f),
                    2,
                ).size
                .coerceAtLeast(1)
        val (titleMetrics, detailMetrics) = readingFontMetrics(blended.titleSize, blended.infoSize)
        paint.typeface = DesignRuntime.typeface()
        paint.textSize =
            shared.blend(
                blended.infoSize,
                maxOf(blended.infoSize, glyphSize(paint, shared.window.height * .028f)),
            )
        val progressMetrics = paint.fontMetrics
        val gap = DesignRuntime.tokens.dpF(4f)
        val safeBar =
            maxOf(
                blended.barBounds.top,
                shared.blend(blended.barBounds.top, shared.authorBaseline + detailMetrics.descent + gap),
                blended.titleTop + actualLines * (titleMetrics.descent - titleMetrics.ascent) +
                    detailMetrics.descent - detailMetrics.ascent + gap * 2,
            )
        val top =
            maxOf(
                blended.coverCenterY - blended.coverHeight / 2,
                safeBar + 4f + gap * 2 + progressMetrics.descent - progressMetrics.ascent,
            )
        val coverHeight = minOf(blended.coverHeight, (blended.coverArea.bottom - top).coerceAtLeast(0f))
        return blended.copy(
            barBounds = blended.barBounds.copy(top = safeBar, bottom = safeBar + 4f),
            coverArea = blended.coverArea.copy(top = top),
            coverCenterY = top + coverHeight / 2,
            coverHeight = coverHeight,
            coverWidth = coverHeight / 1.45f,
        )
    }

    fun refreshBounds(
        width: Int,
        height: Int,
        landscape: Boolean,
    ): BoardBounds {
        val shared = geometry?.takeIf { landscape && it.weight > 0f }
        val size = DesignRuntime.tokens.dpF(44f)
        if (shared != null) {
            val old = landscapeLayout(shared.originalBook.width, shared.originalBook.height)
            val cx =
                shared.blend(
                    shared.originalBook.width * .98f - size / 2 + shared.originalBook.left - shared.book.left,
                    shared.refreshX,
                )
            val cy = landscapeFrame(old, shared).barBounds.centerY - 2f * shared.weight
            return BoardBounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        }
        val frame = layout(width, height, landscape)
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
        val renderer = ImmersiveBookPainter(paint, geometry)
        renderer.drawCovers(canvas, scene, frame)
        renderer.drawReadingInfo(canvas, scene, frame, width.toFloat(), height.toFloat(), landscape, metrics)
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
}

private fun drawEnhancedVerticalStat(
    paint: Paint,
    bounds: RectF,
    value: Int?,
    unit: String,
    label: String,
    gap: Float,
    tokens: InkTokens,
    typeface: Typeface,
    drawText: (String, Float, Float, Float) -> Unit,
): Boolean {
    var drawn = false
    if (bounds.height() / tokens.density > 90f) {
        val valueText = "${value ?: "—"}${if (value == null) "" else unit}"
        val valueSize = tokens.textTitle
        val labelSize = tokens.textBody
        paint.typeface = typeface
        paint.textSize = valueSize
        val valueWidth = paint.measureText(valueText)
        val valueMetrics = paint.fontMetrics
        paint.textSize = labelSize
        val labelWidth = paint.measureText(label)
        val labelMetrics = paint.fontMetrics
        val contentHeight =
            valueMetrics.descent - valueMetrics.ascent + gap +
                labelMetrics.descent - labelMetrics.ascent
        if (
            immersiveStatsTypographyFits(
                bounds.height() / tokens.density,
                bounds.width(),
                bounds.height(),
                maxOf(valueWidth, labelWidth),
                contentHeight,
            )
        ) {
            val valueBaseline = bounds.centerY() - contentHeight / 2 - valueMetrics.ascent
            val labelBaseline = valueBaseline + valueMetrics.descent + gap - labelMetrics.ascent
            drawText(valueText, bounds.left, valueBaseline, valueSize)
            drawText(label, bounds.left, labelBaseline, labelSize)
            drawn = true
        }
    }
    return drawn
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

private class ImmersiveBookPainter(
    private val paint: Paint,
    private val geometry: ImmersiveLandscapeGeometry?,
) {
    fun drawCovers(
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

    fun drawReadingInfo(
        canvas: Canvas,
        scene: WereadBoardScene,
        frame: WereadBoardFrame,
        w: Float,
        h: Float,
        landscape: Boolean,
        metrics: ImmersiveReadingStats,
    ) {
        val current = scene.current ?: return
        val shared = geometry?.takeIf { landscape && it.weight > 0f }
        val cx = if (landscape) w * .68f else w / 2
        val titleLeft = if (shared != null) frame.barBounds.left else w * .38f
        val textWidth = if (landscape) frame.barBounds.width.coerceAtLeast(1f) else w * .88f
        val gap = DesignRuntime.tokens.dpF(4f)
        paint.textSize = frame.titleSize
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val lines = InkDraw.wrap(immersiveWorkTitle(current.title), paint, textWidth, 2)
        val (titleBaseline, titleStep, oldAuthorY) = readingBaselines(frame)
        val authorY =
            if (shared == null) {
                oldAuthorY
            } else {
                val (titleMetrics, detailMetrics) = readingFontMetrics(frame.titleSize, frame.infoSize)
                maxOf(
                    shared.blend(oldAuthorY, shared.authorBaseline),
                    frame.titleTop + lines.size * (titleMetrics.descent - titleMetrics.ascent) +
                        gap - detailMetrics.ascent,
                )
            }
        lines.forEachIndexed { i, line ->
            text(
                canvas,
                line,
                if (landscape) titleLeft else cx,
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
        text(canvas, author, if (landscape) titleLeft else cx, authorY, detailSize, leftAligned = landscape)
        drawProgress(canvas, current, frame, shared, landscape, textWidth)
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
        val shared = geometry?.takeIf { it.weight > 0f }
        val originalW = geometry?.originalBook?.width ?: w
        val originalH = geometry?.originalBook?.height ?: h
        val dx = geometry?.let { it.originalBook.left - it.book.left } ?: 0f
        val dy = geometry?.let { it.originalBook.top - it.book.top } ?: 0f
        val left = originalW * .01f + 20f + dx
        val right = originalW * .26f + 20f + dx
        values.forEachIndexed { index, value ->
            val top = originalH * (.35f + index * .21f) + dy
            val lineLeft = shared?.let { it.blend(left, it.statsLeft) } ?: left
            val lineRight = shared?.let { it.blend(right, it.statsRight) } ?: right
            val lineY = shared?.let { it.blend(top, it.dividerYs[index]) } ?: top
            paint.color = InkColors.INK_3
            paint.strokeWidth = 1f
            canvas.drawLine(lineLeft, lineY, lineRight, lineY, paint)
            val bounds = RectF(left, top, right, top + originalH * .21f)
            drawVerticalStatContent(bounds, value, units[index], labels[index]) { content, x, y, size, isLabel ->
                drawVerticalStatText(canvas, shared, index, isLabel, content, x, y, size)
            }
        }
    }

    fun statisticsFit(
        shared: ImmersiveLandscapeGeometry,
        values: List<Int?>,
        units: List<String>,
        labels: List<String>,
    ): Boolean {
        val t = DesignRuntime.tokens
        val defaults = InkTokens(t.device)
        val gap = t.dpF(4f)
        paint.typeface = DesignRuntime.typeface()
        return values.indices.all { index ->
            val value = "${values[index] ?: "—"}${if (values[index] == null) "" else units[index]}"
            val valueSize =
                ImmersiveLandscapeGeometry.magnifiedGlyphSize(
                    glyphSize(paint, shared.window.height * .037f),
                    t.textTitle,
                    defaults.textTitle,
                )
            val labelSize =
                ImmersiveLandscapeGeometry.magnifiedGlyphSize(
                    glyphSize(paint, shared.window.height * .026f),
                    t.textBody,
                    defaults.textBody,
                )
            paint.textSize = valueSize
            val valueMetrics = paint.fontMetrics
            val valueWidth = paint.measureText(value)
            paint.textSize = labelSize
            val labelMetrics = paint.fontMetrics
            val bottom = shared.dividerYs.getOrNull(index + 1) ?: shared.bookY(.94f)
            val labelY =
                maxOf(
                    shared.labelYs[index],
                    shared.valueYs[index] + valueMetrics.descent + gap - labelMetrics.ascent,
                )
            valueWidth <= shared.statsRight - shared.statsLeft &&
                paint.measureText(labels[index]) <= shared.statsRight - shared.statsLeft &&
                shared.valueYs[index] + valueMetrics.ascent >= shared.dividerYs[index] + gap &&
                labelY + labelMetrics.descent + gap <= bottom
        }
    }

    private fun drawVerticalStatText(
        canvas: Canvas,
        shared: ImmersiveLandscapeGeometry?,
        index: Int,
        label: Boolean,
        content: String,
        x: Float,
        y: Float,
        size: Float,
    ) {
        if (shared == null) {
            text(canvas, content, x, y, size, leftAligned = true)
            return
        }
        val tokens = DesignRuntime.tokens
        val defaults = InkTokens(tokens.device)
        val target =
            ImmersiveLandscapeGeometry.magnifiedGlyphSize(
                glyphSize(paint, shared.window.height * if (label) .026f else .037f),
                if (label) tokens.textBody else tokens.textTitle,
                if (label) defaults.textBody else defaults.textTitle,
            )
        val drawSize = shared.blend(size, target)
        paint.textSize = drawSize
        val oldWidth = shared.originalBook.width * .25f
        val width = shared.blend(oldWidth, shared.statsRight - shared.statsLeft)
        val shown = InkDraw.wrap(content, paint, width.coerceAtLeast(1f), 1).firstOrNull().orEmpty()
        var targetY = if (label) shared.labelYs[index] else shared.valueYs[index]
        if (label) {
            val labelMetrics = paint.fontMetrics
            paint.textSize =
                ImmersiveLandscapeGeometry.magnifiedGlyphSize(
                    glyphSize(paint, shared.window.height * .037f),
                    tokens.textTitle,
                    defaults.textTitle,
                )
            val valueMetrics = paint.fontMetrics
            targetY =
                maxOf(
                    targetY,
                    shared.valueYs[index] + valueMetrics.descent + tokens.dpF(4f) - labelMetrics.ascent,
                )
        }
        val drawY = shared.blend(y, targetY)
        text(canvas, shown, shared.blend(x, shared.statsLeft), drawY, drawSize, leftAligned = true)
    }

    private fun drawVerticalStatContent(
        bounds: RectF,
        value: Int?,
        unit: String,
        label: String,
        drawText: (String, Float, Float, Float, Boolean) -> Unit,
    ) {
        val gap = DesignRuntime.tokens.dpF(4f)
        val enhanced =
            drawEnhancedVerticalStat(
                paint,
                bounds,
                value,
                unit,
                label,
                gap,
                DesignRuntime.tokens,
                DesignRuntime.typeface(),
            ) { content, x, y, size -> drawText(content, x, y, size, content == label) }
        if (enhanced) return
        val captionSize = DesignRuntime.tokens.textCaption
        paint.typeface = DesignRuntime.typeface()
        paint.textSize = captionSize
        val fm = paint.fontMetrics
        val lineHeight = fm.descent - fm.ascent
        val valueText = "${value ?: "—"}${if (value == null) "" else unit}"
        val rows =
            if (lineHeight * 2 + gap * 3 <= bounds.height()) {
                listOf(valueText, label)
            } else {
                listOf("$valueText $label")
            }
        val contentHeight = rows.size * lineHeight + (rows.size - 1) * gap
        rows.forEachIndexed { row, content ->
            paint.textSize = captionSize
            val shown = InkDraw.wrap(content, paint, bounds.width(), 1).firstOrNull().orEmpty()
            val baseline = bounds.centerY() - contentHeight / 2 - fm.ascent + row * (lineHeight + gap)
            drawText(shown, bounds.left, baseline, captionSize, row > 0)
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

    private fun drawProgress(
        canvas: Canvas,
        current: WereadBookCard,
        frame: WereadBoardFrame,
        shared: ImmersiveLandscapeGeometry?,
        landscape: Boolean,
        textWidth: Float,
    ) {
        val gap = DesignRuntime.tokens.dpF(4f)
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
        val progressSize =
            shared?.let {
                it.blend(frame.infoSize, maxOf(frame.infoSize, glyphSize(paint, it.window.height * .028f)))
            } ?: frame.infoSize
        paint.textSize = progressSize
        val progressMetrics = paint.fontMetrics
        val progressText = InkDraw.wrap(progress, paint, textWidth, 1).firstOrNull().orEmpty()
        text(
            canvas,
            progressText,
            if (landscape) frame.barBounds.left else barCenter,
            frame.barBounds.bottom + gap - progressMetrics.ascent,
            progressSize,
            leftAligned = landscape,
        )
    }
}

private fun glyphSize(
    paint: Paint,
    height: Float,
    heading: Boolean = false,
): Float {
    paint.typeface = if (heading) Typeface.create(Typeface.SERIF, Typeface.BOLD) else DesignRuntime.typeface()
    paint.textSize = 100f
    val bounds = Rect()
    paint.getTextBounds("国", 0, 1, bounds)
    return height * 100f / bounds.height().coerceAtLeast(1)
}
