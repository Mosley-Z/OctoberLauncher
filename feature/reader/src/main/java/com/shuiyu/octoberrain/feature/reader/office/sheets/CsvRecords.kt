package com.shuiyu.octoberrain.feature.reader.office.sheets

import java.io.Closeable
import java.io.Reader

/** RFC4180 records, including embedded newlines. Keeps at most one bounded record in memory. */
internal class CsvRecords(
    private val input: Reader,
    start: Long = 0,
    private val checkpoint: () -> Unit = {},
) : Closeable {
    var position: Long = start
        private set
    private var pending = -2
    private var recordChars = 0

    fun next(): List<String>? {
        checkpoint()
        if (peek() < 0) return null
        recordChars = 0
        val fields = mutableListOf<String>()
        var done = false
        while (!done) {
            sheetBudget(fields.size < SheetLimits.COLUMNS)
            val value = StringBuilder()
            if (peek() == '"'.code) quoted(value) else plain(value)
            fields += value.toString()
            when (take()) {
                ','.code -> Unit
                '\r'.code -> {
                    if (peek() == '\n'.code) take()
                    done = true
                }
                '\n'.code, -1 -> done = true
                else -> throw IllegalArgumentException("Invalid CSV separator")
            }
        }
        return fields
    }

    private fun quoted(value: StringBuilder) {
        take()
        var done = false
        while (!done) {
            val char = take()
            require(char >= 0) { "Unclosed CSV quote" }
            if (char == '"'.code) {
                if (peek() == '"'.code) {
                    take()
                    append(value, char)
                } else {
                    done = true
                }
            } else {
                append(value, char)
            }
        }
    }

    private fun plain(value: StringBuilder) {
        while (peek() !in FIELD_END) {
            val char = take()
            require(char != '"'.code) { "Quote inside unquoted CSV field" }
            append(value, char)
        }
    }

    private fun append(
        value: StringBuilder,
        char: Int,
    ) {
        recordChars++
        sheetBudget(value.length < MAX_FIELD && recordChars <= 262144)
        value.append(char.toChar())
        if (value.length % 4096 == 0) checkpoint()
    }

    private fun peek(): Int {
        if (pending == -2) pending = input.read()
        return pending
    }

    private fun take(): Int {
        val value = peek()
        pending = -2
        if (value >= 0) position++
        return value
    }

    override fun close() = input.close()

    companion object {
        const val MAX_FIELD = 65536
        private val FIELD_END = setOf(-1, ','.code, '\r'.code, '\n'.code)
    }
}

internal class SheetLimitException : IllegalArgumentException("Workbook budget exceeded")

internal fun sheetBudget(allowed: Boolean) {
    if (!allowed) throw SheetLimitException()
}

internal object SheetLimits {
    const val ROWS = 100000
    const val COLUMNS = 1024
    const val CELLS = 100000
    const val SHEETS = 100
    const val WINDOW_ROWS = 20
    const val WINDOW_COLUMNS = 10
    const val CELL_TEXT = 512
    const val COMPRESSED = 8L * 1024 * 1024
    const val EXPANDED = 32L * 1024 * 1024
}
