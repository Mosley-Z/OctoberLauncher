package com.shuiyu.octoberrain.feature.content.data

import com.shuiyu.octoberrain.core.common.WereadShelfItem
import org.junit.Assert.assertEquals
import org.junit.Test

class WereadShelfOrderTest {
    private fun candidate(
        id: String,
        time: Long = 0,
    ): WereadShelfCandidate =
        WereadShelfCandidate(
            WereadShelfItem(id, id, "", null, 0f, null),
            time,
        )

    @Test fun recentBookBeyondOldFortyItemWindowIsSelectedBeforeLimit() {
        val candidates = (0 until 100).map { candidate(it.toString(), 10) }
        val selected = selectWereadShelf(candidates, mapOf("99" to 1000), 30)
        assertEquals(30, selected.size)
        assertEquals("99", selected.first().bookId)
        assertEquals((0 until 29).map(Int::toString), selected.drop(1).map { it.bookId })
    }

    @Test fun newerRemoteTimeWinsAndTiesPreserveOriginalOrder() {
        val candidates = listOf(candidate("remote", 300), candidate("local", 1), candidate("tie", 200))
        assertEquals(
            listOf("remote", "local", "tie"),
            selectWereadShelf(candidates, mapOf("local" to 200), 10).map { it.bookId },
        )
    }

    @Test fun reorderingNeverAddsUnknownBooksToSavedMembership() {
        val saved = listOf(candidate("saved"))
        assertEquals(listOf("saved"), selectWereadShelf(saved, mapOf("new" to 1000), 10).map { it.bookId })
    }
}
