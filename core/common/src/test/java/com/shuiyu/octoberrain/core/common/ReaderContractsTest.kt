package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderContractsTest {
    @Test fun sourceRoundTripDoesNotNeedLiveResources() {
        val source: DocumentRef = DocumentRef.ArchiveEntry(DocumentRef.ContentUri("content://fixture/book"), "正文/a.txt")
        assertEquals(source, AppJson.format.decodeFromString<DocumentRef>(AppJson.format.encodeToString(source)))
    }

    @Test fun textLocatorKeepsByteAnchorAndEncodingAcrossRestart() {
        val locator: ReaderLocator = ReaderLocator.Text(50000000, "GB18030")
        assertEquals(locator, AppJson.format.decodeFromString<ReaderLocator>(AppJson.format.encodeToString(locator)))
    }

    @Test fun oldPdfLocatorKeepsDefaultsAndNewViewportRoundTrips() {
        assertEquals(
            ReaderLocator.Pdf(2),
            AppJson.format.decodeFromString<ReaderLocator>("{\"type\":\"pdf\",\"page\":2}"),
        )
        val locator: ReaderLocator = ReaderLocator.Pdf(6, 3f, 0.3f, 0.7f, true)
        assertEquals(locator, AppJson.format.decodeFromString<ReaderLocator>(AppJson.format.encodeToString(locator)))
    }

    @Test fun markdownTextAnchorRoundTripsIndependentlyOfScreenPageCount() {
        val locator: ReaderLocator = ReaderLocator.Markdown(1850, 0.3)
        assertEquals(locator, AppJson.format.decodeFromString<ReaderLocator>(AppJson.format.encodeToString(locator)))
    }

    @Test fun archiveEncodingAndEpubSpineSurviveSerialization() {
        val ref: DocumentRef = DocumentRef.ArchiveEntry(DocumentRef.LocalPath("fixture.zip"), "目录/正文.txt", "GB18030")
        assertEquals(ref, AppJson.format.decodeFromString<DocumentRef>(AppJson.format.encodeToString(ref)))
        val locator: ReaderLocator = ReaderLocator.Epub(3, 4200, 0.75)
        assertEquals(locator, AppJson.format.decodeFromString<ReaderLocator>(AppJson.format.encodeToString(locator)))
    }
}
