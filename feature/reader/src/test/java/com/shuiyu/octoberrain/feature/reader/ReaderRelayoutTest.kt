package com.shuiyu.octoberrain.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 阅读页重排调度规则的单元测试。 */
class ReaderRelayoutTest {
    @Test
    fun idleSizeChangeRelayouts() {
        val decision =
            ReaderRelayout.onSizeChanged(
                visible = true,
                sessionReady = true,
                busy = false,
                targetWidth = 552,
                targetHeight = 768,
                openedWidth = 480,
                openedHeight = 800,
            )
        assertEquals(ReaderRelayout.Decision.RELAYOUT, decision)
    }

    @Test
    fun sameSizeDoesNothing() {
        val decision =
            ReaderRelayout.onSizeChanged(
                visible = true,
                sessionReady = true,
                busy = false,
                targetWidth = 480,
                targetHeight = 800,
                openedWidth = 480,
                openedHeight = 800,
            )
        assertEquals(ReaderRelayout.Decision.NONE, decision)
    }

    @Test
    fun invisiblePageDoesNothing() {
        val decision =
            ReaderRelayout.onSizeChanged(
                visible = false,
                sessionReady = true,
                busy = false,
                targetWidth = 552,
                targetHeight = 768,
                openedWidth = 480,
                openedHeight = 800,
            )
        assertEquals(ReaderRelayout.Decision.NONE, decision)
    }

    @Test
    fun openingWithoutSessionLeavesDecisionToCompletion() {
        val decision =
            ReaderRelayout.onSizeChanged(
                visible = true,
                sessionReady = false,
                busy = true,
                targetWidth = 552,
                targetHeight = 768,
                openedWidth = 480,
                openedHeight = 800,
            )
        assertEquals(ReaderRelayout.Decision.NONE, decision)
    }

    @Test
    fun sizeChangeDuringActionQueuesInsteadOfRelayouting() {
        val decision =
            ReaderRelayout.onSizeChanged(
                visible = true,
                sessionReady = true,
                busy = true,
                targetWidth = 552,
                targetHeight = 768,
                openedWidth = 480,
                openedHeight = 800,
            )
        assertEquals(ReaderRelayout.Decision.QUEUE, decision)
    }

    @Test
    fun afterActionConsumesQueuedChange() {
        assertTrue(ReaderRelayout.afterAction(visible = true, sessionReady = true, queued = true))
    }

    @Test
    fun afterActionIgnoresQueuedChangeWhenPageHidden() {
        assertFalse(ReaderRelayout.afterAction(visible = false, sessionReady = true, queued = true))
    }

    @Test
    fun afterActionIgnoresQueuedChangeWithoutSession() {
        assertFalse(ReaderRelayout.afterAction(visible = true, sessionReady = false, queued = true))
    }
}
