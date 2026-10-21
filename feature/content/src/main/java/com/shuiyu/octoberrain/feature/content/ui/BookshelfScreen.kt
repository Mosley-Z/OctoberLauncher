package com.shuiyu.octoberrain.feature.content.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.BookItem
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.ReadingSnapshot
import com.shuiyu.octoberrain.core.common.WereadSection
import com.shuiyu.octoberrain.core.common.WereadShelfItem
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.data.ContentPaths
import com.shuiyu.octoberrain.feature.content.ui.shelf.ShelfBookCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * 书架页面。
 *
 * 按 [com.shuiyu.octoberrain.core.common.IntegrationsConfig.bookshelfRows] 的行数
 * 展示两列封面卡片：卡片以封面为主体，书名与作者以腰封形式压在封面中下部，
 * 阅读进度由底部覆盖层与右上角百分比角标呈现。数据先取三十分钟内存缓存立即
 * 渲染，再在后台刷新；手动刷新清空缓存后重新读取。默认只展示微信读书来源，
 * 本地来源开启后与微信读书条目合并，并按最近阅读顺序排列；点击条目跳转到
 * 对应阅读应用。
 */
class BookshelfScreen : ContentScreen("书架") {
    private companion object {
        private const val TAG = "书架"

        /** 每行固定两列。 */
        private const val GRID_COLUMNS = 2

        /** 行数配置允许的最小值与最大值。 */
        private const val MIN_ROWS = 1
        private const val MAX_ROWS = 6
    }

    /** 页面刷新令牌，用于丢弃离开页面后完成的异步渲染。 */
    private var refreshToken = 0

    override fun onResume() {
        val token = ++refreshToken
        renderPage(token)
    }

    override fun onPause() {
        refreshToken += 1
        super.onPause()
    }

    /** 同步渲染骨架与缓存数据，再启动后台刷新。 */
    private fun renderPage(token: Int) {
        val container = content
        container.removeAllViews()
        val source = source() ?: return showMissingModule(container)
        val localEnabled = source.localShelfEnabled()
        val wereadEnabled = source.wereadShelfEnabled()
        val limit = rowsSetting() * GRID_COLUMNS
        renderHeader(container, localEnabled, wereadEnabled)
        if (!localEnabled && !wereadEnabled) {
            renderDisabledShelf(container)
            return
        }
        if (localEnabled && !ContentPaths.hasStorageAccess()) {
            container.addView(
                ContentUi.noticeCard(
                    context = context,
                    message = "未获得文件访问权限，本地书架无法读取。",
                    actionLabel = "授权",
                    onAction = { requestStoragePermission() },
                ),
            )
        }
        val gridHost =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
        container.addView(gridHost)

        // 缓存命中时立即渲染，避免等待网络后再出现书籍。
        val cached = source.cachedShelfData(localEnabled, wereadEnabled)
        if (cached != null) {
            renderGrid(
                gridHost,
                mergeEntries(null, cached.local, cached.weread, limit),
                localEnabled,
                wereadEnabled,
            )
        } else {
            ContentUi.loading(gridHost)
        }

        runTask {
            if (token != refreshToken) return@runTask
            try {
                coroutineScope {
                    val snapshotDeferred =
                        async {
                            runCatching { source.readingSnapshot() }.getOrNull()
                        }
                    val fresh = source.refreshShelfData(localEnabled, wereadEnabled)
                    if (token != refreshToken) return@coroutineScope
                    renderGrid(
                        gridHost,
                        mergeEntries(null, fresh.local, fresh.weread, limit),
                        localEnabled,
                        wereadEnabled,
                    )
                    val snapshot = snapshotDeferred.await()
                    if (token != refreshToken || snapshot == null) return@coroutineScope
                    renderGrid(
                        gridHost,
                        mergeEntries(snapshot, fresh.local, fresh.weread, limit),
                        localEnabled,
                        wereadEnabled,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                InkLog.w(TAG, "后台刷新失败：${error.message}", error)
                if (gridHost.childCount == 0 && token == refreshToken) {
                    ContentUi.errorCard(gridHost, "书架读取失败，请稍后重试。")
                }
            }
        }
    }

    /** 来源说明与次要操作行，操作包含刷新与书架设置。 */
    private fun renderHeader(
        container: LinearLayout,
        localEnabled: Boolean,
        wereadEnabled: Boolean,
    ) {
        val label =
            when {
                localEnabled && wereadEnabled -> "本地 / 微信读书"
                localEnabled -> "本地"
                wereadEnabled -> "微信读书"
                else -> "未启用"
            }
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
        row.addView(
            InkWidgets.caption(context, "来源：$label").apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            },
        )
        row.addView(
            ContentUi.actionText(context, "笔记") {
                navigate(Destination.WereadTab(WereadSection.NOTES))
            },
        )
        row.addView(ContentUi.actionText(context, "刷新") { reloadShelf() })
        row.addView(
            ContentUi.actionText(context, "书架设置") {
                navigate(Destination.BookshelfSettings)
            },
        )
        container.addView(row)
    }

    /** 两个来源均关闭时的停用提示。 */
    private fun renderDisabledShelf(container: LinearLayout) {
        container.addView(
            ContentUi.emptyCard(
                context = context,
                title = "书架未启用来源",
                message = "本地书籍与微信读书两个来源均已关闭，开启至少一个来源后此处显示书籍。",
                actionLabel = "书架设置",
                onAction = { navigate(Destination.BookshelfSettings) },
            ),
        )
    }

    /** 模块未安装时的提示。 */
    private fun showMissingModule(container: LinearLayout) {
        container.addView(
            ContentUi.emptyCard(
                context = context,
                title = "内容模块尚未就绪",
                message = "桌面服务尚未完成初始化，请稍后重新进入。",
            ),
        )
    }

    /**
     * 合并两个来源的条目。
     *
     * 微信读书接口按最近阅读优先返回，因此保持接口顺序；本地书籍按 [BookItem.addedAt]
     * 倒序排列。两来源同时启用时，微信读书条目在前，本地条目在后；阅读快照命中的
     * 书籍视为最近阅读并提到首位，其进度也取自快照。缺少时间戳的条目排在最后。
     */
    private fun mergeEntries(
        snapshot: ReadingSnapshot?,
        local: List<BookItem>,
        weread: List<WereadShelfItem>,
        limit: Int,
    ): List<Entry> {
        val snapshotTitle = snapshot?.title?.trim().orEmpty()
        val localEntries =
            local.sortedByDescending { it.addedAt }.map { book ->
                val matched =
                    snapshot != null &&
                        snapshotTitle.isNotEmpty() &&
                        book.title.trim() == snapshotTitle
                Entry(
                    title = book.title,
                    author = book.author,
                    coverPath = book.coverPath,
                    progress = if (matched) snapshot.progress else 0f,
                    deepLink = null,
                    localPath = book.path,
                )
            }
        val wereadEntries =
            weread.map { item ->
                val matched =
                    snapshot != null &&
                        snapshotTitle.isNotEmpty() &&
                        item.title.trim() == snapshotTitle
                Entry(
                    title = item.title,
                    author = item.author,
                    coverPath = item.coverPath,
                    progress = if (matched) snapshot.progress else item.progress,
                    deepLink = item.deepLink,
                    localPath = null,
                    bookId = item.bookId,
                )
            }
        val merged = ArrayList<Entry>(wereadEntries.size + localEntries.size)
        merged.addAll(wereadEntries)
        merged.addAll(localEntries)
        if (snapshotTitle.isNotEmpty()) {
            val index = merged.indexOfFirst { it.title.trim() == snapshotTitle }
            if (index > 0) {
                merged.add(0, merged.removeAt(index))
            }
        }
        return merged.take(limit)
    }

    /** 渲染双列封面卡片网格；条目不足时补空位保持列宽一致。 */
    private fun renderGrid(
        gridHost: LinearLayout,
        entries: List<Entry>,
        localEnabled: Boolean,
        wereadEnabled: Boolean,
    ) {
        gridHost.removeAllViews()
        if (entries.isEmpty()) {
            val message =
                when {
                    localEnabled && wereadEnabled -> "本地书架与微信读书均无书籍，阅读后此处显示最近阅读的书。"
                    wereadEnabled -> "微信读书书架为空，在微信读书中打开书籍后此处显示。"
                    else -> "把书籍放入外部存储的 ${ContentPaths.integrations().booksDir} 目录后，此处按最近阅读排序显示。"
                }
            gridHost.addView(
                ContentUi.emptyCard(
                    context = context,
                    title = "书架为空",
                    message = message,
                    actionLabel = "刷新",
                    onAction = { reloadShelf() },
                ),
            )
            return
        }
        val grid =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
        for (chunk in entries.chunked(GRID_COLUMNS)) {
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                            topMargin = tokens.gapLarge
                        }
                }
            for ((column, entry) in chunk.withIndex()) {
                row.addView(createCard(entry, column))
            }
            repeat(GRID_COLUMNS - chunk.size) {
                row.addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    },
                )
            }
            grid.addView(row)
        }
        gridHost.addView(grid)
    }

    /** 创建单本书的封面卡片；首列保留右间距，使两列留白与页边距一致。 */
    private fun createCard(
        entry: Entry,
        column: Int,
    ): View {
        val card =
            ShelfBookCard.create(
                context = context,
                title = entry.title,
                author = entry.author,
                coverPath = entry.coverPath,
                progress = entry.progress,
                onClick = { open(entry) },
            )
        (card.layoutParams as? LinearLayout.LayoutParams)?.marginEnd =
            if (column == 0) tokens.gap else 0
        return card
    }

    /** 按条目类型打开：本地书籍走文件打开流程，微信读书走深链。 */
    private fun open(entry: Entry) {
        val localPath = entry.localPath
        if (localPath != null) {
            openFile(java.io.File(localPath))
            return
        }
        val target =
            entry.deepLink
                ?: entry.bookId?.let { "weread://reading?bId=$it" }
        if (target.isNullOrBlank()) {
            toast("无法确定该书的打开地址")
            return
        }
        runCatching { AppServices.router.openUrl(target) }
            .onFailure {
                InkLog.w(TAG, "打开微信读书失败：${it.message}")
                toast("无法打开微信读书")
            }
    }

    /** 读取行数配置并限制到 3 到 6 行；负一屏加减号写入同一字段。 */
    private fun rowsSetting(): Int =
        runCatching {
            ContentPaths.integrations().bookshelfRows
        }.getOrDefault(MIN_ROWS).coerceIn(MIN_ROWS, MAX_ROWS)

    /** 清空缓存后重新加载页面，手动刷新与空状态重试共用。 */
    private fun reloadShelf() {
        source()?.invalidate()
        refreshToken += 1
        renderPage(refreshToken)
    }

    /**
     * 书架展示条目。
     *
     * localPath 与 deepLink 二者取一，用于区分本地书与微信读书书；
     * bookId 用于微信读书缺少深链时拼装阅读地址。
     */
    private data class Entry(
        val title: String,
        val author: String,
        val coverPath: String?,
        val progress: Float,
        val deepLink: String?,
        val localPath: String?,
        val bookId: String? = null,
    )
}
