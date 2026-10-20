package com.shuiyu.octoberrain.feature.content.ui

import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.WereadNoteSnapshot
import com.shuiyu.octoberrain.core.common.WereadNotesSnapshot
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets

/**
 * 本记页。
 *
 * 按书分组展示微信读书划线，每条包含划线原文、个人想法、章节与时间，
 * 顶部给出划线总数与累计数量。入口来自本书页与负一屏划线卡片。
 */
class NoteScreen : ContentScreen("本记") {
    private companion object {
        /** 划线读取上限，与阅读页笔记分栏保持一致。 */
        private const val NOTE_LIMIT = 50
    }

    override fun onResume() {
        renderPage()
    }

    /** 读取划线并按书分组绘制。 */
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
                        "未配置微信读书。填写密钥并启用来源后，本记页显示划线集合。",
                        "前往设置",
                    ) { navigate(Destination.Weread) },
                )
                return@load
            }
            val snapshot = source.wereadNotes(NOTE_LIMIT)
            if (snapshot.notes.isEmpty()) {
                container.addView(
                    ContentUi.emptyCard(
                        context,
                        "暂无划线",
                        "在微信读书中划出句子后，本记页按书汇总展示。",
                        "前往阅读",
                    ) { navigate(Destination.Weread) },
                )
                return@load
            }
            container.addView(buildSummary(snapshot))
            WereadSectionLogic.groupNotes(snapshot.notes).forEach { group ->
                ContentUi.sectionTitle(container, group.bookTitle)
                group.notes.forEach { note ->
                    container.addView(buildNote(note))
                }
            }
        }
    }

    /**
     * 构造划线汇总卡片。
     *
     * @param snapshot 笔记分页快照
     */
    private fun buildSummary(snapshot: WereadNotesSnapshot): LinearLayout {
        val card = ContentUi.card(context)
        card.addView(InkWidgets.caption(context, "划线汇总"))
        card.addView(InkWidgets.heading(context, "当前显示 ${snapshot.notes.size} 条"))
        card.addView(
            InkWidgets.caption(
                context,
                "微信读书累计 ${snapshot.total} 条划线，按书分组排列，时间取最近一次创建时间。",
            ),
        )
        return card
    }

    /**
     * 构造单条划线卡片。
     *
     * 划线原文使用正文样式；个人想法非空时追加一行；末行给出章节与时间。
     *
     * @param note 划线条目
     */
    private fun buildNote(note: WereadNoteSnapshot): LinearLayout {
        val card = ContentUi.card(context)
        card.addView(InkWidgets.body(context, note.text))
        if (note.thought.isNotBlank()) {
            card.addView(
                InkWidgets.caption(context, "想法：${note.thought}").apply {
                    setPadding(0, tokens.gap, 0, 0)
                },
            )
        }
        val meta =
            buildString {
                if (note.chapterTitle.isNotBlank()) {
                    append(note.chapterTitle)
                    append(" · ")
                }
                append(ContentUi.formatDateTime(note.createdAt))
            }
        card.addView(
            InkWidgets.caption(context, meta).apply {
                setTextColor(InkColors.INK_3)
                setPadding(0, tokens.gap, 0, 0)
            },
        )
        return card
    }
}
