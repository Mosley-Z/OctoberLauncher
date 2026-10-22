package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.RecentDocument
import com.shuiyu.octoberrain.feature.content.ContentRegistry
import com.shuiyu.octoberrain.feature.content.ui.RecentBooksSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ShelfDisplayState(
    val snapshot: ShelfSnapshot? = null,
    val recent: List<RecentDocument> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false,
)

/** 书架前台生命周期：普通显示只读本地；本书共享数据变化时重读，不独立轮询。 */
internal class ShelfLoader(
    context: Context,
    private val publish: (ShelfDisplayState) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = ShelfDisplayRepository(context)
    private var work: Job? = null
    private var observer: Job? = null
    private var foreground = false
    private var generation = 0
    private var key = ""
    private var state = ShelfDisplayState()

    fun resume() {
        foreground = true
        load(false)
        observer?.cancel()
        observer =
            scope.launch {
                ContentRegistry.source()?.dataVersion?.drop(1)?.collect {
                    if (foreground &&
                        !state.busy &&
                        AppServices.config.state.value.desktop.negativePage.effectiveShelfSource ==
                        ShelfSourceSupport.WEREAD
                    ) {
                        load(false)
                    }
                }
            }
    }

    fun pause() {
        foreground = false
        generation++
        observer?.cancel()
        work?.cancel()
    }

    fun release() {
        pause()
        scope.cancel()
    }

    fun refresh() {
        if (foreground && !state.busy) load(true, "manual")
    }

    private fun load(
        refresh: Boolean,
        trigger: String = "display",
    ) {
        work?.cancel()
        val config = AppServices.config.state.value
        val page = config.desktop.negativePage
        val source = page.effectiveShelfSource
        val directory = config.integrations.booksDir
        val nextKey = if (source == ShelfSourceSupport.LOCAL) "$source:$directory" else source
        if (nextKey != key) state = ShelfDisplayState()
        key = nextKey
        val mark = ++generation
        state = state.copy(busy = refresh, message = null)
        publish(state)
        work =
            scope.launch {
                try {
                    val next =
                        withContext(Dispatchers.IO) {
                            readState(source, directory, refresh)
                        }
                    if (foreground && mark == generation) {
                        state = next
                        publish(state)
                        if (refresh) {
                            InkLog.d("ShelfRefresh", "trigger=$trigger source=$source success=${next.message == null}")
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (foreground && mark == generation) {
                        val old = state.snapshot ?: ShelfSnapshot(directory = directory)
                        state =
                            state.copy(
                                snapshot = old.copy(attemptedAt = System.currentTimeMillis()),
                                busy = false,
                                message = "书架读取失败，已保留原内容，请稍后刷新",
                            )
                        publish(state)
                    }
                }
            }
    }

    private suspend fun readState(
        source: String,
        directory: String,
        refresh: Boolean,
    ): ShelfDisplayState {
        val saved = repository.load(source, directory)
        val result = if (refresh) repository.refresh(source, directory, saved) else null
        val recent = if (source == ShelfSourceSupport.LOCAL) RecentBooksSection.load() else emptyList()
        return ShelfDisplayState(if (result != null) result.snapshot else saved, recent, result?.message)
    }
}
