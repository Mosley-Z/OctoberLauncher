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
import kotlin.math.abs

/** Rightward reader on the current desktop; no persistent state or independent page. */
internal class LandscapeNoteOverlayView(
    private val invalidate: () -> Unit,
) {
    private var source: WidgetNoteOverlaySource? = null
    private var content: WidgetNoteContent? = null
    private val buttons = NoteOverlayButtons()
    private val bounds = RectF()
    private val body = RectF()
    private val availableBounds = RectF()
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val paper = Paint().apply { color = Color.WHITE }
    private var lines = emptyList<LandscapeNoteLine>()
    private var scroll = 0f
    private var unit = 1f
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var moved = false
    val visible: Boolean get() = source != null

    fun open(
        source: WidgetNoteOverlaySource,
        available: RectF,
    ): Boolean {
        val note = source.currentNoteContent()
        val p = note?.presentation
        val layout = p?.landscape
        if (note == null || p == null || layout == null) return false
        val right = available.right
        val canOpen =
            note.needsExpansion && right > available.left + layout.right * p.scale && layout.bottom > layout.top
        if (canOpen) {
            this.source = source
            content = null
            scroll = 0f
            update(note, available)
            invalidate()
        }
        return canOpen
    }

    fun close(): Boolean {
        val wasVisible = visible
        source = null
        content = null
        lines = emptyList()
        scroll = 0f
        if (wasVisible) invalidate()
        return wasVisible
    }

    fun draw(
        canvas: Canvas,
        available: RectF,
    ) {
        val note = source?.currentNoteContent()
        if (note?.presentation?.landscape == null) {
            close()
            return
        }
        if (!note.needsExpansion) {
            close()
            return
        }
        update(note, available)
        val save = canvas.save()
        canvas.clipRect(bounds)
        canvas.drawRect(bounds, paper)
        canvas.translate(bounds.left, bounds.top)
        source?.drawExpandedNoteCard(canvas, bounds.width().toInt(), bounds.height().toInt())
        canvas.translate(-bounds.left, -bounds.top)
        canvas.drawRect(body, paper)
        canvas.drawRect(body.left, body.bottom, bounds.right, bounds.bottom, paper)
        drawBody(canvas)
        drawFooter(canvas)
        canvas.restoreToCount(save)
    }

    private fun update(
        note: WidgetNoteContent,
        available: RectF,
    ) {
        val sameNote = content?.identity == note.identity && content?.body == note.body
        val sameLayout =
            content?.presentation?.landscape == note.presentation?.landscape && availableBounds == available
        if (sameNote && sameLayout) {
            return
        }
        val p = note.presentation
        val layout = p?.landscape
        if (p == null || layout == null) return
        if (content?.identity != note.identity) scroll = 0f
        content = note
        availableBounds.set(available)
        unit = layout.unit * p.scale
        bounds.set(
            available.left,
            available.top,
            available.right,
            available.bottom,
        )
        body.set(
            available.left + layout.left * p.scale,
            available.top + layout.top * p.scale,
            bounds.right - 14f * unit,
            available.top + layout.bottom * p.scale,
        )
        lines = LandscapeNoteFlow.layout(layout, p.scale, body.width())
        scroll = scroll.coerceIn(0f, (LandscapeNoteFlow.height(lines) - body.height()).coerceAtLeast(0f))
    }

    private fun drawBody(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipRect(body)
        lines.forEach { line ->
            paint.color = Color.BLACK
            paint.typeface = if (line.idea) Typeface.DEFAULT else Typeface.SERIF
            paint.textSize = line.size
            val x = body.left + if (line.idea) 10f * unit else 0f
            val y = body.top + line.top - scroll
            if (line.idea) {
                paint.color = Color.GRAY
                paint.strokeWidth = 2f * unit
                canvas.drawLine(body.left, y, body.left, y + 24f * unit, paint)
                paint.color = Color.BLACK
            }
            canvas.drawText(line.value, x, y - paint.fontMetrics.ascent, paint)
        }
        canvas.restoreToCount(save)
    }

    private fun drawFooter(canvas: Canvas) {
        buttons.draw(
            canvas,
            RectF(body.left, body.bottom + 19f * unit, body.right, bounds.bottom - 8f * unit),
            unit,
            19f * unit,
        )
    }

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
                if (abs(event.x - downX) > 8f * unit || abs(event.y - downY) > 8f * unit) moved = true
                if (body.contains(downX, downY)) {
                    scroll =
                        (scroll + lastY - event.y).coerceIn(
                            0f,
                            (LandscapeNoteFlow.height(lines) - body.height()).coerceAtLeast(0f),
                        )
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
}
