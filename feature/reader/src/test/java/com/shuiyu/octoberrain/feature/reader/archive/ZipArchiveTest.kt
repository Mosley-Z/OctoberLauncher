package com.shuiyu.octoberrain.feature.reader.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

class ZipArchiveTest {
    @Test fun virtualDirectoryPreservesChineseAndLiteralPercentWithoutExtracting() {
        val file = archive("目录/正文.txt" to "中文", "目录/100%20.txt" to "percent", "top.txt" to "top")
        try {
            ZipArchive(file).use { zip ->
                assertEquals(listOf("目录", "top.txt"), zip.children("").map { it.path })
                assertTrue(zip.children("").first().directory)
                assertEquals(2, zip.children("目录").size)
                assertEquals("中文", zip.open("目录/正文.txt").bufferedReader().use { it.readText() })
                assertEquals("percent", zip.open("目录/100%20.txt").bufferedReader().use { it.readText() })
            }
        } finally {
            file.delete()
        }
    }

    @Test fun rejectsEscapingAndAmbiguousNames() {
        listOf("../secret", "/secret", "C:\\secret", "a/../../secret", "a\u0000b").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { ArchivePaths.normalize(path) }
        }
        val file = archive("a/../x" to "one", "x" to "two")
        try {
            assertThrows(ZipException::class.java) { ZipArchive(file) }
        } finally {
            file.delete()
        }
    }

    @Test fun outputBudgetAndCrcAreCheckedButPartialPreviewDoesNotDrain() {
        val bytes = "test".toByteArray()
        val crc = CRC32().apply { update(bytes) }.value
        CheckedEntryStream(ByteArrayInputStream(bytes), 4, crc, 4).use { stream ->
            assertEquals(2L, stream.skip(2))
            assertEquals("st", stream.readBytes().toString(Charsets.UTF_8))
        }
        assertThrows(ZipException::class.java) {
            CheckedEntryStream(ByteArrayInputStream(bytes), 4, crc, 3).readBytes()
        }
        assertThrows(ZipException::class.java) {
            CheckedEntryStream(ByteArrayInputStream(bytes), 4, crc + 1, 4).readBytes()
        }
        CheckedEntryStream(ByteArrayInputStream(bytes), 10, 0, 10).use { assertEquals('t'.code, it.read()) }
    }

    @Test fun rejectsCorruptAndOversizedCentralDirectoryBeforeZipFileAllocation() {
        val file = File.createTempFile("reader-zip-", ".zip")
        try {
            file.writeText("broken")
            assertThrows(ZipException::class.java) { ZipPreflight.check(file) }
            val end = ByteArray(22)
            end[0] = 0x50
            end[1] = 0x4b
            end[2] = 5
            end[3] = 6
            end[14] = 0x50 // 5MiB directory exceeds the 4MiB budget.
            file.writeBytes(end)
            assertThrows(ZipException::class.java) { ZipPreflight.check(file) }
        } finally {
            file.delete()
        }
    }

    private fun archive(vararg entries: Pair<String, String>): File =
        File.createTempFile("reader-zip-", ".zip").also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (name, value) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(value.toByteArray())
                    zip.closeEntry()
                }
            }
        }
}
