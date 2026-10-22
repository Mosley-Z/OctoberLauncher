package com.shuiyu.octoberrain.feature.reader.office.sheets

import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

class WorkbookBudgetTest {
    @Test fun xlsxAllowsSparseChineseCellsWithinDimensions() {
        WorkbookBudget.checkXml(
            "<worksheet><dimension ref=\"A1:ZZ1000\"/><row r=\"1000\"><c r=\"ZZ1000\"/></row></worksheet>",
        )
    }

    @Test fun xlsxHugeDimensionsAndTotalCellCountsFailBeforeSheetJs() {
        assertThrows(SheetLimitException::class.java) {
            WorkbookBudget.checkXml("<worksheet><dimension ref=\"A1:XFD1048576\"/></worksheet>")
        }
        assertThrows(SheetLimitException::class.java) {
            WorkbookBudget.checkXml("<worksheet>" + "<c r=\"A1\"/>".repeat(100001) + "</worksheet>")
        }
    }

    @Test fun odsRepeatedRowsAndCellsUseExpandedCellCount() {
        val prefix = "<document xmlns:t=\"urn:table\"><t:table><t:table-row t:number-rows-repeated=\"1000\">"
        WorkbookBudget.checkXml(
            prefix + "<t:table-cell t:number-columns-repeated=\"100\"/></t:table-row></t:table></document>",
            true,
        )
        assertThrows(SheetLimitException::class.java) {
            WorkbookBudget.checkXml(
                prefix + "<t:table-cell t:number-columns-repeated=\"101\"/></t:table-row></t:table></document>",
                true,
            )
        }
    }

    @Test fun externalEntityIsRejectedBeforeContentRead() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkbookBudget.checkXml("<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///never-read'>]><a>&x;</a>")
        }
    }

    @Test fun declarationScannerRejectsUtf16AndTokensSplitAcrossReads() {
        listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE).forEach { charset ->
            listOf("<!DOCTYPE a>", "<!ENTITY x 'value'>").forEach { declaration ->
                val text = "<before/>" + " ".repeat(9000) + declaration
                val source =
                    object : java.io.ByteArrayInputStream(text.toByteArray(charset)) {
                        override fun read(
                            bytes: ByteArray,
                            offset: Int,
                            length: Int,
                        ): Int = super.read(bytes, offset, minOf(length, 1))
                    }
                assertThrows(IllegalArgumentException::class.java) {
                    SheetXmlInput(source).use { it.readBytes() }
                }
            }
        }
    }

    @Test fun biffCellsAreCountedBeforeWorkbookParsing() {
        BiffBudget.checkRecords(biff(10))
        assertThrows(SheetLimitException::class.java) { BiffBudget.checkRecords(biff(100001)) }
        assertThrows(
            IllegalArgumentException::class.java,
        ) { BiffBudget.checkRecords(biff(1).dropLast(1).toByteArray()) }
    }

    private fun biff(count: Int): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(9, 8, 4, 0, 0, 6, 5, 0))
        repeat(count) {
            output.write(byteArrayOf(3, 2, 14, 0))
            output.write(ByteArray(14))
        }
        return output.toByteArray()
    }
}
