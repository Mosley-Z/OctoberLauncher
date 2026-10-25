package com.shuiyu.octoberrain.core.widget

import java.text.BreakIterator
import java.util.Locale

/** Keep measured UTF-16 prefixes on character boundaries when wrapping or adding an inline action. */
object WidgetNoteText {
    fun fitLength(
        text: String,
        count: Int,
        wrap: Boolean = false,
    ): Int {
        if (text.isEmpty()) return 0
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        val limit = count.coerceIn(0, text.length)
        val end = if (iterator.isBoundary(limit)) limit else iterator.preceding(limit)
        return if (wrap && end <= 0) iterator.following(0) else end.coerceAtLeast(0)
    }

    fun dropLast(text: String): String {
        if (text.isEmpty()) return text
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        return text.take(iterator.preceding(text.length).coerceAtLeast(0))
    }
}
