package com.shuiyu.octoberrain.core.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubCoverTest {
    @Test fun relativePathsPreservePlusAndRejectEscapes() {
        assertEquals("OPS/images/a+b c.png", EpubCover.resolve("OPS/book.opf", "images/a+b%20c.png#cover"))
        assertEquals("images/a.png", EpubCover.resolve("OPS/book.opf", "../images/a.png"))
        listOf("../../escape.png", "/outside.png", "https://host/a.png", "//host/a.png", "%2Foutside.png")
            .forEach { assertNull(EpubCover.resolve("OPS/book.opf", it)) }
    }

    @Test fun actualReadEnforcesBudget() {
        assertArrayEquals(byteArrayOf(1, 2), EpubCover.boundedRead(ByteArrayInputStream(byteArrayOf(1, 2)), 2))
        assertNull(EpubCover.boundedRead(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2))
    }

    @Test fun epub2MetadataAndEpub3PropertiesResolveCover() {
        for (metadata in listOf("<meta name='cover' content='picture'/>", "")) {
            val props = if (metadata.isEmpty()) "properties='cover-image'" else ""
            val opf =
                "<package><metadata>$metadata</metadata><manifest>" +
                    "<item id='picture' href='images/a+b%20c.png' media-type='image/png' $props/>" +
                    "</manifest></package>"
            withBook(mapOf("OPS/book.opf" to opf.toByteArray(), "OPS/images/a+b c.png" to byteArrayOf(1, 2))) {
                assertEquals("OPS/images/a+b c.png", EpubCover.read(it)?.path)
                assertArrayEquals(byteArrayOf(1, 2), EpubCover.read(it)?.bytes)
            }
        }
    }

    @Test fun guideCoverPageAndMissingPrimaryCandidate() {
        val opf =
            "<package><manifest><item id='cover' href='missing.png' media-type='image/png'/>" +
                "</manifest><guide><reference type='cover' href='cover.xhtml'/></guide></package>"
        val page =
            "<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.1//EN' 'http://example.invalid/dtd'>" +
                "<html xmlns='http://www.w3.org/1999/xhtml'><body><img src='../images/cover.png'/></body></html>"
        withBook(
            mapOf(
                "OPS/book.opf" to opf.toByteArray(),
                "OPS/cover.xhtml" to page.toByteArray(),
                "images/cover.png" to byteArrayOf(7),
            ),
        ) {
            assertEquals("images/cover.png", EpubCover.read(it)?.path)
        }
    }

    @Test fun missingCorruptAndOversizedCoversReturnNull() {
        withBook(mapOf("OPS/book.opf" to "<package/>".toByteArray())) { assertNull(EpubCover.read(it)) }
        val file = File.createTempFile("broken-cover", ".epub")
        try {
            file.writeText("broken")
            assertNull(EpubCover.read(file))
        } finally {
            file.delete()
        }
        val opf = "<package><manifest><item id='cover' href='cover.png' media-type='image/png'/></manifest></package>"
        withBook(mapOf("OPS/book.opf" to opf.toByteArray(), "OPS/cover.png" to ByteArray(4 * 1024 * 1024 + 1))) {
            assertNull(EpubCover.read(it))
        }
    }

    private fun withBook(
        entries: Map<String, ByteArray>,
        block: (File) -> Unit,
    ) {
        val file = File.createTempFile("epub-cover", ".epub")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                val container = "<container><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>"
                for ((name, bytes) in entries + ("META-INF/container.xml" to container.toByteArray())) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            block(file)
        } finally {
            file.delete()
        }
    }
}
