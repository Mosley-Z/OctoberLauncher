package com.shuiyu.octoberrain.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/** 共享本记状态必须跨实例持久化，并保留原版三个浏览槽的行为。 */
class WereadNoteSelectionTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun selectedBookTraversesEveryNoteBeyondOldTwoHundredLimit() {
        val root = library(1, 250)
        val selection = WereadNoteSelection(root)
        selection.selectBook("b0")
        val ids = mutableSetOf(selection.current()!!.bookmarkId)
        repeat(249) { ids.add(selection.current(advance = true)!!.bookmarkId) }
        assertEquals(250, ids.size)
        assertEquals(250, selection.progress()[1])
        assertEquals("m0", selection.current(advance = true)!!.bookmarkId)
    }

    @Test
    fun sameDaySurvivesNewInstanceAndNewDayAdvancesOnce() {
        val root = library(1, 3)
        var date = "2026-10-21"
        val selection = WereadNoteSelection(root, { date }, Random(4))
        selection.selectBook("b0")
        val initial = selection.current()!!.bookmarkId
        assertEquals(initial, WereadNoteSelection(root, { date }).current()!!.bookmarkId)
        date = "2026-10-22"
        val next = selection.current()!!.bookmarkId
        assertNotEquals(initial, next)
        assertEquals(next, selection.current()!!.bookmarkId)
    }

    @Test
    fun previousFollowsActualVisitedHistoryAfterRestart() {
        val root = library(3, 10)
        val selection = WereadNoteSelection(root, random = Random(7))
        val first = selection.current()!!
        val second = selection.current(advance = true)!!
        selection.current(advance = true)
        val restored = WereadNoteSelection(root)
        assertEquals(second.bookId to second.bookmarkId, restored.previous()!!.let { it.bookId to it.bookmarkId })
        assertEquals(first.bookId to first.bookmarkId, restored.previous()!!.let { it.bookId to it.bookmarkId })
    }

    @Test
    fun appBookSelectionIsSharedWithDesktopWhileIdeasModeIsIsolated() {
        val root = library(2, 3)
        val app = WereadNoteSelection(root)
        app.selectBook("b1")
        val item = app.current()!!
        val desktop = WereadNoteSelection(root)
        assertEquals("b1", desktop.current(desktop = true)!!.bookId)
        app.current(advance = true)
        assertNotEquals(item.bookmarkId, desktop.current(desktop = true)!!.bookmarkId)
        app.setIdeasOnly(true)
        assertEquals(1, app.slot())
        assertEquals(0, desktop.slot(desktop = true))
        assertTrue(app.current()!!.hasIdea())
        assertEquals(2, app.progress()[1])
        assertEquals(8, desktop.progress(desktop = true)[1])
    }

    @Test
    fun defaultSlotProgressSurvivesTemporaryBookFilter() {
        val root = library(2, 3)
        val selection = WereadNoteSelection(root)
        val item = selection.current()!!
        selection.selectBook("b1")
        selection.current()
        selection.selectBook("")
        assertEquals(item.bookId to item.bookmarkId, selection.current()!!.let { it.bookId to it.bookmarkId })
    }

    @Test
    fun migratesOriginalCurrentAndBatchWithoutCopyingUnrelatedSecrets() {
        val root = library(1, 3)
        File(root, "kernel_prefs.json").writeText(
            """{"string":{"pick_book":"b0","day_p":"2026-10-21",
                "cur_book_p":"b0","cur_key_p":"m1","api_key":"private"},
                "int":{"batch_pos_p":1,"taken_p":2}}""",
        )
        File(
            root,
            "nt_batch_p.json",
        ).writeText("""{"items":[{"b":"b0","k":"m0"},{"b":"b0","k":"m1"},{"b":"b0","k":"m2"}]}""")
        val selection = WereadNoteSelection(root, { "2026-10-21" })
        assertEquals("m1", selection.current()!!.bookmarkId)
        assertEquals("m2", selection.current(advance = true)!!.bookmarkId)
        assertTrue(!File(root, "note_selection.json").readText().contains("private"))
    }

    @Test
    fun ideasMergeByRangeAndHistoricalBooksRemainReachable() {
        val root = library(230, 1)
        val library = WereadNoteLibrary(root)
        assertEquals(230, library.books().size)
        val items = library.items("b229")
        assertEquals(1, items.size)
        assertTrue(items.first().hasIdea())
        assertEquals("Book 229", items.first().title)
        val selection = WereadNoteSelection(root)
        selection.selectBook("b229")
        assertEquals("b229", selection.current()!!.bookId)
    }

    @Test
    fun sameRangeInDifferentChaptersDoesNotAttachIdeaToWrongMark() {
        val root = library(1, 1)
        File(root, "nt_b0.json").writeText(
            """{"marks":[{"bookmarkId":"m1","markText":"same","range":"r0","chapterUid":1}]}""",
        )
        File(root, "ni_b0.json").writeText(
            """{"ideas":[{"reviewId":"i1","content":"thought","abstract":"same","range":"r0","chapterUid":2}]}""",
        )
        val notes = WereadNoteLibrary(root).items("b0")
        assertEquals(2, notes.size)
        assertTrue(!notes.first().hasIdea())
        assertEquals(2, notes.last().chapterUid)
    }

    @Test
    fun automaticBookIgnoresCachedManualBookAndUsesLatestReadTime() {
        val root = temporary.newFolder()
        File(root, "shelf.json").writeText(
            """{"books":[{"bookId":"manual","readUpdateTime":20},{"bookId":"recent","readUpdateTime":100}]}""",
        )
        File(root, "book.json").writeText("""{"bookId":"manual"}""")
        assertEquals("recent", WereadBookSelection.automaticBookId(root))
    }

    private fun library(
        bookCount: Int,
        markCount: Int,
    ): File {
        val root = temporary.newFolder()
        val books =
            (0 until bookCount).map { book ->
                File(root, "nt_b$book.json").writeText(
                    """{"marks":[${(0 until markCount).joinToString {
                        """{"bookmarkId":"m$it","markText":"text $it","range":"r$it"}"""
                    }}]}""",
                )
                File(root, "ni_b$book.json").writeText(
                    """{"ideas":[{"reviewId":"i1","content":"thought","abstract":"text 0","range":"r0"}]}""",
                )
                """{"bookId":"b$book","title":"Book $book","noteCount":$markCount,"reviewCount":1}"""
            }
        File(root, "nt_index.json").writeText("""{"books":[${books.joinToString()}]}""")
        return root
    }
}
