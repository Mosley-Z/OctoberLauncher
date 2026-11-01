package com.shuiyu.octoberrain.feature.content.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.WereadNotesSnapshot
import com.shuiyu.octoberrain.core.common.WereadShelfItem
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlin.math.roundToInt

/**
 * 本书页。
 *
 * 汇总微信读书书架每一本书的阅读进度与笔记数量：顶部给出书架概览，
 * 下方按两列排列书目，点击书目进入微信读书阅读页。数据来自
 * [com.shuiyu.octoberrain.core.common.WereadContent]，未配置密钥或来源
 * 关闭时显示对应提示。
 */
class BookScreen : ContentScreen("本书") {
    private companion object {
        private const val TAG = "本书页"

        /** 书架读取上限。 */
        private const val SHELF_LIMIT = 30

        /** 笔记读取上限，仅用于统计划线数量。 */
        private const val NOTE_LIMIT = 50

        /** 书目网格列数。 */
        private const val COLUMNS = 2
    }

    override fun onResume() {
        renderPage()
    }

    /** 读取书架与笔记数据并绘制页面。 */
    private fun renderPage() {
        load { container ->
            val source = source()
            if (source == null) {
                container.addView(
                    ContentUi.emptyCard(
                        context,
                        "内容模块尚未就绪",
                        "桌面服务尚未完成初始化，请稍后重新进入。",
                    ),
                )
                return@load
            }
            if (!source.wereadClient.isConfigured()) {
                container.addView(
                    ContentUi.noticeCard(
                        context,
                        "未配置微信读书。填写密钥并启用来源后，本书页显示书架每本书的阅读进度与笔记汇总。",
                        "前往设置",
                    ) { navigate(Destination.Weread) },
                )
                return@load
            }
            val shelf = source.wereadShelf(SHELF_LIMIT)
            val notes = source.wereadNotes(NOTE_LIMIT)
            if (shelf.isEmpty()) {
                container.addView(
                    ContentUi.emptyCard(
                        context,
                        "书架暂无可展示的书目",
                        "在微信读书中添加书籍后，本书页显示逐本阅读进度。",
                        "前往书架",
                    ) { navigate(Destination.Bookshelf) },
                )
                return@load
            }
            container.addView(buildSummary(shelf, notes))
            ContentUi.sectionTitle(container, "逐本统计")
            addGrid(container, shelf)
        }
    }

    /**
     * 构造书架概览卡片。
     *
     * 统计在读本数、读完本数与平均阅读进度，并给出划线总数与进入本记页的入口。
     *
     * @param shelf 书架全部书目
     * @param notes 当前页笔记快照，用于统计划线数量
     */
    private fun buildSummary(
        shelf: List<WereadShelfItem>,
        notes: WereadNotesSnapshot,
    ): LinearLayout {
        val card = ContentUi.card(context)
        val reading = shelf.count { it.progress > 0f && it.progress < 1f }
        val finished = shelf.count { it.progress >= 1f }
        val known = shelf.map { it.progress }.filter { it >= 0f }
        val average = if (known.isEmpty()) "未知" else "${(known.average() * 100).roundToInt()}%"
        card.addView(InkWidgets.caption(context, "书架概览"))
        card.addView(InkWidgets.heading(context, "在读 $reading 本 · 读完 $finished 本"))
        card.addView(
            InkWidgets.caption(
                context,
                "平均进度 $average · 划线 ${notes.notes.size} 条（累计 ${notes.total} 条）",
            ),
        )
        card.addView(
            ContentUi.actionText(context, "查看全部划线") {
                navigate(Destination.Note)
            },
        )
        return card
    }

    /**
     * 以两列网格排列书目卡片。
     *
     * 每行最多两张卡片，末行不足时以空白视图补齐，保持列宽一致。
     *
     * @param container 页面正文容器
     * @param shelf 书架全部书目
     */
    private fun addGrid(
        container: LinearLayout,
        shelf: List<WereadShelfItem>,
    ) {
        shelf.chunked(COLUMNS).forEach { week ->
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    setPadding(0, tokens.gap, 0, 0)
                }
            week.forEach { item ->
                row.addView(
                    ContentUi.bookCard(
                        context,
                        item.title.ifBlank { "未知书籍" },
                        item.author.ifBlank { "未知作者" },
                        item.coverPath,
                        item.progress,
                    ) { openBook(item) },
                )
            }
            repeat(COLUMNS - week.size) {
                row.addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                    },
                )
            }
            container.addView(row)
        }
    }

    /**
     * 打开指定书目的阅读入口。
     *
     * 优先使用数据源提供的深度链接跳转微信读书；缺少链接时以提示说明。
     *
     * @param item 书架条目
     */
    private fun openBook(item: WereadShelfItem) {
        val link = item.deepLink
        if (link.isNullOrBlank()) {
            toast("该书没有可用的阅读入口，请在微信读书中打开")
            return
        }
        runCatching { AppServices.router.openUrl(link) }
            .onFailure {
                InkLog.w(TAG, "打开书目失败：${it.message}", it)
                toast("无法打开该书，请确认已安装微信读书")
            }
    }
}
