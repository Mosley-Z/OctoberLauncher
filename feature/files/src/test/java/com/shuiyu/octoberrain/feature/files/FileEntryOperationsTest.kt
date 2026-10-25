package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.data.FileEntryMetadata
import com.shuiyu.octoberrain.feature.files.data.deleteFileEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileEntryOperationsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun metadataReadsActualSizeAndDeletionAffectsOnlySelectedFile() {
        val chosen = temp.newFile("chosen.txt").apply { writeText("hello") }
        val retained = temp.newFile("retained.txt").apply { writeText("keep") }
        assertEquals(5L, FileEntryMetadata.read(chosen)?.bytes)
        chosen.appendText(" more")
        assertEquals(10L, FileEntryMetadata.read(chosen)?.bytes)
        assertTrue(deleteFileEntry(chosen))
        assertFalse(chosen.exists())
        assertEquals("keep", retained.readText())
        assertNull(FileEntryMetadata.read(chosen))
        assertFalse(deleteFileEntry(chosen))
    }

    @Test
    fun directoryIsNeverRecursivelyDeleted() {
        val folder = temp.newFolder("folder")
        val child = java.io.File(folder, "kept.txt").apply { writeText("keep") }
        assertTrue(FileEntryMetadata.read(folder)?.directory == true)
        assertFalse(deleteFileEntry(folder))
        assertTrue(child.exists())
    }
}
