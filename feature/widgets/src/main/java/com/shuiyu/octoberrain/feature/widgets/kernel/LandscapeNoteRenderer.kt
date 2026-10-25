package com.shuiyu.octoberrain.feature.widgets.kernel

import android.graphics.Color
import android.graphics.Typeface
import com.inkread.weekread.kernel.CardState
import com.shuiyu.octoberrain.core.widget.WidgetLandscapeNotePresentation
import com.shuiyu.octoberrain.core.widget.WidgetNoteText
import com.shuiyu.octoberrain.core.widget.noteChapterLabel

/** Approved C card: keep its existing typography and offer right expansion only at an overflow tail. */
internal object LandscapeNoteRenderer {
    fun draw(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        state: CardState,
    ) {
        state.expandBox().setEmpty()
        buttons(ink, g, state)
        val note = state.note()
        if (note == null) {
            ink.text(
                state.noteHint().orEmpty().ifBlank { state.phMain().orEmpty() },
                g.body.left,
                g.body.top + 30f * g.unit,
                17f,
                g.body.width,
            )
        } else {
            ink.text(
                note.title.orEmpty(),
                g.body.left,
                g.meta.top + 3f * g.unit,
                18f,
                g.body.width,
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
            )
            val chapter = noteChapterLabel(note.title, note.chapterTitle)
            if (chapter.isNotEmpty()) {
                ink.text(
                    chapter,
                    g.body.left,
                    g.meta.top + 31f * g.unit,
                    15f,
                    g.body.width,
                    color = Color.DKGRAY,
                )
            }
            drawBody(ink, g, state)
        }
        state.setNoteScrollMax(0f)
        state.setNoteViewH(0f)
        state.setNoteContentH(0f)
        state.setNoteScrollY(0f)
    }

    /** Coordinates come from the last real C frame, including its actual minimum-touch gutter. */
    fun presentation(
        g: LandscapeCardGeometry,
        state: CardState,
    ): WidgetLandscapeNotePresentation? {
        val note = state.note()
        if (note == null) return null
        return WidgetLandscapeNotePresentation(
            g.body.left,
            bodyTop(g, state),
            g.body.right,
            g.body.bottom,
            note.markText.orEmpty(),
            note.ideaText.orEmpty(),
            maxOf(22f, state.noteCardSize().toFloat()),
            g.unit,
        )
    }

    private fun bodyTop(
        g: LandscapeCardGeometry,
        state: CardState,
    ): Float {
        val chapter =
            if (noteChapterLabel(state.note()?.title, state.note()?.chapterTitle).isEmpty()) {
                0f
            } else {
                23f *
                    g.unit
            }
        return g.meta.top + 31f * g.unit + chapter + 14f * g.unit
    }

    private fun drawBody(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        state: CardState,
    ) {
        val note = state.note() ?: return
        val u = g.unit
        val viewport = LandscapeRect(g.body.left, bodyTop(g, state), g.body.right, g.body.bottom)
        val size = maxOf(22f, state.noteCardSize().toFloat())
        val quote = passage(ink, note.markText, viewport.width, size, Typeface.SERIF)
        val idea =
            passage(
                ink,
                note.ideaText
                    ?.takeIf {
                        it.isNotBlank()
                    }?.let { "想法：$it" },
                viewport.width - 12f * u,
                15f,
                Typeface.DEFAULT,
            )
        val available = viewport.height.coerceAtLeast(0f)
        val reserve = if (idea.isEmpty()) 0f else minOf(48f * u, available * .4f)
        val gap = if (quote.isNotEmpty() && idea.isNotEmpty()) 18f * u else 0f
        val count =
            ((available - reserve - gap).coerceAtLeast(0f) / (size * 1.7f * u))
                .toInt()
                .coerceAtMost(quote.size)
        val ideaTop = viewport.top + count * size * 1.7f * u + if (count > 0) gap else 0f
        val ideaCount = ((viewport.bottom - ideaTop) / (24f * u)).toInt().coerceIn(0, idea.size)
        val entry = expansionTarget(count, quote.size, ideaCount, idea.size)
        val save = ink.canvas.save()
        ink.canvas.clipRect(viewport.androidRect())
        drawLines(
            ink,
            NoteLines(
                quote,
                count,
                viewport.left,
                viewport.top,
                viewport.width,
                size,
                size * 1.7f,
                Typeface.SERIF,
                entry == 1,
            ),
            state,
        )
        drawIdea(ink, viewport, idea, ideaCount, ideaTop, entry == 2, state)
        ink.canvas.restoreToCount(save)
    }

    private fun drawIdea(
        ink: LandscapeInk,
        viewport: LandscapeRect,
        idea: List<String>,
        ideaCount: Int,
        ideaTop: Float,
        entry: Boolean,
        state: CardState,
    ) {
        val u = ink.unit
        if (ideaCount > 0) {
            ink.rule(viewport.left, ideaTop, viewport.left, ideaTop + ideaCount * 24f * u, Color.GRAY, 2f)
            drawLines(
                ink,
                NoteLines(
                    idea,
                    ideaCount,
                    viewport.left + 10f * u,
                    ideaTop,
                    viewport.width - 10f * u,
                    15f,
                    24f,
                    Typeface.DEFAULT,
                    entry,
                ),
                state,
            )
        }
    }

    private fun passage(
        ink: LandscapeInk,
        text: String?,
        width: Float,
        size: Float,
        face: Typeface,
    ): List<String> = if (text.isNullOrBlank()) emptyList() else ink.lines(text, width, size, face)

    /** Prefer the last visible truncated passage; otherwise expose a fully hidden passage from a visible tail. */
    private fun expansionTarget(
        quoteCount: Int,
        quoteTotal: Int,
        ideaCount: Int,
        ideaTotal: Int,
    ): Int {
        val allVisible = quoteCount >= quoteTotal && ideaCount >= ideaTotal
        if (allVisible) return 0
        return when {
            ideaCount in 1 until ideaTotal -> 2
            quoteCount in 1 until quoteTotal -> 1
            ideaCount > 0 -> 2
            quoteCount > 0 -> 1
            else -> 0
        }
    }

    private fun drawLines(
        ink: LandscapeInk,
        block: NoteLines,
        state: CardState,
    ) {
        block.lines.take(block.count).forEachIndexed { index, line ->
            val top = block.top + index * block.lineHeight * ink.unit
            val lastVisible = index == block.count - 1
            val truncated = lastVisible && block.count < block.lines.size
            if (lastVisible && block.entry) {
                inline(ink, block.copy(top = top), line, state)
            } else {
                val shown = if (truncated) WidgetNoteText.dropLast(line) + "…" else line
                ink.text(shown, block.left, top, block.size, block.width, block.face)
            }
        }
    }

    private fun inline(
        ink: LandscapeInk,
        block: NoteLines,
        line: String,
        state: CardState,
    ) {
        val label = "… 展开▶"
        val paint = ink.paint
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 11.5f * ink.unit
        val labelWidth = paint.measureText(label)
        val labelAscent = paint.fontMetrics.ascent
        val labelDescent = paint.fontMetrics.descent
        if (labelWidth > block.width) {
            ink.text(line, block.left, block.top, block.size, block.width, block.face)
            return
        }
        paint.typeface = block.face
        paint.textSize = block.size * ink.unit
        val count = paint.breakText(line, true, block.width - labelWidth, null)
        val prefix = line.take(WidgetNoteText.fitLength(line, count, wrap = false)).trimEnd()
        val x = block.left + paint.measureText(prefix)
        val baseline = block.top - paint.fontMetrics.ascent
        ink.text(prefix, block.left, block.top, block.size, block.width - labelWidth, block.face)
        ink.text(label, x, baseline + labelAscent, 11.5f, labelWidth + .01f)
        state.expandBox().set(x, baseline + labelAscent, x + labelWidth, baseline + labelDescent)
    }

    private fun buttons(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        state: CardState,
    ) {
        val top = g.footer.first().top - 9f * g.unit
        ink.rule(g.body.left, top, g.body.right, top, Color.GRAY)
        val enabled = state.note() != null
        g.footer.forEachIndexed { index, rect ->
            val color = if (enabled) Color.DKGRAY else Color.LTGRAY
            val visualHeight = minOf(34f * g.unit, rect.height)
            val visual =
                android.graphics.RectF(
                    rect.left,
                    rect.centerY - visualHeight / 2f,
                    rect.right,
                    rect.centerY + visualHeight / 2f,
                )
            ink.paint.style = android.graphics.Paint.Style.STROKE
            ink.paint.strokeWidth = g.unit
            ink.paint.color = color
            ink.canvas.drawRoundRect(visual, 6f * g.unit, 6f * g.unit, ink.paint)
            ink.centered(if (index == 0) "上一条" else "换一条", rect, 19f, color)
        }
        if (enabled) {
            state.prevBox().set(g.footer[0].androidRect())
            state.openBox().set(g.footer[1].androidRect())
        } else {
            state.prevBox().setEmpty()
            state.openBox().setEmpty()
        }
    }
}

private data class NoteLines(
    val lines: List<String>,
    val count: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val size: Float,
    val lineHeight: Float,
    val face: Typeface,
    val entry: Boolean,
)
