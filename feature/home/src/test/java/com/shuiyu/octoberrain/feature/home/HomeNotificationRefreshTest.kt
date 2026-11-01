package com.shuiyu.octoberrain.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNotificationRefreshTest {
    @Test fun backgroundBurstConsumesLatestStateOnceAfterVisibility() {
        val state = HomeNotificationRefresh()
        var latest = 0
        val painted = mutableListOf<Int>()
        repeat(100) {
            latest = it
            state.changed(false) { painted.add(latest) }
        }
        assertEquals(emptyList<Int>(), painted)
        state.resume { painted.add(latest) }
        state.resume { painted.add(latest) }
        assertEquals(listOf(99), painted)
    }

    @Test fun foregroundUpdatesRemainImmediateAndCleanResumeDoesNothing() {
        val state = HomeNotificationRefresh()
        var calls = 0
        state.resume { calls++ }
        repeat(3) { state.changed(true) { calls++ } }
        state.resume { calls++ }
        assertEquals(3, calls)
        state.changed(false) { calls++ }
        state.resume { calls++ }
        state.changed(true) { calls++ }
        assertEquals(5, calls)
    }

    @Test fun recreatedSubscriptionInitialValueMarksDirty() {
        repeat(2) {
            val state = HomeNotificationRefresh()
            var calls = 0
            state.changed(false) { calls++ }
            state.resume { calls++ }
            assertEquals(1, calls)
        }
    }

    @Test fun clearDiscardsDestroyedSessionsPendingState() {
        val state = HomeNotificationRefresh()
        var calls = 0
        state.changed(false) { calls++ }
        state.clear()
        state.resume { calls++ }
        assertEquals(0, calls)
        state.changed(false) { calls++ }
        state.resume { calls++ }
        assertEquals(1, calls)
    }
}
