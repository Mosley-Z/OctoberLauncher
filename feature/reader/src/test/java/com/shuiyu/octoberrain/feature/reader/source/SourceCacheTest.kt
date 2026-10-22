package com.shuiyu.octoberrain.feature.reader.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SourceCacheTest {
    @Test fun reservationsBoundLazyEntriesAndReleaseWithoutDeletingUnownedFiles() {
        val directory = Files.createTempDirectory("reader-cache-").toFile()
        val stale = File(directory, "source-stale.cache").apply { writeText("stale") }
        val unrelated = File(directory, "keep.txt").apply { writeText("keep") }
        try {
            val cache = SourceCache(directory)
            cache.allocate(80L * 1024 * 1024).use { first ->
                assertFalse(stale.exists())
                assertTrue(unrelated.exists())
                assertThrows(ReaderSourceException::class.java) { cache.allocate(60L * 1024 * 1024) }
                first.file.writeText("small")
                first.compact()
                cache.allocate(60L * 1024 * 1024).use { assertEquals(0L, it.file.length()) }
            }
            assertEquals(listOf("keep.txt"), directory.listFiles().orEmpty().map { it.name })
        } finally {
            directory.listFiles().orEmpty().forEach { it.delete() }
            directory.delete()
        }
    }
}
