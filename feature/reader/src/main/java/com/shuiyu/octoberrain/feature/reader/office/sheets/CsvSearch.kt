package com.shuiyu.octoberrain.feature.reader.office.sheets

import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.search.SearchCollector
import com.shuiyu.octoberrain.feature.reader.search.SearchScan
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class CsvSearch(
    private val data: CsvData,
    private val charset: String,
) {
    suspend fun scan(
        query: String,
        offset: Int,
    ): SearchScan {
        val context = currentCoroutineContext()
        val collector = SearchCollector(offset)
        val needle = query.take(256)
        if (needle.isNotEmpty()) {
            scanRecords(needle, collector) { context.ensureActive() }
        }
        return SearchScan(page = collector.page())
    }

    private fun scanRecords(
        needle: String,
        collector: SearchCollector,
        checkpoint: () -> Unit,
    ) {
        data.open(0, checkpoint).use { records ->
            var row = 0
            var record = data.next(records)
            while (record != null) {
                checkpoint()
                if (row >= SheetLimits.ROWS) throw ReaderSourceException(ReaderFailure.LIMIT)
                collect(collector, record, needle, row)
                row++
                record = data.next(records)
            }
        }
    }

    private fun collect(
        collector: SearchCollector,
        record: List<String>,
        needle: String,
        row: Int,
    ) {
        record.forEachIndexed { column, value ->
            val match = value.indexOf(needle)
            if (match >= 0) {
                val snippet = value.substring((match - 32).coerceAtLeast(0)).take(160)
                collector.add(ReaderLocator.Sheet(0, row, column, charset), snippet)
            }
        }
    }
}
