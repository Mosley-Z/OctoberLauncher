package com.shuiyu.octoberrain.feature.reader.source

import com.shuiyu.octoberrain.core.common.ReaderFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentKindsTest {
    @Test fun magicOverridesMisleadingExtensionAndMime() {
        assertEquals(ReaderFormat.PDF, DocumentKinds.detect("a.txt", "text/plain", "%PDF-1.4".toByteArray()))
        assertEquals(ReaderFormat.UNKNOWN, DocumentKinds.detect("a.txt", "text/plain", byteArrayOf(0, 1, 2)))
        assertEquals(ReaderFormat.ZIP, DocumentKinds.detect("a.pdf", "application/pdf", byteArrayOf(80, 75, 3, 4)))
    }

    @Test fun utf16AndUnknownLengthTextRemainReadableCandidates() {
        assertEquals(ReaderFormat.TEXT, DocumentKinds.detect("a.txt", null, byteArrayOf(-1, -2, 97, 0)))
        assertEquals(ReaderFormat.TEXT, DocumentKinds.detect("无扩展名", "text/plain", "正文".toByteArray()))
        assertEquals(ReaderFormat.UNKNOWN, DocumentKinds.detect("a.bin", null, "data".toByteArray()))
    }

    @Test fun zipContainerMarksTakePrecedenceOverExtension() {
        val pk = byteArrayOf(80, 75, 3, 4)
        assertEquals(ReaderFormat.EPUB, DocumentKinds.detect("无扩展名", null, pk, ZipContainer.EPUB))
        assertEquals(ReaderFormat.DOCX, DocumentKinds.detect("a.zip", null, pk, ZipContainer.DOCX))
        assertEquals(ReaderFormat.SHEET, DocumentKinds.detect("a.zip", null, pk, ZipContainer.SHEET))
        assertEquals(ReaderFormat.SLIDES, DocumentKinds.detect("a.zip", null, pk, ZipContainer.SLIDES))
        assertEquals(ReaderFormat.ZIP, DocumentKinds.detect("a.docx", null, pk, ZipContainer.ZIP))
    }

    @Test fun missingContainerKeepsExtensionFallbackForZipHeader() {
        val pk = byteArrayOf(80, 75, 3, 4)
        assertEquals(ReaderFormat.DOCX, DocumentKinds.detect("a.docx", null, pk))
        assertEquals(ReaderFormat.ZIP, DocumentKinds.detect("a.tar", null, pk))
        assertEquals(ReaderFormat.TEXT, DocumentKinds.detect("a.txt", "text/plain", "正文".toByteArray()))
    }

    @Test fun zipHeaderRequiresFourBytesAndPkMagic() {
        assertTrue(DocumentKinds.isZipHeader(byteArrayOf(80, 75, 3, 4)))
        assertFalse(DocumentKinds.isZipHeader(byteArrayOf(80, 75, 3)))
        assertFalse(DocumentKinds.isZipHeader(byteArrayOf(80, 76, 3, 4)))
        assertFalse(DocumentKinds.isZipHeader(byteArrayOf()))
    }
}
