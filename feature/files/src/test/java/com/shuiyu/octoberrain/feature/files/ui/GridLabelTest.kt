package com.shuiyu.octoberrain.feature.files.ui

import com.shuiyu.octoberrain.feature.files.data.FileItem
import org.junit.Assert.assertEquals
import org.junit.Test

class GridLabelTest {
    @Test fun visualFilesDropOnlyTheirRedundantExtension() {
        assertEquals("封面", PaneDraw.gridName(file("封面.JPG", "image/jpeg")))
        assertEquals("小说", PaneDraw.gridName(file("小说.epub", "application/epub+zip")))
        assertEquals("notes.txt", PaneDraw.gridName(file("notes.txt", "text/plain")))
        assertEquals("dir.epub", PaneDraw.gridName(FileItem.dir("dir.epub", "/dir.epub", 0)))
    }

    @Test fun longNamesStopAtTenCodepointsWithoutSplittingEmoji() {
        assertEquals("123456789😀…", PaneDraw.gridName(file("123456789😀后.png", "image/png")))
        assertEquals("123456789😀", PaneDraw.gridName(file("123456789😀.png", "image/png")))
    }

    private fun file(
        name: String,
        mime: String,
    ): FileItem = FileItem.file(name, "/$name", 1, 0, mime)
}
