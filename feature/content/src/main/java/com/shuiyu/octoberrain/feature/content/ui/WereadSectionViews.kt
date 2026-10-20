package com.shuiyu.octoberrain.feature.content.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.WereadBillSnapshot
import com.shuiyu.octoberrain.core.common.WereadFingerprintSnapshot
import com.shuiyu.octoberrain.core.common.WereadHighlight
import com.shuiyu.octoberrain.core.common.WereadMonth
import com.shuiyu.octoberrain.core.common.WereadNoteSnapshot
import com.shuiyu.octoberrain.core.common.WereadNotesSnapshot
import com.shuiyu.octoberrain.core.common.WereadRankEntry
import com.shuiyu.octoberrain.core.common.WereadSection
import com.shuiyu.octoberrain.core.common.WereadShelfItem
import com.shuiyu.octoberrain.core.common.WereadStatsPeriod
import com.shuiyu.octoberrain.core.common.WereadStatsSnapshot
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkTokens
import com.shuiyu.octoberrain.core.design.InkWidgets

private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

/** 总览分段的数据快照。 */
internal data class WereadOverviewData(
    val month: WereadMonth?,
    val shelf: List<WereadShelfItem>,
    val highlights: List<WereadHighlight>,
)

/**
 * 微信读书各分段的内容绘制。
 *
 * 视图全部由 [ContentUi] 与 [InkWidgets] 组成，不读取数据、不发起请求；周期切换
 * 与月历入口通过构造参数回调给页面处理。
 *
 * @param context 宿主上下文
 * @param tokens 设计令牌
 * @param onPeriodSelected 统计分段切换周期时的回调
 * @param onCalendar 打开月历页的回调
 */
@Suppress("TooManyFunctions")
internal class WereadSectionViews(
    private val context: Context,
    private val tokens: InkTokens,
    private val onPeriodSelected: (WereadStatsPeriod) -> Unit,
    private val onCalendar: () -> Unit,
) {
    private companion object {
        private const val GRID_COLUMNS = 3
        private const val MAX_SHELF_ITEMS = 18
        private const val CHART_HEIGHT_DP = 48f
    }

    /** 绘制总览：本月统计、书架与最近划线。 */
    fun renderOverview(
        container: LinearLayout,
        data: WereadOverviewData,
    ) {
        renderMonth(container, data.month)
        renderShelf(container, data.shelf)
        renderHighlights(container, data.highlights)
    }

    /** 绘制统计：周期切换、周期汇总、分桶图与阅读指纹。 */
    fun renderStats(
        container: LinearLayout,
        period: WereadStatsPeriod,
        snapshot: WereadStatsSnapshot?,
        fingerprint: WereadFingerprintSnapshot?,
    ) {
        container.addView(periodChips(period))
        if (snapshot == null) {
            container.addView(
                ContentUi.emptyCard(
                    context = context,
                    title = "${WereadSectionLogic.periodLabel(period)}暂无统计",
                    message = WereadSectionLogic.emptyMessage(WereadSection.STATS, period),
                    actionLabel = "查看月历",
                    onAction = onCalendar,
                ),
            )
        } else {
            val card = ContentUi.card(context)
            card.addView(ContentUi.cardTitle(context, "${WereadSectionLogic.periodLabel(period)}阅读"))
            card.addView(
                InkWidgets.textView(
                    context,
                    ContentUi.formatMinutes(snapshot.totalMinutes),
                    tokens.textHeading,
                    InkColors.INK,
                ),
            )
            card.addView(ContentUi.keyValue(context, "阅读天数", "${snapshot.readDays} 天"))
            card.addView(ContentUi.keyValue(context, "自然日均", ContentUi.formatMinutes(snapshot.dayAverageMinutes)))
            if (snapshot.buckets.isNotEmpty()) {
                card.addView(barChart(snapshot.buckets.map { it.minutes }))
            }
            container.addView(card)
            container.addView(
                InkWidgets.button(context, "月历", onClick = onCalendar).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(MATCH_PARENT, tokens.buttonHeight).apply {
                            topMargin = tokens.gap
                        }
                },
            )
        }
        renderFingerprint(container, fingerprint)
    }

    /** 绘制笔记：按书分组展示划线。 */
    fun renderNotes(
        container: LinearLayout,
        notes: WereadNotesSnapshot,
    ) {
        val groups = WereadSectionLogic.groupNotes(notes.notes)
        if (groups.isEmpty()) {
            container.addView(
                ContentUi.emptyCard(
                    context = context,
                    title = "暂无笔记",
                    message =
                        WereadSectionLogic.emptyMessage(
                            WereadSection.NOTES,
                        ),
                ),
            )
            return
        }
        container.addView(
            InkWidgets.caption(context, "共 ${notes.total.coerceAtLeast(groups.size)} 条笔记").apply {
                setPadding(0, 0, 0, tokens.gap)
            },
        )
        groups.forEach { group ->
            ContentUi.sectionTitle(container, "${group.bookTitle}（${group.notes.size}）")
            val card = ContentUi.card(context)
            group.notes.forEachIndexed { index, note ->
                if (index > 0) card.addView(InkWidgets.divider(context))
                card.addView(noteView(note))
            }
            container.addView(card)
        }
    }

    /** 绘制账单：本月时长、环比与逐日柱状图。 */
    fun renderBill(
        container: LinearLayout,
        bill: WereadBillSnapshot?,
    ) {
        if (bill == null) {
            container.addView(
                ContentUi.emptyCard(
                    context = context,
                    title = "暂无账单",
                    message =
                        WereadSectionLogic.emptyMessage(
                            WereadSection.BILL,
                        ),
                ),
            )
            return
        }
        val card = ContentUi.card(context)
        card.addView(ContentUi.cardTitle(context, "本月阅读"))
        card.addView(
            InkWidgets.textView(
                context,
                ContentUi.formatMinutes(bill.monthMinutes),
                tokens.textHeading,
                InkColors.INK,
            ),
        )
        card.addView(
            InkWidgets
                .caption(
                    context,
                    "上月 " +
                        ContentUi.formatMinutes(bill.lastMonthMinutes) +
                        " · " +
                        WereadSectionLogic.deltaText(bill.monthMinutes, bill.lastMonthMinutes),
                ).apply {
                    setPadding(0, tokens.gap / 2, 0, 0)
                },
        )
        if (bill.days.isNotEmpty()) {
            card.addView(barChart(bill.days))
        }
        container.addView(card)
    }

    /** 绘制排行：按阅读进度降序列出书架条目。 */
    fun renderRank(
        container: LinearLayout,
        rank: List<WereadRankEntry>,
    ) {
        if (rank.isEmpty()) {
            container.addView(
                ContentUi.emptyCard(
                    context = context,
                    title = "暂无排行",
                    message =
                        WereadSectionLogic.emptyMessage(
                            WereadSection.RANK,
                        ),
                ),
            )
            return
        }
        val card = ContentUi.card(context)
        rank.forEachIndexed { index, entry ->
            if (index > 0) card.addView(InkWidgets.divider(context))
            card.addView(
                InkWidgets.body(context, "${index + 1}. ${entry.title.ifBlank { "未知书籍" }}").apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
            card.addView(
                InkWidgets.caption(context, entry.author.ifBlank { "未知作者" }).apply {
                    setPadding(0, tokens.gap / 2, 0, 0)
                },
            )
            card.addView(
                InkWidgets.caption(
                    context,
                    WereadSectionLogic.progressText(entry.progress),
                ),
            )
        }
        container.addView(card)
    }

    /** 月度统计卡片。 */
    private fun renderMonth(
        container: LinearLayout,
        month: WereadMonth?,
    ) {
        if (month == null) {
            container.addView(
                ContentUi.noticeCard(
                    context = context,
                    message = "阅读统计读取失败，请检查网络后重试。",
                ),
            )
            return
        }
        val card = ContentUi.card(context)
        card.addView(ContentUi.cardTitle(context, "本月阅读"))
        card.addView(
            InkWidgets.textView(
                context,
                ContentUi.formatMinutes(month.monthMinutes),
                tokens.textHeading,
                InkColors.INK,
            ),
        )
        card.addView(
            InkWidgets
                .caption(
                    context,
                    "${WereadSectionLogic.deltaText(month.monthMinutes, month.lastMonthMinutes)}（上月 ${
                        ContentUi.formatMinutes(month.lastMonthMinutes)
                    }）",
                ).apply {
                    setPadding(0, tokens.gap / 2, 0, 0)
                },
        )
        if (month.days.isNotEmpty()) {
            card.addView(barChart(month.days))
        }
        container.addView(card)
    }

    /** 书架网格。 */
    private fun renderShelf(
        container: LinearLayout,
        shelf: List<WereadShelfItem>,
    ) {
        if (shelf.isEmpty()) {
            container.addView(
                ContentUi.emptyCard(
                    context = context,
                    title = "书架为空",
                    message = "密钥无效或账号内没有书籍时不会显示内容，可更换密钥后重试。",
                ),
            )
            return
        }
        ContentUi.sectionTitle(container, "书架")
        val card = ContentUi.card(context)
        val shown = shelf.take(MAX_SHELF_ITEMS)
        for (chunk in shown.chunked(GRID_COLUMNS)) {
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                }
            for (item in chunk) {
                row.addView(shelfCell(item))
            }
            repeat(GRID_COLUMNS - chunk.size) {
                row.addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    },
                )
            }
            card.addView(row)
        }
        container.addView(card)
    }

    /** 单个书架单元格。 */
    private fun shelfCell(item: WereadShelfItem): View {
        val cell =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                setPadding(tokens.gap / 2, tokens.gap / 2, tokens.gap / 2, tokens.gap / 2)
            }
        cell.addView(ContentUi.coverView(context, item.coverPath, item.title, tokens.dp(72f)))
        cell.addView(
            InkWidgets.caption(context, item.title).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, tokens.gap / 2, 0, 0)
            },
        )
        cell.addView(
            InkWidgets.caption(context, WereadSectionLogic.progressText(item.progress)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        return cell
    }

    /** 最近划线卡片。 */
    private fun renderHighlights(
        container: LinearLayout,
        highlights: List<WereadHighlight>,
    ) {
        if (highlights.isEmpty()) return
        ContentUi.sectionTitle(container, "最近划线")
        val card = ContentUi.card(context)
        highlights.forEachIndexed { index, highlight ->
            if (index > 0) card.addView(InkWidgets.divider(context))
            card.addView(
                InkWidgets.body(context, highlight.text).apply {
                    maxLines = 4
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
            card.addView(
                InkWidgets
                    .caption(
                        context,
                        "${highlight.bookTitle} · ${ContentUi.formatDateTime(highlight.createdAt)}",
                    ).apply {
                        setPadding(0, tokens.gap / 2, 0, 0)
                    },
            )
        }
        container.addView(card)
    }

    /** 阅读指纹：取最近 28 天绘制 7 列 4 行热力格。 */
    private fun renderFingerprint(
        container: LinearLayout,
        fingerprint: WereadFingerprintSnapshot?,
    ) {
        if (fingerprint == null) return
        val days = fingerprint.days.takeLast(28)
        if (days.isEmpty()) return
        ContentUi.sectionTitle(container, "阅读指纹")
        val card = ContentUi.card(context)
        card.addView(
            InkWidgets
                .caption(
                    context,
                    "累计 ${ContentUi.formatMinutes(fingerprint.totalMinutes)} · 阅读 ${fingerprint.readDays} 天 · 日均 ${
                        ContentUi.formatMinutes(fingerprint.dayAverageMinutes)
                    }",
                ).apply { setPadding(0, 0, 0, tokens.gap) },
        )
        val peak = days.maxOrNull() ?: 0
        for (row in 0 until 4) {
            val line =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                }
            for (column in 0 until 7) {
                val index = row * 7 + column
                val minutes = days.getOrElse(index) { 0 }
                line.addView(fingerprintCell(WereadSectionLogic.heatLevel(minutes, peak)))
            }
            card.addView(line)
        }
        container.addView(card)
    }

    /** 单个指纹格，按热力等级映射四级灰。 */
    private fun fingerprintCell(level: Int): View {
        val side = tokens.dp(18f)
        return View(context).apply {
            layoutParams =
                LinearLayout.LayoutParams(0, side, 1f).apply {
                    marginEnd = 2
                    bottomMargin = 2
                }
            setBackgroundColor(
                when (level) {
                    4 -> InkColors.INK
                    3 -> InkColors.INK_2
                    2 -> InkColors.INK_3
                    1 -> InkColors.INK_4
                    else -> InkColors.PAPER
                },
            )
        }
    }

    /** 周期选择行，当前周期反白。 */
    private fun periodChips(period: WereadStatsPeriod): View {
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams =
                    LinearLayout.LayoutParams(MATCH_PARENT, tokens.buttonHeight).apply {
                        bottomMargin = tokens.gap
                    }
            }
        WereadSectionLogic.PERIODS.forEach { option ->
            val chip =
                TextView(context).apply {
                    text = WereadSectionLogic.periodLabel(option)
                    gravity = Gravity.CENTER
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, tokens.textBody)
                    setTextColor(if (option == period) InkColors.PAPER else InkColors.INK)
                    setBackgroundColor(if (option == period) InkColors.INK else InkColors.PAPER)
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
                    setOnClickListener { onPeriodSelected(option) }
                }
            row.addView(chip)
        }
        return row
    }

    /** 逐项柱状图，横向铺满、底部对齐。 */
    private fun barChart(values: List<Int>): View {
        val chartHeight = tokens.dp(CHART_HEIGHT_DP)
        val peak = maxOf(1, values.maxOrNull() ?: 1)
        val chart =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                layoutParams =
                    LinearLayout.LayoutParams(MATCH_PARENT, chartHeight).apply {
                        topMargin = tokens.gap
                    }
            }
        values.forEach { value ->
            val ratio = value.coerceAtLeast(0).toFloat() / peak.toFloat()
            val barHeight = (chartHeight * ratio).toInt().coerceIn(if (value > 0) 2 else 1, chartHeight)
            chart.addView(
                View(context).apply {
                    setBackgroundColor(if (value > 0) InkColors.INK else InkColors.INK_4)
                    layoutParams =
                        LinearLayout.LayoutParams(0, barHeight, 1f).apply {
                            marginEnd = 1
                        }
                },
            )
        }
        return chart
    }

    /** 单条笔记视图：划线正文、想法与章节时间。 */
    private fun noteView(note: WereadNoteSnapshot): View {
        val cell =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
        cell.addView(
            InkWidgets.body(context, note.text).apply {
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        if (note.thought.isNotBlank()) {
            cell.addView(
                InkWidgets.caption(context, note.thought).apply {
                    setPadding(0, tokens.gap / 2, 0, 0)
                },
            )
        }
        val meta =
            listOf(note.chapterTitle, ContentUi.formatDateTime(note.createdAt))
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        if (meta.isNotEmpty()) {
            cell.addView(
                InkWidgets.caption(context, meta).apply {
                    setPadding(0, tokens.gap / 2, 0, 0)
                },
            )
        }
        return cell
    }
}
