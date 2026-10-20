package com.shuiyu.octoberrain.feature.content.ui

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.KoreaderHeat
import com.shuiyu.octoberrain.core.common.ReadingSnapshot
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.data.KoreaderEntry
import com.shuiyu.octoberrain.feature.content.data.KoreaderStats
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File

private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * KOReader 页面。
 *
 * 展示当前阅读进度、今日与本月时长、二十八天阅读热力图与最近书目，并提供
 * 启动 KOReader 的入口。数据全部来自外部存储的 koreader 目录，缺失时显示
 * 空状态。
 */
class KoreaderScreen : ContentScreen("KOReader") {
    private companion object {
        private const val HEAT_COLUMNS = 7
        private const val MAX_ENTRIES = 20
        private val KOREADER_PACKAGES =
            listOf(
                "org.koreader.launcher",
                "org.koreader.launcher.fdroid",
            )
    }

    override fun onResume() {
        load { container -> render(container) }
    }

    /** 读取并渲染全部区块。 */
    private suspend fun render(container: LinearLayout) {
        val source = source() ?: return showMissingModule(container)
        renderHeader(container)
        coroutineScope {
            val snapshotDeferred = async { source.readingSnapshot() }
            val statsDeferred = async { source.koreaderStats() }
            val entries = source.koreaderEntries(MAX_ENTRIES)
            val stats = statsDeferred.await()
            renderSnapshot(container, snapshotDeferred.await())
            renderStats(container, stats)
            renderEntries(container, entries)
            if (stats == null && entries.isEmpty()) {
                container.addView(
                    ContentUi.emptyCard(
                        context = context,
                        title = "未检测到 KOReader 数据",
                        message = "需要外部存储中的 koreader 目录可读，并至少阅读过一本书。",
                        actionLabel = "刷新",
                        onAction = { refreshData() },
                    ),
                )
            }
        }
    }

    /** 模块未安装时的提示。 */
    private fun showMissingModule(container: LinearLayout) {
        container.addView(
            ContentUi.emptyCard(
                context = context,
                title = "KOReader 尚未就绪",
                message = "桌面服务尚未完成初始化，请稍后重新进入。",
            ),
        )
    }

    /** 顶部操作卡。 */
    private fun renderHeader(container: LinearLayout) {
        val card = ContentUi.card(context)
        card.addView(InkWidgets.body(context, "阅读数据来自 koreader 目录"))
        card.addView(
            InkWidgets.caption(context, "统计数据库会在读取时复制到缓存，不影响原始文件。").apply {
                setPadding(0, tokens.gap / 2, 0, 0)
            },
        )
        val actions =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams =
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = tokens.gap
                    }
            }
        actions.addView(
            InkWidgets.button(context, "打开 KOReader", onClick = { openKoreader() }).apply {
                layoutParams = LinearLayout.LayoutParams(0, tokens.buttonHeight, 1f)
            },
        )
        actions.addView(
            InkWidgets.button(context, "刷新", onClick = { refreshData() }).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, tokens.buttonHeight, 1f).apply {
                        marginStart = tokens.gap
                    }
            },
        )
        card.addView(actions)
        container.addView(card)
    }

    /** 当前阅读卡片。 */
    private fun renderSnapshot(
        container: LinearLayout,
        snapshot: ReadingSnapshot?,
    ) {
        if (snapshot == null) return
        val card = ContentUi.card(context)
        card.addView(ContentUi.cardTitle(context, "当前阅读"))
        card.addView(InkWidgets.body(context, snapshot.title))
        if (snapshot.author.isNotBlank()) {
            card.addView(InkWidgets.caption(context, snapshot.author))
        }
        card.addView(
            ContentUi.progressBar(context, snapshot.progress, tokens.dp(180f)).apply {
                layoutParams =
                    LinearLayout.LayoutParams(tokens.dp(180f), tokens.progressHeight).apply {
                        topMargin = tokens.gap
                    }
            },
        )
        card.addView(
            InkWidgets
                .caption(
                    context,
                    "进度 ${(snapshot.progress * 100f).toInt()}%  今日 ${ContentUi.formatMinutes(snapshot.todayMinutes)}",
                ).apply {
                    setPadding(0, tokens.gap / 2, 0, 0)
                },
        )
        container.addView(card)
    }

    /** 统计与热力图卡片。 */
    private fun renderStats(
        container: LinearLayout,
        stats: KoreaderStats?,
    ) {
        if (stats == null) {
            container.addView(
                ContentUi.noticeCard(
                    context = context,
                    message = "阅读统计暂不可用，可能尚未产生阅读记录。",
                    actionLabel = "重试",
                    onAction = { refreshData() },
                ),
            )
            return
        }
        val card = ContentUi.card(context)
        card.addView(ContentUi.cardTitle(context, "阅读时长"))
        card.addView(
            ContentUi.keyValue(context, "今日", ContentUi.formatMinutes(stats.todayMinutes)),
        )
        card.addView(
            ContentUi.keyValue(context, "本月", ContentUi.formatMinutes(stats.monthMinutes)),
        )
        if (stats.days.isNotEmpty()) {
            card.addView(
                InkWidgets.caption(context, "近 ${stats.days.size} 天阅读分布").apply {
                    setPadding(0, tokens.gap, 0, 0)
                },
            )
            card.addView(heatGrid(KoreaderHeat(stats.days, stats.maxMinutes)))
            card.addView(
                InkWidgets.caption(context, "颜色越深表示阅读时长越长，峰值 ${stats.maxMinutes} 分钟。").apply {
                    setPadding(0, tokens.gap / 2, 0, 0)
                },
            )
        }
        container.addView(card)
    }

    /** 二十八天阅读热力图。 */
    private fun heatGrid(heat: KoreaderHeat): View {
        val cellSize = tokens.dp(16f)
        val grid =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = tokens.gap / 2
                    }
            }
        for (week in heat.days.chunked(HEAT_COLUMNS)) {
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                }
            for (value in week) {
                row.addView(
                    View(context).apply {
                        background =
                            InkWidgets.background(
                                tokens.dp(2f),
                                heatColor(value, heat.maxMinutes),
                                0,
                                InkColors.INK_4,
                            )
                        layoutParams =
                            LinearLayout.LayoutParams(0, cellSize, 1f).apply {
                                marginEnd = 1
                                bottomMargin = 1
                            }
                    },
                )
            }
            repeat(HEAT_COLUMNS - week.size) {
                row.addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, cellSize, 1f)
                    },
                )
            }
            grid.addView(row)
        }
        return grid
    }

    /** 按阅读时长占比取灰度。 */
    private fun heatColor(
        minutes: Int,
        maxMinutes: Int,
    ): Int {
        if (minutes <= 0) return InkColors.INK_4
        if (maxMinutes <= 0) return InkColors.INK_3
        val ratio = minutes.toFloat() / maxMinutes.toFloat()
        return when {
            ratio < 0.34f -> InkColors.INK_3
            ratio < 0.67f -> InkColors.INK_2
            else -> InkColors.INK
        }
    }

    /** 最近书目列表。 */
    private fun renderEntries(
        container: LinearLayout,
        entries: List<KoreaderEntry>,
    ) {
        if (entries.isEmpty()) return
        ContentUi.sectionTitle(container, "最近阅读")
        val card = ContentUi.card(context)
        entries.forEachIndexed { index, entry ->
            if (index > 0) card.addView(InkWidgets.divider(context))
            card.addView(entryRow(entry))
        }
        container.addView(card)
    }

    /** 单条最近书目。 */
    private fun entryRow(entry: KoreaderEntry): View {
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = tokens.listRowHeight
                setPadding(0, tokens.gap / 2, 0, tokens.gap / 2)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                isClickable = true
                isFocusable = true
                setOnClickListener { openFile(File(entry.book.path)) }
            }
        row.addView(ContentUi.coverView(context, entry.book.coverPath, entry.book.title, tokens.dp(44f)))
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                        marginStart = tokens.gap
                    }
            }
        column.addView(
            InkWidgets.body(context, entry.book.title).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        column.addView(
            InkWidgets.caption(
                context,
                "进度 ${(entry.progress * 100f).toInt()}%  ${ContentUi.formatSize(entry.book.sizeBytes)}",
            ),
        )
        row.addView(column)
        row.addView(InkGlyphs.view(context, InkGlyphs.Kind.CHEVRON, tokens.settingsIcon))
        return row
    }

    /** 启动 KOReader 客户端。 */
    private fun openKoreader() {
        val installed = KOREADER_PACKAGES.firstOrNull { packageInstalled(it) }
        if (installed == null) {
            toast("未安装 KOReader")
            return
        }
        runCatching { AppServices.router.openApp(installed) }
            .onFailure { toast("无法打开 KOReader") }
    }

    /** 判断应用是否已安装。 */
    private fun packageInstalled(packageName: String): Boolean =
        runCatching {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false)

    /** 清空缓存并重新读取。 */
    private fun refreshData() {
        source()?.invalidate()
        onResume()
    }
}
