package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageImportBatchTest {
    @Test fun cancellationCopiesAndDiscardsNothing() {
        var touched = false
        val result =
            importImageBatch<Int, String>(
                emptyList(),
                {
                    touched = true
                    "copy"
                },
                { touched = true },
            )
        assertEquals(emptyList<String>(), result)
        assertEquals(false, touched)
    }

    @Test fun partialFailureDiscardsOnlyThisAttemptAndDoesNotPublish() {
        val previous = listOf("previous")
        var collection = previous
        val discarded = mutableListOf<String>()
        val result =
            importImageBatch(
                listOf(1, 2, 3),
                {
                    if (it == 2) null else "copy-$it"
                },
                { discarded.add(it) },
            )
        if (result != null) collection = result
        assertNull(result)
        assertEquals(previous, collection)
        assertEquals(listOf("copy-1"), discarded)
    }

    @Test fun exceptionsRollBackAndSuccessKeepsSourceOrder() {
        val discarded = mutableListOf<String>()
        assertNull(
            importImageBatch(
                listOf(1, 2),
                {
                    check(it != 2)
                    "copy-$it"
                },
                { discarded.add(it) },
            ),
        )
        assertEquals(listOf("copy-1"), discarded)
        assertEquals(
            listOf("copy-2", "copy-1"),
            importImageBatch(listOf(2, 1), { "copy-$it" }, { error("Must not discard") }),
        )
    }
}
