package com.shuiyu.octoberrain.feature.content.ui.negative

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WereadFingerprintBooksTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun notebookIndexControlsCandidatesAndMissingNotesAreVisible() {
        val root = folder.newFolder()
        File(root, "nt_index.json").writeText(
            """{"books":[
                {"bookId":"a","book":{"title":"A","author":"Author"}},
                {"bookId":"b","title":"B"},{"bookId":"a","title":"duplicate"}
            ]}""",
        )
        File(root, "shelf.json").writeText("""{"books":[{"bookId":"unread","title":"Unread"}]}""")
        File(root, "nt_a.json").writeText("""{"marks":[]}""")
        val candidates = WereadFingerprintBooks.candidates(root)
        assertEquals(listOf("a", "b"), candidates.map { it[0] })
        assertEquals("A", candidates[0][1])
        assertEquals("Author", candidates[0][2])
        assertEquals("B（笔记未同步）", candidates[1][1])
    }
}
