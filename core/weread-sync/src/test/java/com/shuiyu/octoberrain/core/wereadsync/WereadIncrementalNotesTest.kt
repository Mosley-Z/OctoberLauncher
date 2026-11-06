package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WereadIncrementalNotesTest {
    @get:Rule val folder = TemporaryFolder()
    private var now = 1_800_000_000_000L
    private val checked = mutableListOf<String>()
    private var failIdeas = false
    private var changed = false
    private var failBook = ""
    private var missingVersion = false
    private var nonEmptyBodies = false

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val request =
        WereadRequest { api, body ->
            when (api) {
                "/user/notebooks" ->
                    json(
                        """{"books":[${listOf("a", "b", "c").joinToString {
                            if (missingVersion && it == "c") {
                                """{"bookId":"c"}"""
                            } else {
                                """{"bookId":"$it","sort":1,"noteCount":${if (changed && it == "c") 2 else 1},"reviewCount":${if (nonEmptyBodies) 1 else 0}}"""
                            }
                        }}],"hasMore":0}""",
                    )
                "/book/bookmarklist" -> {
                    checked += body["bookId"].toString().trim('"')
                    if (body["bookId"].toString().trim('"') == failBook) {
                        json("""{"malformed":true}""")
                    } else {
                        json("""{"updated":[],"chapters":[]}""")
                    }
                }
                "/review/list/mine" ->
                    if (failIdeas) {
                        null
                    } else if (nonEmptyBodies) {
                        json("""{"reviews":[{"reviewId":"r","content":"thought","abstract":"other"}],"hasMore":0}""")
                    } else {
                        json("""{"reviews":[],"hasMore":0}""")
                    }
                else -> error(api)
            }
        }

    private fun sync(reading: Long = 10): WereadNoteBatch =
        WereadNoteSync(request, folder.root) { now }
            .incremental(listOf(WereadShelfBook("a", "A", "", "", 0, -1, false, 0, readUpdateTime = reading))) {}!!

    @Test fun repeatedRefreshChecksRecentAndOnlyOneRotatingBookWithoutRewritingUnchangedBodies() {
        assertTrue(sync().complete)
        assertEquals(listOf("a", "b", "c"), checked)
        val original = File(folder.root, "nt_a.json").readText()
        checked.clear()
        now += 1000
        assertTrue(sync().complete)
        assertEquals(listOf("a", "b"), checked)
        assertEquals(original, File(folder.root, "nt_a.json").readText())
        checked.clear()
        now += 1000
        sync()
        assertEquals(listOf("a", "c"), checked)
    }

    @Test fun indexChangesRemainCandidatesEvenAfterAnotherConsumerPublishesIndex() {
        sync()
        checked.clear()
        changed = true
        now += 1000
        assertTrue(sync().complete)
        assertTrue("c" in checked)
    }

    @Test
    fun changedReadingStateChecksMultipleBooksWithoutChangingSelection() {
        val selection = File(folder.root, "note_selection.json")
        selection.writeText("""{"pick_book":"c","day":"2026-11-06","hist":"preserve"}""")
        val before = selection.readText()
        sync()
        checked.clear()
        now += 1000
        val shelf =
            listOf(
                WereadShelfBook("a", "A", "", "", 0, -1, false, 0, readUpdateTime = 20),
                WereadShelfBook("c", "C", "", "", 0, -1, false, 0, readUpdateTime = 15),
            )
        assertTrue(WereadNoteSync(request, folder.root) { now }.incremental(shelf) {}!!.complete)
        assertTrue("a" in checked && "c" in checked)
        assertEquals(before, selection.readText())
    }

    @Test
    fun unchangedHistoricalBodiesRemainCachedBeyondSixHours() {
        sync()
        now += 7 * 60 * 60 * 1000
        checked.clear()
        sync()
        assertEquals(2, checked.size)
        assertTrue("a" in checked)
    }

    @Test fun failedIdeasKeepOldCacheAndDoNotAdvanceSuccessfulBaseline() {
        sync()
        val before = File(folder.root, "note_checks.json").readText()
        val ideas = File(folder.root, "ni_a.json").readText()
        failIdeas = true
        now += 1000
        assertFalse(sync(reading = 20).complete)
        assertEquals(ideas, File(folder.root, "ni_a.json").readText())
        val baseline = json(File(folder.root, "note_checks.json").readText())["a"]!!.jsonObject
        assertEquals("10", baseline["readUpdateTime"].toString())
        assertTrue(before.isNotEmpty())
    }

    @Test
    fun historicalFailureRemainsIncompleteDuringCooldownAndRetriesWithPriority() {
        sync()
        now += 1000
        failBook = "b"
        assertFalse(sync().complete)
        now += 1000
        checked.clear()
        val cooling = sync()
        assertFalse(cooling.complete)
        assertTrue(cooling.remainingBooks > 0)
        assertFalse("b" in checked)
        failBook = ""
        now += 31 * 60 * 1000
        checked.clear()
        assertTrue(sync().complete)
        assertEquals(listOf("a", "b"), checked.take(2))
        val baseline = json(File(folder.root, "note_checks.json").readText())["b"]!!.jsonObject
        assertFalse(baseline.containsKey("retryAt"))
    }

    @Test
    fun malformedRecentBookDoesNotStarveChangedHistoricalBooks() {
        sync()
        failBook = "a"
        changed = true
        checked.clear()
        now += 1000
        assertFalse(sync().complete)
        assertTrue("c" in checked)
    }

    @Test
    fun missingIndexVersionDoesNotInheritOldSuccessfulVersion() {
        sync()
        missingVersion = true
        repeat(2) {
            checked.clear()
            now += 1000
            assertTrue(sync().complete)
            assertTrue("c" in checked)
        }
    }

    @Test
    fun globalFailureStopsFurtherBookRequests() {
        sync()
        failBook = "a"
        changed = true
        checked.clear()
        val offline =
            object : WereadRequest {
                override fun request(
                    apiName: String,
                    business: JsonObject,
                ): JsonObject? = request.request(apiName, business)

                override fun stopAfterFailure(): Boolean = true
            }
        val shelf = listOf(WereadShelfBook("a", "A", "", "", 0, -1, false, 0, readUpdateTime = 10))
        assertFalse(WereadNoteSync(offline, folder.root) { now }.incremental(shelf) {}!!.complete)
        assertEquals(listOf("a"), checked)
    }

    @Test
    fun missingCountsKeepCachedBookInBothConsumerPoolsWhileVersionRemainsUnknown() {
        nonEmptyBodies = true
        sync()
        val before =
            com.shuiyu.octoberrain.core.data
                .WereadNoteLibrary(folder.root)
                .total(false, "")
        missingVersion = true
        repeat(2) {
            checked.clear()
            now += 1000
            assertTrue(sync().complete)
            assertTrue("c" in checked)
            val library =
                com.shuiyu.octoberrain.core.data
                    .WereadNoteLibrary(folder.root)
            assertTrue(library.candidates(false).any { it.first == "c" })
            assertTrue(library.candidates(true).any { it.first == "c" })
            assertTrue(library.items("c").isNotEmpty())
            assertEquals(before, library.total(false, ""))
        }
    }
}
