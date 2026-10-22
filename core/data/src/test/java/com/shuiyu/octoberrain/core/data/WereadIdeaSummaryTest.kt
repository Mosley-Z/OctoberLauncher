package com.shuiyu.octoberrain.core.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WereadIdeaSummaryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun summaryTracksChangedAndRemovedBooksWithoutRetainingWholeLibrary() {
        val root = temporary.newFolder()
        val index = File(root, "nt_index.json")
        index.writeText("""{"books":[{"bookId":"a"},{"bookId":"b"}]}""")
        File(root, "ni_a.json").writeText("""{"ideas":[{"reviewId":"1","content":"abc"}]}""")
        File(root, "ni_b.json").writeText("""{"ideas":[{"reviewId":"2","content":"de"}]}""")
        val summary = WereadIdeaSummary(root)
        assertEquals(5, summary.characters())
        assertEquals(5, summary.characters())
        File(root, "ni_a.json").writeText("""{"ideas":[{"reviewId":"1","content":"abcdef"}]}""")
        assertEquals(8, summary.characters())
        index.writeText("""{"books":[{"bookId":"a"}]}""")
        assertEquals(6, summary.characters())
        File(root, "ni_a.json").delete()
        assertEquals(0, summary.characters())
    }

    @Test fun legacyArrayIndexAndMergedIdeaKeepSameCharacterCount() {
        val root = temporary.newFolder()
        File(root, "nt_index.json").writeText("""[{"bookId":"a"}]""")
        File(root, "nt_a.json").writeText("""{"marks":[{"bookmarkId":"m","markText":"x","range":"r"}]}""")
        File(root, "ni_a.json").writeText("""{"ideas":[{"reviewId":"i","abstract":"x","range":"r","content":"abc"}]}""")
        assertEquals(3, WereadIdeaSummary(root).characters())
    }
}
