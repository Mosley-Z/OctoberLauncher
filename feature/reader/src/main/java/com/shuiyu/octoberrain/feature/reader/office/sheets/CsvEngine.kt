package com.shuiyu.octoberrain.feature.reader.office.sheets

import com.shuiyu.octoberrain.core.common.ReaderCapabilities
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.ReaderFrame
import com.shuiyu.octoberrain.feature.reader.ReaderTable
import com.shuiyu.octoberrain.feature.reader.TableEngine
import com.shuiyu.octoberrain.feature.reader.search.SearchHit
import com.shuiyu.octoberrain.feature.reader.search.SearchProvider
import com.shuiyu.octoberrain.feature.reader.search.SearchScan
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.charset.Charset
import java.util.TreeMap

/** Reads only requested records, retaining sparse decoded-character anchors rather than table contents. */
internal class CsvEngine private constructor(
    source: DocumentHandle,
    private val charset: Charset,
) : TableEngine,
    SearchProvider {
    private val data = CsvData(source, charset)
    private val search = CsvSearch(data, charset.name())
    override val capabilities = ReaderCapabilities(search = true, bookmark = true)
    override val sheetNames = listOf("CSV")
    private val anchors = TreeMap<Int, Long>().apply { put(0, 0) }
    private var knownRows = 0
    private var columns = 0
    private var eof = false
    private var current = ReaderLocator.Sheet(charset = charset.name())
    private var lastQuery = ""
    private var lastMatch: Pair<Int, Int>? = null

    override suspend fun page(locator: ReaderLocator?): ReaderFrame {
        val context = currentCoroutineContext()
        val saved =
            when (locator) {
                is ReaderLocator.Sheet -> locator
                is ReaderLocator.Text ->
                    ReaderLocator.Sheet(
                        row = data.legacyRow(locator.byteOffset) { context.ensureActive() },
                    )
                else -> current
            }
        return locate(0, saved.row, saved.column)
    }

    override suspend fun turn(delta: Int): ReaderFrame = locate(0, current.row + delta * 20, current.column)

    override suspend fun locate(
        sheet: Int,
        row: Int,
        column: Int,
    ): ReaderFrame {
        val context = currentCoroutineContext()
        val target = row.coerceIn(0, SheetLimits.ROWS - 1)
        val anchor = checkNotNull(anchors.floorEntry(target))
        val values = mutableListOf<List<String>>()
        var number = anchor.key
        data.open(anchor.value) { context.ensureActive() }.use { records ->
            while (number < target + SheetLimits.WINDOW_ROWS) {
                context.ensureActive()
                if (number % ANCHOR_STEP == 0) anchors[number] = records.position
                val record = data.next(records)
                if (record == null) {
                    eof = true
                    break
                }
                checkCsvRow(number)
                knownRows = maxOf(knownRows, number + 1)
                columns = maxOf(columns, record.size)
                if (number >= target) values += record
                number++
            }
        }
        val actualRow = if (values.isEmpty()) (knownRows - 1).coerceAtLeast(0) else target
        if (actualRow != target) return locate(0, actualRow, column)
        val actualColumn = column.coerceIn(0, (columns - 1).coerceAtLeast(0))
        val cells = values.map { it.drop(actualColumn).take(10).map { value -> value.take(SheetLimits.CELL_TEXT) } }
        return acceptTable(ReaderTable(0, sheetNames, actualRow, actualColumn, knownRows, columns, cells))
    }

    override suspend fun findNext(query: String): ReaderFrame? {
        if (query.isEmpty()) return null
        val context = currentCoroutineContext()
        val needle = query.take(256)
        val start = if (needle == lastQuery && lastMatch == (current.row to current.column)) lastMatch else null
        val found = scan(needle, start) { context.ensureActive() }
        return found?.let {
            lastQuery = needle
            lastMatch = it
            locate(0, it.first, it.second)
        }
    }

    private fun scan(
        needle: String,
        start: Pair<Int, Int>?,
        checkpoint: () -> Unit,
    ): Pair<Int, Int>? {
        var number = 0
        var match: Pair<Int, Int>? = null
        data.open(0, checkpoint).use { records ->
            while (match == null) {
                checkpoint()
                if (number % ANCHOR_STEP == 0) anchors[number] = records.position
                val record = data.next(records) ?: break
                checkCsvRow(number)
                val column = matchingColumn(record, needle, start, number)
                if (column != null) match = number to column
                number++
            }
        }
        return match
    }

    private fun matchingColumn(
        record: List<String>,
        needle: String,
        start: Pair<Int, Int>?,
        row: Int,
    ): Int? {
        val firstColumn =
            when {
                start == null -> 0
                row < start.first -> record.size
                row == start.first -> start.second + 1
                else -> 0
            }
        return (firstColumn until record.size).firstOrNull { record[it].contains(needle) }
    }

    override fun acceptTable(table: ReaderTable): ReaderFrame {
        current = ReaderLocator.Sheet(0, table.row, table.column, charset.name())
        val rows = if (eof) "${table.totalRows}行" else "已读${table.totalRows}行"
        return ReaderFrame(current, "CSV · ${table.row + 1}行 ${table.column + 1}列 · $rows", table = table)
    }

    override suspend fun scan(
        query: String,
        offset: Int,
    ): SearchScan = search.scan(query, offset)

    override suspend fun showHit(
        hit: SearchHit,
        query: String,
    ): ReaderFrame {
        val saved = hit.locator as? ReaderLocator.Sheet ?: error("Expected sheet search locator")
        val frame = locate(saved.sheet, saved.row, saved.column)
        return frame.copy(table = frame.table?.copy(highlight = saved.row to saved.column))
    }

    override fun close() = anchors.clear()

    companion object {
        private const val ANCHOR_STEP = 256

        fun create(
            source: DocumentHandle,
            locator: ReaderLocator?,
            charset: String = "UTF-8",
        ): CsvEngine =
            CsvEngine(source, Charset.forName(charset)).apply {
                current = (locator as? ReaderLocator.Sheet)?.copy(charset = charset) ?: current
            }
    }
}

private fun checkCsvRow(row: Int) {
    if (row >= SheetLimits.ROWS) throw ReaderSourceException(ReaderFailure.LIMIT)
}
