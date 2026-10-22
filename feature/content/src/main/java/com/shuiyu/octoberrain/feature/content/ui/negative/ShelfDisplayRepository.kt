package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.render.WereadCoverStore
import com.shuiyu.octoberrain.feature.content.ContentRegistry
import com.shuiyu.octoberrain.feature.content.data.ContentPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

internal data class ShelfRefreshResult(
    val snapshot: ShelfSnapshot?,
    val message: String? = null,
)

/** Local files retain a directory snapshot; WeRead reads the same saved shelf as the book widget. */
internal class ShelfDisplayRepository(
    private val context: Context,
) {
    private val store = ShelfSnapshotStore(File(context.filesDir, "shelf-display"))

    fun load(
        source: String,
        directory: String,
    ): ShelfSnapshot? = if (source == ShelfSourceSupport.LOCAL) store.load(source, directory) else sharedShelf()

    private fun sharedShelf(downloadCovers: Boolean = false): ShelfSnapshot? {
        val items = ContentRegistry.source()?.storedWereadShelf().orEmpty()
        val visible = AppServices.config.state.value.desktop.negativePage.shelfVisibleLimit
        val saved =
            items.mapIndexed { index, item ->
                val cover =
                    if (downloadCovers && index < visible) {
                        WereadCoverStore.ensure(context, item.bookId, item.coverPath)
                    } else {
                        WereadCoverStore.local(context, item.bookId, item.coverPath)
                            ?: item.coverPath?.let {
                                WereadCoverStore.local(
                                    context,
                                    Integer.toHexString(it.hashCode()),
                                    it,
                                )
                            }
                    }
                item.copy(coverPath = cover?.absolutePath)
            }
        return saved.takeIf { it.isNotEmpty() }?.let { ShelfSnapshot(weread = it) }
    }

    suspend fun refresh(
        source: String,
        directory: String,
        previous: ShelfSnapshot?,
    ): ShelfRefreshResult {
        if (source == ShelfSourceSupport.WEREAD) return refreshSharedShelf()
        val attempted = System.currentTimeMillis()
        val result =
            try {
                check(ContentPaths.hasStorageAccess())
                val items = checkNotNull(ContentRegistry.source()).refreshLocalDisplayShelf().take(10)
                ShelfRefreshResult(ShelfSnapshot(directory, System.currentTimeMillis(), attempted, local = items))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ShelfRefreshResult(
                    (previous ?: ShelfSnapshot(directory = directory)).copy(attemptedAt = attempted),
                    "刷新失败，已保留原书架，请检查目录和存储权限",
                )
            }
        currentCoroutineContext().ensureActive()
        result.snapshot?.let { store.save(source, it) }
        return result
    }

    private suspend fun refreshSharedShelf(): ShelfRefreshResult {
        val outcome = SharedBookRefresh.request(AppServices.wereadSync)
        currentCoroutineContext().ensureActive()
        return ShelfRefreshResult(
            sharedShelf(downloadCovers = outcome.first),
            outcome.second.takeUnless { outcome.first },
        )
    }
}
