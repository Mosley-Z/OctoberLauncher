package com.shuiyu.octoberrain.feature.reader.source

import android.os.ParcelFileDescriptor
import com.shuiyu.octoberrain.core.common.DocumentMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveIdentityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun entryRetainsParentUriAliasWithEncodingPathAndCrc() {
        val file = temporary.newFile("source.zip")
        ZipOutputStream(file.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("a.txt"))
            output.write("fixture".toByteArray())
            output.closeEntry()
        }
        val parent =
            object : DocumentHandle {
                override val metadata =
                    DocumentMetadata("path:fixture", "source.zip", null, file.length(), 1, true, listOf("uri:fixture"))

                override fun openStream() = file.inputStream()

                override fun archiveFile(): File = file

                override fun openSeekable(): ParcelFileDescriptor = error("No Android descriptor needed")

                override fun close() = Unit
            }
        ArchiveEntryHandle(parent, "a.txt", "UTF-8", SourceCache(temporary.newFolder())).use { entry ->
            val suffix = entry.metadata.id.removePrefix("path:fixture")
            assertTrue(suffix.startsWith("!UTF-8:a.txt:"))
            assertEquals(listOf("uri:fixture$suffix"), entry.metadata.aliases)
        }
    }
}
