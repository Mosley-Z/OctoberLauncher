package com.shuiyu.octoberrain.feature.reader.office.sheets

import android.os.ParcelFileDescriptor
import com.shuiyu.octoberrain.core.common.DocumentMetadata
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.CancellationException

class CsvEngineTest {
    @Test fun unifiedSearchCountsCellsPaginatesAndPreservesTheVisibleProgress() =
        runBlocking {
            val source = MemoryCsv((0 until 100).joinToString("\n") { "目标$it,目标目标$it" })
            val engine = CsvEngine.create(source, null)
            val before = engine.locate(0, 70, 1)
            val page = engine.scan("目标", 64).page
            assertEquals(200, page?.total)
            assertEquals(64, page?.hits?.size)
            assertEquals(ReaderLocator.Sheet(row = 32), page?.hits?.first()?.locator)
            val after = engine.turn(0)
            assertEquals(before.locator, after.locator)
            assertEquals(before.status, after.status)
            assertEquals(
                0,
                engine
                    .scan("目标", 999)
                    .page
                    ?.hits
                    ?.size,
            )
            engine.close()
        }

    @Test fun unifiedSearchShowHitLocatesAndMarksTheCell() =
        runBlocking {
            val source = MemoryCsv((0 until 1000).joinToString("\n") { "行$it,晚段$it" })
            val engine = CsvEngine.create(source, null)
            val hit =
                checkNotNull(
                    engine
                        .scan("晚段900", 0)
                        .page
                        ?.hits
                        ?.single(),
                )
            val frame = engine.showHit(hit, "晚段900")
            assertEquals(ReaderLocator.Sheet(row = 900, column = 1), frame.locator)
            assertEquals(900 to 1, frame.table?.highlight)
            assertEquals(
                "晚段900",
                frame.table
                    ?.cells
                    ?.first()
                    ?.first(),
            )
            engine.close()
        }

    @Test fun unifiedScanPropagatesCancellation() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                coroutineContext.cancel()
                CsvEngine.create(MemoryCsv("a,b"), null).scan("missing", 0)
            }
        }
    }

    @Test fun sparseAnchorsPreserveBomMultibyteAndEmbeddedNewlineRecords() =
        runBlocking {
            val text = "\uFEFF" + (0 until 1000).joinToString("\r\n") { "\"第${it}行\n内容\",$it" }
            val engine = CsvEngine.create(MemoryCsv(text), null)
            assertEquals(
                "第600行\n内容",
                engine
                    .locate(0, 600, 0)
                    .table
                    ?.cells
                    ?.first()
                    ?.first(),
            )
            assertEquals(
                "第260行\n内容",
                engine
                    .locate(0, 260, 0)
                    .table
                    ?.cells
                    ?.first()
                    ?.first(),
            )
            val restored = engine.page(ReaderLocator.Sheet(row = 999, column = 1))
            assertEquals(
                "999",
                restored.table
                    ?.cells
                    ?.first()
                    ?.first(),
            )
            assertEquals(1000, restored.table?.totalRows)
            engine.close()
        }

    @Test fun explicitCharsetOverridesStoredCharsetAndSearchReadsUnloadedRows() =
        runBlocking {
            val charset = Charset.forName("GB18030")
            val source = MemoryCsv((0 until 1000).joinToString("\n") { "中文$it,晚段目标$it" }, charset)
            val engine = CsvEngine.create(source, ReaderLocator.Sheet(charset = "UTF-8"), charset.name())
            engine.page(null)
            val found = engine.findNext("晚段目标900")
            assertEquals(900, (found?.locator as? ReaderLocator.Sheet)?.row)
            assertEquals(1, (found?.locator as? ReaderLocator.Sheet)?.column)
            assertEquals("GB18030", (found?.locator as? ReaderLocator.Sheet)?.charset)
            engine.close()
        }

    @Test fun emptyCsvProducesEmptyWindowAndSearchCancellationPropagates() =
        runBlocking {
            val empty = CsvEngine.create(MemoryCsv(""), null).page(null).table
            assertEquals(0, empty?.totalRows)
            assertEquals(0, empty?.totalColumns)
            assertEquals(emptyList<List<String>>(), empty?.cells)
            assertThrows(CancellationException::class.java) {
                runBlocking {
                    coroutineContext.cancel()
                    CsvEngine.create(MemoryCsv("a,b"), null).findNext("missing")
                }
            }
            Unit
        }
}

private class MemoryCsv(
    text: String,
    charset: Charset = Charsets.UTF_8,
) : DocumentHandle {
    private val bytes = text.toByteArray(charset)
    override val metadata = DocumentMetadata("fixture", "fixture.csv", "text/csv", bytes.size.toLong(), null, false)

    override fun openStream() = bytes.inputStream()

    override fun openSeekable(): ParcelFileDescriptor = error("Streaming CSV must not request a file descriptor")

    override fun archiveFile(): File = error("Streaming CSV must not request an archive copy")

    override fun close() = Unit
}
