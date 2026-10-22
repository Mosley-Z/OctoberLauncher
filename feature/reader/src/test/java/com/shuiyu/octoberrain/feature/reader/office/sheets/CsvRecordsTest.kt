package com.shuiyu.octoberrain.feature.reader.office.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.StringReader
import java.nio.charset.Charset
import java.util.concurrent.CancellationException

class CsvRecordsTest {
    @Test fun quotedCommaEscapedQuoteAndEmbeddedCrLfStayInOneRecord() {
        CsvRecords(StringReader("\"中文,列\",\"第一行\r\n第二行\",\"a\"\"b\",\r\nx,,\r\n")).use {
            assertEquals(listOf("中文,列", "第一行\r\n第二行", "a\"b", ""), it.next())
            assertEquals(listOf("x", "", ""), it.next())
            assertNull(it.next())
        }
    }

    @Test fun decodedCharacterAnchorsResumeAfterMultilineRecord() {
        val text = "\"前\n后\",1\r\n目标,2"
        val offset =
            CsvRecords(StringReader(text)).use {
                it.next()
                it.position
            }
        CsvRecords(StringReader(text.drop(offset.toInt())), offset).use {
            assertEquals(listOf("目标", "2"), it.next())
            assertEquals(text.length.toLong(), it.position)
        }
    }

    @Test fun utf8AndGb18030ReadersPreserveChineseAndSupplementaryCharacters() {
        listOf(Charsets.UTF_8, Charset.forName("GB18030")).forEach { charset ->
            val input = "中文,𠀀\n".byteInputStream(charset).reader(charset)
            CsvRecords(input).use { assertEquals(listOf("中文", "𠀀"), it.next()) }
        }
    }

    @Test fun emptyFileHasNoRowsAndEmptyRecordHasOneCell() {
        CsvRecords(StringReader("")).use { assertNull(it.next()) }
        CsvRecords(StringReader("\n")).use {
            assertEquals(listOf(""), it.next())
            assertNull(it.next())
        }
    }

    @Test fun unclosedAndUnquotedQuotesAreRejected() {
        listOf("\"unfinished", "a\"b,c", "\"a\"b,c").forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { CsvRecords(StringReader(text)).use { it.next() } }
        }
    }

    @Test fun fieldAndColumnBudgetsStopBeforeUnboundedRecordAllocation() {
        listOf("x".repeat(CsvRecords.MAX_FIELD + 1), ",".repeat(SheetLimits.COLUMNS)).forEach { text ->
            assertThrows(SheetLimitException::class.java) { CsvRecords(StringReader(text)).use { it.next() } }
        }
    }

    @Test fun longRecordChecksCancellationWhileReadingAField() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            val input = StringReader("x".repeat(20000))
            val records =
                CsvRecords(input, checkpoint = {
                    checks++
                    if (checks == 3) throw CancellationException()
                })
            records.use { it.next() }
        }
        assertEquals(3, checks)
    }
}
