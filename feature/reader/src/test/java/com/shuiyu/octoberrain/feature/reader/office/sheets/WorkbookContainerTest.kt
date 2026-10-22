package com.shuiyu.octoberrain.feature.reader.office.sheets

import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

class WorkbookContainerTest {
    @Test fun biffCompoundStreamIsValidatedAndCyclicFatIsRejected() {
        BiffBudget.check(compoundWorkbook())
        val cyclic = compoundWorkbook()
        putInt(cyclic, 512 + 2 * 4, 2)
        assertThrows(IllegalArgumentException::class.java) { BiffBudget.check(cyclic) }
    }

    @Test fun nonXmlCrcIsVerifiedBeforeSheetJsReceivesTheSource() {
        val file = File.createTempFile("sheets-crc", ".ods")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("content.xml"))
                zip.write("<document/>".toByteArray())
                zip.closeEntry()
                val bytes = "unique-binary-payload".toByteArray()
                val entry = ZipEntry("media.bin")
                entry.method = ZipEntry.STORED
                entry.size = bytes.size.toLong()
                entry.crc =
                    java.util.zip
                        .CRC32()
                        .apply { update(bytes) }
                        .value
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
            WorkbookBudget.check(file)
            val bytes = file.readBytes()
            val marker = "unique-binary-payload".toByteArray()
            val start =
                bytes.indices.first { index ->
                    index + marker.size <= bytes.size &&
                        bytes.copyOfRange(index, index + marker.size).contentEquals(marker)
                }
            bytes[start] = 'X'.code.toByte()
            file.writeBytes(bytes)
            assertThrows(ZipException::class.java) { WorkbookBudget.check(file) }
        } finally {
            file.delete()
        }
    }

    @Test fun expandedContainerBudgetAppliesToBinaryEntries() {
        val file = File.createTempFile("sheets-expanded", ".ods")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("content.xml"))
                zip.write("<document/>".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("media.bin"))
                val block = ByteArray(8192)
                repeat(4097) { zip.write(block) }
                zip.closeEntry()
            }
            assertThrows(SheetLimitException::class.java) { WorkbookBudget.check(file) }
        } finally {
            file.delete()
        }
    }

    private fun compoundWorkbook(): ByteArray {
        val bytes = ByteArray(11 * 512)
        val signature = byteArrayOf(-48, -49, 17, -32, -95, -79, 26, -31)
        signature.copyInto(bytes)
        bytes[28] = -2
        bytes[29] = -1
        bytes[30] = 9
        bytes[32] = 6
        putInt(bytes, 44, 1)
        putInt(bytes, 48, 1)
        putInt(bytes, 56, 4096)
        putInt(bytes, 60, -2)
        putInt(bytes, 68, -2)
        putInt(bytes, 76, 0)
        repeat(128) { putInt(bytes, 512 + it * 4, -1) }
        putInt(bytes, 512, -3)
        putInt(bytes, 516, -2)
        for (index in 2..9) putInt(bytes, 512 + index * 4, if (index == 9) -2 else index + 1)
        directory(bytes, 1024, "Root Entry", 5, -2, 0)
        directory(bytes, 1152, "Workbook", 2, 2, 4096)
        byteArrayOf(9, 8, 4, 0, 0, 6, 5, 0).copyInto(bytes, 1536)
        return bytes
    }

    private fun directory(
        bytes: ByteArray,
        offset: Int,
        name: String,
        type: Int,
        start: Int,
        size: Int,
    ) {
        val encoded = (name + '\u0000').toByteArray(Charsets.UTF_16LE)
        encoded.copyInto(bytes, offset)
        bytes[offset + 64] = encoded.size.toByte()
        bytes[offset + 66] = type.toByte()
        putInt(bytes, offset + 116, start)
        putInt(bytes, offset + 120, size)
    }

    private fun putInt(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        for (index in 0..3) bytes[offset + index] = (value ushr (index * 8)).toByte()
    }
}
