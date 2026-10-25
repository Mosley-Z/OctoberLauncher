package com.shuiyu.octoberrain.feature.home

import android.graphics.Paint
import android.graphics.Typeface
import com.shuiyu.octoberrain.core.widget.WidgetLandscapeNotePresentation
import com.shuiyu.octoberrain.core.widget.WidgetNoteText

internal data class LandscapeNoteLine(
    val value: String,
    val top: Float,
    val size: Float,
    val idea: Boolean,
)

/** Wraps the original passages using the same fonts and line spacing as the collapsed C card. */
internal object LandscapeNoteFlow {
    fun layout(
        note: WidgetLandscapeNotePresentation,
        scale: Float,
        width: Float,
    ): List<LandscapeNoteLine> {
        val result = mutableListOf<LandscapeNoteLine>()
        val u = note.unit * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var top = 0f

        fun append(
            text: String,
            idea: Boolean,
        ) {
            val size = (if (idea) 15f else note.quoteSize) * u
            val lineHeight = (if (idea) 24f else note.quoteSize * 1.7f) * u
            paint.textSize = size
            paint.typeface = if (idea) Typeface.DEFAULT else Typeface.SERIF
            text.split('\n').forEach { paragraph ->
                var rest = paragraph
                if (rest.isEmpty()) {
                    result += LandscapeNoteLine("", top, size, idea)
                    top += lineHeight
                }
                while (rest.isNotEmpty()) {
                    val measured =
                        paint.breakText(
                            rest,
                            true,
                            (width - if (idea) 12f * u else 0f).coerceAtLeast(1f),
                            null,
                        )
                    val count = WidgetNoteText.fitLength(rest, measured, wrap = true)
                    result += LandscapeNoteLine(rest.take(count), top, size, idea)
                    top += lineHeight
                    rest = rest.drop(count)
                }
            }
        }
        if (note.quote.isNotBlank()) append(note.quote, false)
        if (note.idea.isNotBlank()) {
            if (result.isNotEmpty()) top += 18f * u
            append("想法：${note.idea}", true)
        }
        return result
    }

    fun height(lines: List<LandscapeNoteLine>): Float =
        lines.lastOrNull()?.let { it.top + it.size * if (it.idea) 1.6f else 1.7f } ?: 0f
}
