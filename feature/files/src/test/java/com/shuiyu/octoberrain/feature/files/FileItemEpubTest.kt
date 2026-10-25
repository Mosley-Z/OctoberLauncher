package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.data.FileItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileItemEpubTest {
    @Test fun epubPreviewDoesNotTurnDocumentIntoImage() {
        val item = FileItem.file("BOOK.EPUB", "/books/BOOK.EPUB", 1, 1, "application/octet-stream")
        assertTrue(item.isEpub)
        assertTrue(item.hasThumbnail)
        assertFalse(item.isImage)
        assertFalse(item.isEditableImage)
        assertFalse(FileItem.dir("book.epub", "/book.epub", 1).isEpub)
    }

    @Test fun mimeCanIdentifyEpubWithoutExtension() {
        assertTrue(FileItem.file("book", "/book", 1, 1, "application/epub+zip").isEpub)
        assertFalse(FileItem.file("book.txt", "/book.txt", 1, 1, "text/plain").hasThumbnail)
    }
}
