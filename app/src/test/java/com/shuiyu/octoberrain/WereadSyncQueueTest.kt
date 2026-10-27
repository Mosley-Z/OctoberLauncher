package com.shuiyu.octoberrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WereadSyncQueueTest {
    @Test fun `full requests during refresh share one next run and retain callbacks`() {
        val queue = WereadSyncQueue()
        val results = mutableListOf<String>()
        assertTrue(queue.tryStartRefresh())
        assertFalse(queue.submitFull { _, message -> results += "first:$message" })
        assertFalse(queue.submitFull { _, message -> results += "second:$message" })
        val next = queue.finish()
        assertTrue(queue.isRunning())
        assertFalse(queue.tryStartRefresh())
        next.forEach { it(true, "latest key") }
        assertEquals(listOf("first:latest key", "second:latest key"), results)
        assertTrue(queue.finish().isEmpty())
        assertFalse(queue.isRunning())
    }

    @Test fun `request after completion starts immediately`() {
        val queue = WereadSyncQueue()
        assertTrue(queue.submitFull { _, _ -> })
        assertTrue(queue.finish().isEmpty())
        assertTrue(queue.submitFull { _, _ -> })
        assertTrue(queue.isRunning())
    }

    @Test fun `request during followup is retained for another run`() {
        val queue = WereadSyncQueue()
        assertTrue(queue.submitFull { _, _ -> })
        assertFalse(queue.submitFull { _, _ -> })
        assertEquals(1, queue.finish().size)
        assertFalse(queue.submitFull { _, _ -> })
        assertEquals(1, queue.finish().size)
        assertTrue(queue.finish().isEmpty())
        assertFalse(queue.isRunning())
    }
}
