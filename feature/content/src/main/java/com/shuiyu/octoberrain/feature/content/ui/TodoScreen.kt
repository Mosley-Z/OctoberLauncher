package com.shuiyu.octoberrain.feature.content.ui

import android.view.Gravity
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.WereadTodoItem
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets

/**
 * 待办页。
 *
 * 展示微读侧迁移过来的阅读待办，按编号排列并标注完成状态与最近更新时间；
 * 顶部给出总项数与已完成项数。数据来自
 * [com.shuiyu.octoberrain.core.common.WereadContent.wereadTodos]，条目为空时
 * 显示空态提示。
 */
class TodoScreen : ContentScreen("待办") {
    override fun onResume() {
        renderPage()
    }

    /** 读取待办列表并绘制页面。 */
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
            val todos = source.wereadTodos()
            if (todos.isEmpty()) {
                container.addView(
                    ContentUi.emptyCard(
                        context,
                        "暂无待办",
                        "微读侧待办迁移完成后，待办页显示条目与编号。",
                    ),
                )
                return@load
            }
            val done = todos.count { it.done }
            container.addView(buildSummary(todos.size, done))
            todos.forEachIndexed { index, item ->
                container.addView(buildTodo(index, item))
            }
        }
    }

    /**
     * 构造待办汇总卡片。
     *
     * @param total 待办总项数
     * @param done 已完成项数
     */
    private fun buildSummary(
        total: Int,
        done: Int,
    ): LinearLayout {
        val card = ContentUi.card(context)
        card.addView(InkWidgets.caption(context, "待办汇总"))
        card.addView(InkWidgets.heading(context, "共 $total 项 · 已完成 $done 项"))
        card.addView(InkWidgets.caption(context, "未完成 ${total - done} 项，编号按条目顺序生成。"))
        return card
    }

    /**
     * 构造单条待办卡片。
     *
     * 编号取条目顺序加一，补足两位数字；已完成项正文使用次要墨色，右侧标注状态；
     * 更新时间大于零时在下方补充一行。
     *
     * @param index 条目下标，从 0 开始
     * @param item 待办条目
     */
    private fun buildTodo(
        index: Int,
        item: WereadTodoItem,
    ): LinearLayout {
        val card = ContentUi.card(context)
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
        row.addView(
            InkWidgets.caption(context, (index + 1).toString().padStart(2, '0')).apply {
                minWidth = tokens.settingsIcon
            },
        )
        row.addView(
            InkWidgets.body(context, item.text.ifBlank { "未填写内容" }).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (item.done) setTextColor(InkColors.INK_3)
            },
        )
        row.addView(InkWidgets.caption(context, if (item.done) "已完成" else "进行中"))
        card.addView(row)
        if (item.updatedAt > 0L) {
            card.addView(
                InkWidgets.caption(context, "更新于 ${ContentUi.formatDateTime(item.updatedAt)}").apply {
                    setPadding(tokens.settingsIcon, tokens.gap, 0, 0)
                },
            )
        }
        return card
    }
}
