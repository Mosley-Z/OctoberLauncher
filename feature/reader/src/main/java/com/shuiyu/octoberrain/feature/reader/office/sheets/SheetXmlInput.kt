package com.shuiyu.octoberrain.feature.reader.office.sheets

import java.io.FilterInputStream
import java.io.InputStream

/** Reject declarations throughout the stream without Android-specific SAX feature flags. */
internal class SheetXmlInput(
    input: InputStream,
) : FilterInputStream(input) {
    private val patterns = listOf("<!DOCTYPE", "<!ENTITY")
    private val matched = IntArray(patterns.size)

    override fun read(): Int {
        val value = `in`.read()
        if (value >= 0) inspect(value)
        return value
    }

    override fun read(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val count = `in`.read(bytes, offset, length)
        for (index in offset until offset + count.coerceAtLeast(0)) inspect(bytes[index].toInt() and 255)
        return count
    }

    private fun inspect(value: Int) {
        // Ignoring zero bytes also recognises UTF-16 LE/BE and UTF-32 declaration tokens.
        if (value == 0) return
        patterns.forEachIndexed { index, pattern ->
            val previous = matched[index]
            matched[index] =
                when (value) {
                    pattern[previous].code -> previous + 1
                    '<'.code -> 1
                    else -> 0
                }
            require(matched[index] < pattern.length) { "XML declarations are unsupported" }
        }
    }
}
