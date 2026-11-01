package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageDirectoryIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun realDirectorySizesFilterSortAndReuseTwentyLoads() {
        listOf(0, 1, 100, 1000).forEach { size ->
            val fixture = Fixture(temporaryFolder.newFolder("size-$size"))
            val images =
                (0 until size).reversed().map { index ->
                    fixture.file("image-${index.toString().padStart(4, '0')}.${if (index % 2 == 0) "PNG" else "jpg"}")
                }
            fixture.file("notes.txt")
            fixture.file("almost.png.txt")
            assertTrue(File(fixture.directory, "directory.png").mkdir())
            // Zero candidates deliberately selects a nonexistent legacy source: resolver returns empty.
            fixture.selected = images.firstOrNull() ?: File(fixture.directory, "missing.png")
            val expected = images.sortedBy { it.name }
            repeat(20) {
                assertEquals("size=$size load=$it", expected, fixture.load().files)
            }
            assertEquals("actual resolver calls for size=$size", 1, fixture.scans)
        }
    }

    @Test
    fun selectedNonimageLegacyFileRemainsInOriginalSortedFallback() {
        val fixture = Fixture(temporaryFolder.newFolder())
        fixture.selected = fixture.file("selected.custom")
        assertEquals(listOf(fixture.selected), fixture.load().files)
        val image = fixture.file("A.PNG")
        fixture.file("ignored.txt")
        fixture.event()
        assertEquals(listOf(image, fixture.selected), fixture.load().files)
        assertEquals(2, fixture.scans)
    }

    @Test
    fun createDeleteAndRenameEventsDiscoverActualDirectoryOnFreshRequests() {
        val fixture = Fixture(temporaryFolder.newFolder())
        fixture.selected = fixture.file("selected.png")
        val sibling = fixture.file("b.jpg")
        assertEquals(listOf(sibling, fixture.selected), fixture.load().files)
        val created = fixture.file("a.webp")
        fixture.event()
        assertEquals(listOf(created, sibling, fixture.selected), fixture.load().files)
        assertTrue(sibling.delete())
        fixture.event()
        assertEquals(listOf(created, fixture.selected), fixture.load().files)
        val renamed = File(fixture.directory, "z.gif")
        assertTrue(created.renameTo(renamed))
        fixture.event()
        assertEquals(listOf(fixture.selected, renamed), fixture.load().files)
        assertEquals(4, fixture.scans)
    }

    @Test
    fun deletedOriginalLegacySourceReturnsEmptyDespiteSurvivingSibling() {
        val fixture = Fixture(temporaryFolder.newFolder())
        fixture.selected = fixture.file("selected.png")
        val sibling = fixture.file("sibling.jpg")
        assertEquals(listOf(fixture.selected, sibling), fixture.load().files)
        assertTrue(fixture.selected.delete())
        fixture.event()
        assertEquals(emptyList<File>(), fixture.load().files)
        assertTrue(sibling.isFile)
        assertEquals(emptyList<File>(), fixture.load().files)
        assertEquals(2, fixture.scans)
    }

    @Test
    fun injectedNullProbeRescansRealDirectoryAndRecoversTrustedReuse() {
        val fixture = Fixture(temporaryFolder.newFolder())
        fixture.selected = fixture.file("selected.png")
        fixture.load()
        // Deterministic untrusted metadata injection, not a physical permission/chmod test.
        fixture.trusted = false
        val added = fixture.file("a.jpg")
        val untrusted = fixture.load()
        assertFalse(untrusted.reusable)
        assertEquals(listOf(added, fixture.selected), untrusted.files)
        assertTrue(added.delete())
        assertEquals(listOf(fixture.selected), fixture.load().files)
        assertEquals(3, fixture.scans)
        fixture.trusted = true
        assertTrue(fixture.load().reusable)
        assertEquals(4, fixture.scans)
        assertEquals(listOf(fixture.selected), fixture.load().files)
        assertEquals(4, fixture.scans)
        val recovered = fixture.file("recovered.bmp")
        fixture.event()
        assertEquals(listOf(recovered, fixture.selected), fixture.load().files)
        assertEquals(5, fixture.scans)
    }

    private class Fixture(
        val directory: File,
    ) {
        var selected = File(directory, "missing.png")
        var trusted = true
        var scans = 0
        private val filesDir = File(directory, "private-files")

        // Fixed trusted stamp isolates event invalidation from host filesystem timestamp resolution.
        private val cache =
            ImageDirectoryCache(clock = { 0L }, probe = { if (trusted) 1L else null }) { key ->
                scans++
                WidgetImageFiles.resolveSelection(emptyList(), key.legacyPath, key.legacySource, filesDir) { false }
            }

        private fun key() = ImageDirectoryCache.Key(0, directory.canonicalPath, selected.absolutePath, null)

        fun load(): ImageDirectoryCache.Result = requireNotNull(cache.load(cache.request(key())))

        fun event() = cache.onEvent(cache.request(key()))

        // Marker contents exercise file enumeration only; no bitmap decoding is attempted.
        fun file(name: String): File = File(directory, name).apply { writeText("fixture marker") }
    }
}
