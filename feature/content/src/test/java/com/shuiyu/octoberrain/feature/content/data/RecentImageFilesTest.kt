package com.shuiyu.octoberrain.feature.content.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecentImageFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun newerBooksNeverDisplaceImagesAndNestedPicturesAreFound() {
        val root = folder.newFolder("Pictures")
        repeat(8) {
            java.io.File(root, "$it.epub").apply {
                writeText("book")
                setLastModified(9000L)
            }
        }
        val nested = java.io.File(root, "nested").apply { mkdirs() }
        val old =
            java.io.File(nested, "old.PNG").apply {
                writeText("image")
                setLastModified(1000L)
            }
        val fresh =
            java.io.File(root, "fresh.jpg").apply {
                writeText("image")
                setLastModified(2000L)
            }
        assertEquals(listOf(fresh, old), RecentImageFiles.collect(listOf(root, root), 4))
        assertEquals(listOf(fresh), RecentImageFiles.collect(listOf(root), 1))
    }
}
