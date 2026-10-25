package com.shuiyu.octoberrain.feature.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.view.MotionEvent
import com.shuiyu.octoberrain.core.widget.WidgetNoteContent
import com.shuiyu.octoberrain.core.widget.WidgetNoteOverlaySource
import com.shuiyu.octoberrain.core.widget.WidgetNotePresentation
import kotlin.math.abs
import kotlin.math.floor

/** Activity 内临时阅读层，由 Home 在所有应用之后绘制；不保存正文或滚动状态。 */
internal class NoteOverlayView(
    private val invalidate: () -> Unit,
) {
    private var source: WidgetNoteOverlaySource? = null
    private var content: WidgetNoteContent? = null
    private val buttons = NoteOverlayButtons()
    private var metadataBlock = 0f
    private val bounds = RectF()
    private val bodyBounds = RectF()
    private val availableBounds = RectF()
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val paper = Paint().apply { color = Color.WHITE }
    private var contentHeight = 0f
    private var scroll = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var padding = 0f
    private var barHeight = 0f
    val visible: Boolean get() = source != null

    /** 几何不足三行正文时拒绝覆盖，由宿主打开独立本记页。 */
    fun open(
        source: WidgetNoteOverlaySource,
        bounds: RectF,
    ): Boolean {
        val note = source.currentNoteContent()
        val canOpen =
            note?.let {
                val size = it.textSizePx.coerceAtLeast(1f)
                it.needsExpansion &&
                    it.presentation != null &&
                    bounds.width() >= size * MIN_WIDTH_LINES &&
                    bounds.height() >= size * MIN_HEIGHT_LINES
            } == true
        if (canOpen && note != null) {
            this.source = source
            content = null
            scroll = 0f
            update(note, bounds)
            invalidate()
        }
        return canOpen
    }

    /** 返回、离开、换页、换组件和尺寸变化都可调用，关闭后重画背景。 */
    fun close(): Boolean {
        if (!visible) return false
        source = null
        content = null
        scroll = 0f
        invalidate()
        return true
    }

    fun draw(
        canvas: Canvas,
        available: RectF,
    ) {
        val note =
            source?.currentNoteContent() ?: run {
                close()
                return
            }
        val presentation = note.presentation
        if (presentation == null || !note.needsExpansion) {
            close()
            return
        }
        update(note, available)
        canvas.drawRect(bounds, paper)
        drawBody(canvas, available, note, presentation)
        drawFooter(canvas, available, note, presentation)
    }

    private fun drawBody(
        canvas: Canvas,
        available: RectF,
        note: WidgetNoteContent,
        presentation: WidgetNotePresentation,
    ) {
        val nativeScale = presentation.scale
        val x = available.left + presentation.textX * nativeScale
        val lineHeight = presentation.lineHeight * nativeScale
        var baselineY = available.top + presentation.firstBaseline * nativeScale + metadataBlock - scroll
        paint.textSize = note.textSizePx
        paint.typeface = Typeface.SERIF
        val save = canvas.save()
        canvas.clipRect(bodyBounds)
        if (presentation.quote.isNotEmpty()) {
            val size = paint.textSize
            paint.textSize = 30f * nativeScale
            paint.color = 0xffa8a8a8.toInt()
            canvas.drawText(
                "“",
                available.left + available.width() * 0.05f,
                baselineY - size + paint.textSize * 0.78f,
                paint,
            )
            paint.textSize = size
            paint.color = Color.BLACK
        }
        presentation.quote.forEach { line ->
            canvas.drawText(line, x, baselineY, paint)
            baselineY += lineHeight
        }
        if (presentation.idea.isNotEmpty()) {
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 11f * nativeScale
            paint.color = 0xff3c3c3c.toInt()
            canvas.drawText("想法", x, baselineY - note.textSizePx + lineHeight * 0.75f, paint)
            baselineY += presentation.ideaTagBlock * nativeScale
            paint.typeface = Typeface.SERIF
            paint.textSize = note.textSizePx
            paint.color = Color.BLACK
            presentation.idea.forEach { line ->
                canvas.drawText(line, x, baselineY, paint)
                baselineY += lineHeight
            }
        }
        canvas.restoreToCount(save)
    }

    private fun drawFooter(
        canvas: Canvas,
        available: RectF,
        note: WidgetNoteContent,
        presentation: WidgetNotePresentation,
    ) {
        val nativeScale = presentation.scale
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 17f * nativeScale
        val x = available.left + available.width() * .05f
        val textWidth = available.width() * .9f
        canvas.drawText(
            android.text.TextUtils
                .ellipsize(
                    note.title,
                    paint,
                    textWidth,
                    android.text.TextUtils.TruncateAt.END,
                ).toString(),
            x,
            bounds.top + 18f * nativeScale,
            paint,
        )
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 14f * nativeScale
        if (note.displayChapter.isNotEmpty()) {
            canvas.drawText(
                android.text.TextUtils
                    .ellipsize(
                        note.displayChapter,
                        paint,
                        textWidth,
                        android.text.TextUtils.TruncateAt.END,
                    ).toString(),
                x,
                bounds.top + 37f * nativeScale,
                paint,
            )
        }
        canvas.drawRect(bounds.left, bodyBounds.bottom, bounds.right, bounds.bottom, paper)
        buttons.draw(
            canvas,
            RectF(
                x,
                bounds.bottom - barHeight + 9f * nativeScale,
                bounds.right - available.width() * .05f,
                bounds.bottom - 4f * nativeScale,
            ),
            nativeScale,
            presentation.buttonTextSize * nativeScale,
            radius = 0f,
        )
    }

    /** 展开期间消费整个触摸序列，包括覆盖区外，应用入口不可穿透。 */
    fun onTouchEvent(event: MotionEvent): Boolean {
        if (!visible) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastY = event.y
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.y - downY) > padding / 2f || abs(event.x - downX) > padding / 2f) moved = true
                if (bodyBounds.contains(downX, downY)) {
                    scroll = (scroll + lastY - event.y).coerceIn(0f, maxScroll())
                    invalidate()
                }
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> finishTouch(event)
            MotionEvent.ACTION_CANCEL -> moved = false
        }
        return true
    }

    private fun finishTouch(event: MotionEvent) {
        val index = buttons.hit(event.x, event.y)
        if (!moved && index >= 0 && index == buttons.hit(downX, downY)) {
            when (index) {
                0 -> source?.moveOverlayNote(true)
                1 -> source?.moveOverlayNote(false)
                else -> close()
            }
            invalidate()
        }
    }

    private fun sameContent(note: WidgetNoteContent): Boolean {
        val sameHeader = content?.title == note.title && content?.chapter == note.chapter
        val sameBody = content?.body == note.body && content?.textSizePx == note.textSizePx
        return content?.identity == note.identity && sameHeader && sameBody
    }

    private fun update(
        note: WidgetNoteContent,
        available: RectF,
    ) {
        val sameIdentity = content?.identity == note.identity
        if (sameContent(note) && availableBounds == available) return
        if (!sameIdentity) scroll = 0f
        content = note
        availableBounds.set(available)
        val presentation = note.presentation ?: return
        paint.textSize = note.textSizePx.coerceAtLeast(1f)
        padding = paint.textSize
        barHeight = maxOf(paint.textSize * BAR_LINES, 58f * presentation.scale)
        metadataBlock = (if (note.displayChapter.isEmpty()) 24f else 46f) * presentation.scale
        val bodyTop = available.top + presentation.firstBaseline * presentation.scale - note.textSizePx
        val lineHeight = presentation.lineHeight * presentation.scale
        val lines = presentation.quote.size + presentation.idea.size
        val tagHeight = if (presentation.idea.isEmpty()) 0f else presentation.ideaTagBlock * presentation.scale
        val bodyHeight = lines * lineHeight + tagHeight + paint.descent()
        bounds.set(available.left, bodyTop, available.right, available.bottom)
        val firstBaseline = available.top + presentation.firstBaseline * presentation.scale + metadataBlock
        val lastLine =
            floor((bounds.bottom - barHeight - firstBaseline - paint.descent()) / lineHeight)
                .coerceAtLeast(0f)
        val bodyBottom = firstBaseline + lastLine * lineHeight + paint.descent()
        bodyBounds.set(bounds.left, bodyTop + metadataBlock - 2f * presentation.scale, bounds.right, bodyBottom)
        contentHeight = bodyHeight
        scroll = scroll.coerceIn(0f, maxScroll())
    }

    private fun maxScroll(): Float = (contentHeight - bodyBounds.height()).coerceAtLeast(0f)

    private companion object {
        const val MIN_WIDTH_LINES = 6f
        const val MIN_HEIGHT_LINES = 11f
        const val BAR_LINES = 2.6f
    }
}
