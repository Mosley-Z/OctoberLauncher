package com.shuiyu.octoberrain.feature.reader.office.sheets

import android.os.ParcelFileDescriptor
import com.shuiyu.octoberrain.core.common.DocumentMetadata
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.CancellationException

class CsvLegacyTest {
    @Test fun oldTextProgressAndBookmarksMapLogicalQuotedRecordsForUtf8AndGb18030() =
        runBlocking {
            listOf(Charsets.UTF_8, Charset.forName("GB18030")).forEach { charset ->
                listOf("", "\uFEFF").forEach { bom ->
                    val text = bom + "表头,值\r\n\"第一\n第二\",中文\r\n目标,末行\r\n"
                    val source = LegacyCsvSource(text, charset)
                    val quoted = oldLocator(text, text.indexOf("第二"), charset)
                    val engine = CsvEngine.create(source, quoted, charset.name())
                    val progress = engine.page(quoted)
                    assertEquals(ReaderLocator.Sheet(row = 1, charset = charset.name()), progress.locator)
                    assertEquals(
                        "第一\n第二",
                        progress.table
                            ?.cells
                            ?.first()
                            ?.first(),
                    )
                    val bookmark = oldLocator(text, text.indexOf("目标"), charset)
                    assertEquals(ReaderLocator.Sheet(row = 2, charset = charset.name()), engine.page(bookmark).locator)
                    assertEquals(
                        "目标",
                        engine
                            .page(bookmark)
                            .table
                            ?.cells
                            ?.first()
                            ?.first(),
                    )
                    engine.close()
                }
            }
        }

    @Test fun bomInteriorRecordBoundaryEofAndEmptyFileClampToValidRecords() =
        runBlocking {
            listOf(Charsets.UTF_8, Charset.forName("GB18030")).forEach { charset ->
                val text = "\uFEFF首行,1\r\n目标,2\r\n"
                val source = LegacyCsvSource(text, charset)
                val engine = CsvEngine.create(source, null, charset.name())
                for (byte in 0 until "\uFEFF".toByteArray(charset).size) {
                    val locator = ReaderLocator.Text(byte.toLong(), charset.name())
                    assertEquals(0, (engine.page(locator).locator as ReaderLocator.Sheet).row)
                }
                val boundary = oldLocator(text, text.indexOf("目标"), charset)
                assertEquals(1, (engine.page(boundary).locator as ReaderLocator.Sheet).row)
                assertEquals(
                    1,
                    (
                        engine
                            .page(
                                boundary.copy(byteOffset = boundary.byteOffset + 1),
                            ).locator as ReaderLocator.Sheet
                    ).row,
                )
                val end = text.toByteArray(charset).size.toLong()
                assertEquals(1, (engine.page(boundary.copy(byteOffset = end)).locator as ReaderLocator.Sheet).row)
                assertEquals(1, (engine.page(boundary.copy(byteOffset = end + 999)).locator as ReaderLocator.Sheet).row)
                engine.close()
            }
            val empty = CsvEngine.create(LegacyCsvSource("", Charsets.UTF_8), null)
            assertEquals(0, (empty.page(ReaderLocator.Text(999)).locator as ReaderLocator.Sheet).row)
            empty.close()
        }

    @Test fun boundedDecoderRetainsMultibyteTailsAcrossEightKibReads() =
        runBlocking {
            listOf(Charsets.UTF_8, Charset.forName("GB18030")).forEach { charset ->
                val text = "\uFEFF\"" + "x".repeat(8186) + "𠀀\n跨行\",数据\r\n目标,值"
                val locator = oldLocator(text, text.indexOf("目标"), charset)
                val engine = CsvEngine.create(LegacyCsvSource(text, charset), locator, charset.name())
                assertEquals(ReaderLocator.Sheet(row = 1, charset = charset.name()), engine.page(locator).locator)
                engine.close()
            }
        }

    @Test fun migrationChecksCancellationDuringPrefixAndRecordScan() {
        val source = LegacyCsvSource("x".repeat(20000) + "\n目标", Charsets.UTF_8)
        var checks = 0
        assertThrows(CancellationException::class.java) {
            CsvData(source, Charsets.UTF_8).legacyRow(19000) {
                checks++
                if (checks == 2) throw CancellationException()
            }
        }
        assertEquals(2, checks)
        checks = 0
        val records = LegacyCsvSource("a\n".repeat(1000), Charsets.UTF_8)
        assertThrows(CancellationException::class.java) {
            CsvData(records, Charsets.UTF_8).legacyRow(1000) {
                checks++
                if (checks == 20) throw CancellationException()
            }
        }
        assertEquals(20, checks)
    }

    private fun oldLocator(
        text: String,
        character: Int,
        charset: Charset,
    ): ReaderLocator.Text =
        ReaderLocator.Text(
            text
                .substring(0, character)
                .toByteArray(charset)
                .size
                .toLong(),
            charset.name(),
        )
}

private class LegacyCsvSource(
    text: String,
    charset: Charset,
) : DocumentHandle {
    private val bytes = text.toByteArray(charset)
    override val metadata =
        DocumentMetadata("legacy-fixture", "legacy.csv", "text/csv", bytes.size.toLong(), null, false)

    override fun openStream() = bytes.inputStream()

    override fun openSeekable(): ParcelFileDescriptor = error("CSV migration must remain streaming")

    override fun archiveFile(): File = error("CSV migration must not copy the source")

    override fun close() = Unit
}
