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
import com.shuiyu.octoberrain.feature.reader.web.OfficeWebMode
import com.shuiyu.octoberrain.feature.reader.web.WebDocument
import com.shuiyu.octoberrain.feature.reader.web.WebPosition
import com.shuiyu.octoberrain.feature.reader.web.WebRequest
import com.shuiyu.octoberrain.feature.reader.web.WebResource
import com.shuiyu.octoberrain.feature.reader.web.WebResources
import java.util.UUID

/** A single offline parser owns the bounded workbook; only 20×10 cell values cross the bridge. */
internal class WorkbookEngine private constructor(
    private val document: WebDocument,
) : TableEngine,
    SearchProvider {
    override val capabilities = ReaderCapabilities(search = true, bookmark = true)
    override var sheetNames: List<String> = emptyList()
        private set
    private var position = WebPosition()
    private var highlight: Pair<Int, Int>? = null

    override suspend fun page(locator: ReaderLocator?): ReaderFrame {
        val saved = locator as? ReaderLocator.Sheet
        return locate(saved?.sheet ?: 0, saved?.row ?: 0, saved?.column ?: 0)
    }

    override suspend fun locate(
        sheet: Int,
        row: Int,
        column: Int,
    ): ReaderFrame {
        highlight = null
        val target =
            position.copy(
                sheet = sheet.coerceAtLeast(0),
                row = row.coerceAtLeast(0),
                column = column.coerceAtLeast(0),
            )
        return request(WebRequest(document, target))
    }

    override suspend fun turn(delta: Int): ReaderFrame {
        highlight = null
        return request(WebRequest(document, position, "turn", delta))
    }

    override suspend fun scan(
        query: String,
        offset: Int,
    ): SearchScan =
        SearchScan(web = WebRequest(document, position, "searchAll", query = query.take(256), searchOffset = offset))

    override suspend fun showHit(
        hit: SearchHit,
        query: String,
    ): ReaderFrame {
        val saved = hit.locator as? ReaderLocator.Sheet ?: error("Expected sheet search locator")
        highlight = saved.row to saved.column
        val target = position.copy(sheet = saved.sheet, row = saved.row, column = saved.column)
        return request(WebRequest(document, target, "hit", query = query.take(256)))
    }

    override suspend fun findNext(query: String): ReaderFrame =
        request(WebRequest(document, position, "search", query = query.take(256)))

    private fun request(request: WebRequest): ReaderFrame =
        ReaderFrame(
            ReaderLocator.Sheet(request.position.sheet, request.position.row, request.position.column),
            "正在读取表格…",
            web = request,
        )

    override fun acceptTable(table: ReaderTable): ReaderFrame {
        sheetNames = table.sheets
        position = position.copy(sheet = table.sheet, row = table.row, column = table.column)
        val name = sheetNames.getOrElse(table.sheet) { "工作表" }
        return ReaderFrame(
            ReaderLocator.Sheet(table.sheet, table.row, table.column),
            "$name · ${table.row + 1}/${table.totalRows}行 · ${table.column + 1}/${table.totalColumns}列 · 公式仅缓存值",
            table = table.copy(highlight = highlight),
        )
    }

    override fun close() = document.resources.close()

    companion object {
        fun create(
            source: DocumentHandle,
            locator: ReaderLocator?,
            font: Int,
        ): WorkbookEngine {
            val failure =
                try {
                    WorkbookBudget.check(source.archiveFile())
                    null
                } catch (_: SheetLimitException) {
                    ReaderFailure.LIMIT
                } catch (_: IllegalArgumentException) {
                    ReaderFailure.CORRUPT
                } catch (_: org.xml.sax.SAXException) {
                    ReaderFailure.CORRUPT
                }
            if (failure != null) throw ReaderSourceException(failure)
            val document =
                WebDocument(
                    UUID.randomUUID().toString(),
                    "",
                    font,
                    WorkbookResources(source),
                    office = OfficeWebMode.SHEETS,
                )
            return WorkbookEngine(document).apply {
                val saved = locator as? ReaderLocator.Sheet
                if (saved != null) position = WebPosition(sheet = saved.sheet, row = saved.row, column = saved.column)
            }
        }
    }
}

private class WorkbookResources(
    private val source: DocumentHandle,
) : WebResources {
    private var closed = false

    @Synchronized override fun open(path: String): WebResource? =
        if (!closed && path == "source.bin") WebResource("application/octet-stream", source.openStream()) else null

    @Synchronized override fun close() {
        closed = true
    }
}
