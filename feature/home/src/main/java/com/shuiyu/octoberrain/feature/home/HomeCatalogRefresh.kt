package com.shuiyu.octoberrain.feature.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Owns the refresh sequence for one visible Home session; called on the main thread. */
internal class HomeCatalogRefresh(
    private val scope: CoroutineScope,
    private val migrated: () -> Boolean,
    private val refresh: suspend () -> Unit,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private var visible = false
    private var generation = 0L
    private var job: Job? = null

    fun resume() {
        if (visible) return
        visible = true
        val session = ++generation
        val next =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    refreshSafely()
                    repeat(6) {
                        currentCoroutineContext().ensureActive()
                        if (!visible || generation != session || migrated()) return@launch
                        wait(500L)
                        currentCoroutineContext().ensureActive()
                        if (!visible || generation != session || migrated()) return@launch
                        refreshSafely()
                    }
                } finally {
                    if (generation == session) job = null
                }
            }
        job = next
        next.start()
    }

    fun pause() {
        visible = false
        generation++
        job?.cancel()
        job = null
    }

    private suspend fun refreshSafely() {
        currentCoroutineContext().ensureActive()
        try {
            refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A failed query still gets only this session's bounded retries.
        }
    }
}
