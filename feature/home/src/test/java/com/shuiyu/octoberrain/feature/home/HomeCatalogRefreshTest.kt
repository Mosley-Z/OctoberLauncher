package com.shuiyu.octoberrain.feature.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class HomeCatalogRefreshTest {
    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var migrated = false
        var calls = 0
        var failure: Exception? = null
        var pending: Continuation<Unit>? = null
        val refresh =
            HomeCatalogRefresh(
                scope,
                { migrated },
                {
                    calls++
                    failure?.let { throw it }
                },
                { milliseconds ->
                    assertEquals(500L, milliseconds)
                    suspendCoroutine { pending = it }
                },
            )

        fun tick() {
            val continuation = checkNotNull(pending)
            pending = null
            continuation.resume(Unit)
        }
    }

    @Test fun migratedStillRefreshesOnResume() {
        val f = Fixture()
        f.migrated = true
        f.refresh.resume()
        assertEquals(1, f.calls)
        assertEquals(null, f.pending)
        f.scope.cancel()
    }

    @Test fun retriesAreBoundedAndDuplicateResumeDoesNotOverlap() {
        val f = Fixture()
        f.refresh.resume()
        f.refresh.resume()
        repeat(6) { f.tick() }
        assertEquals(7, f.calls)
        assertEquals(null, f.pending)
        f.scope.cancel()
    }

    @Test fun pauseBeforeFirstDelayPreventsQueryAndResumeStartsNewSession() {
        val f = Fixture()
        f.refresh.resume()
        f.refresh.pause()
        f.tick()
        assertEquals(1, f.calls)
        f.refresh.resume()
        repeat(6) { f.tick() }
        assertEquals(8, f.calls)
        f.scope.cancel()
    }

    @Test fun pauseAfterTwoRetriesStopsOldSessionWithoutClearingNewJob() {
        val f = Fixture()
        f.refresh.resume()
        repeat(2) { f.tick() }
        val old = checkNotNull(f.pending)
        f.refresh.pause()
        f.refresh.resume()
        old.resume(Unit)
        f.refresh.pause()
        f.tick()
        assertEquals(4, f.calls)
        f.scope.cancel()
    }

    @Test fun migrationDuringDelayStopsRetries() {
        val f = Fixture()
        f.refresh.resume()
        f.migrated = true
        f.tick()
        assertEquals(1, f.calls)
        f.scope.cancel()
    }

    @Test fun ordinaryFailuresRetryButCancellationDoesNot() {
        val f = Fixture()
        f.failure = IllegalStateException()
        f.refresh.resume()
        repeat(6) { f.tick() }
        assertEquals(7, f.calls)
        f.refresh.pause()
        f.failure = CancellationException()
        f.refresh.resume()
        assertEquals(8, f.calls)
        assertEquals(null, f.pending)
        f.scope.cancel()
    }

    @Test fun cancelledInitialQueryCannotRetryOrClearNewJob() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        var initial: Continuation<Unit>? = null
        var waiting: Continuation<Unit>? = null
        var waits = 0
        val refresh =
            HomeCatalogRefresh(
                scope,
                { false },
                {
                    calls++
                    if (calls == 1) suspendCoroutine { initial = it }
                },
                {
                    waits++
                    suspendCoroutine { waiting = it }
                },
            )
        refresh.resume()
        assertEquals(1, calls)
        refresh.pause()
        refresh.resume()
        assertEquals(2, calls)
        assertEquals(1, waits)
        checkNotNull(initial).resume(Unit)
        assertEquals(2, calls)
        assertEquals(1, waits)
        refresh.pause()
        checkNotNull(waiting).resume(Unit)
        assertEquals(2, calls)
        assertEquals(1, waits)
        scope.cancel()
    }

    @Test fun migrationCompletesAtOriginalStableWindow() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var elapsed = 0L
        var migrated = false
        val times = mutableListOf<Long>()
        var waiting: Continuation<Unit>? = null
        val refresh =
            HomeCatalogRefresh(
                scope,
                { migrated },
                {
                    times.add(elapsed)
                    migrated = elapsed >= 2500L
                },
                { milliseconds ->
                    assertEquals(500L, milliseconds)
                    suspendCoroutine { waiting = it }
                    elapsed += milliseconds
                },
            )
        refresh.resume()
        repeat(5) {
            val next = checkNotNull(waiting)
            waiting = null
            next.resume(Unit)
        }
        assertEquals(listOf(0L, 500L, 1000L, 1500L, 2000L, 2500L), times)
        assertEquals(true, migrated)
        assertEquals(null, waiting)
        scope.cancel()
    }
}
