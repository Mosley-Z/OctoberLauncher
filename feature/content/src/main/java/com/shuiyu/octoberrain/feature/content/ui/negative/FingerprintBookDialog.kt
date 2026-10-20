package com.shuiyu.octoberrain.feature.content.ui.negative

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkTokens
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.ui.ContentUi

/**
 * 阅读指纹的书目选择对话框。
 *
 * 对话框列出内核给出的候选书目，支持按书名筛选，选中后把书目编号交给调用方
 * 写入内核配置并重绘分区。候选书目由内核从原盘笔记与书架数据汇总得到，
 * 未提供候选时对话框不打开，由调用方给出提示。
 */
internal object FingerprintBookDialog {
    /**
     * 展示书目选择对话框。
     *
     * @param context 界面上下文
     * @param candidates 候选书目，每个元素按编号与书名的顺序给出
     * @param onPick 选中回调，参数为书目编号
     */
    fun show(
        context: Context,
        candidates: List<Array<String>>,
        onPick: (String) -> Unit,
    ) {
        if (candidates.isEmpty()) {
            runCatching { AppServices.router.toast(EMPTY_MESSAGE) }
            return
        }
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val builder = RowBuilder(context, list, dialog, onPick)
        val search = buildSearch(context) { keyword -> builder.render(candidates, keyword) }
        dialog.setContentView(buildContent(context, search, list) { dialog.dismiss() })
        builder.render(candidates, "")
        dialog.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        dialog.show()
    }

    /**
     * 构建对话框内容视图。
     *
     * @param context 视图上下文
     * @param search 搜索框
     * @param list 候选行容器
     * @param onClose 关闭动作
     * @return 内容视图
     */
    private fun buildContent(
        context: Context,
        search: EditText,
        list: LinearLayout,
        onClose: () -> Unit,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(InkColors.PAPER)
                setPadding(0, tokens.gap, 0, tokens.pageBottom)
            }
        root.addView(buildTitleBar(context, tokens, onClose))
        val searchParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        searchParams.leftMargin = tokens.pageGutter
        searchParams.rightMargin = tokens.pageGutter
        root.addView(search, searchParams)
        val scroll =
            ScrollView(context).apply {
                isFillViewport = true
                addView(
                    list,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return root
    }

    /**
     * 构建标题行。
     *
     * @param context 视图上下文
     * @param tokens 当前设计令牌
     * @param onClose 关闭动作
     * @return 标题行视图
     */
    private fun buildTitleBar(
        context: Context,
        tokens: InkTokens,
        onClose: () -> Unit,
    ): LinearLayout {
        val bar =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = tokens.topBarHeight
                setPadding(tokens.pageGutter, 0, tokens.pageGutter, 0)
            }
        bar.addView(
            InkWidgets.heading(context, TITLE).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            },
        )
        bar.addView(ContentUi.actionText(context, CLOSE) { onClose() })
        return bar
    }

    /**
     * 构建搜索框。
     *
     * @param context 视图上下文
     * @param onChange 关键词变化回调
     * @return 搜索框
     */
    private fun buildSearch(
        context: Context,
        onChange: (String) -> Unit,
    ): EditText {
        val tokens = DesignRuntime.tokens
        val search =
            EditText(context).apply {
                hint = SEARCH_HINT
                setSingleLine(true)
                setTextColor(InkColors.INK)
                setHintTextColor(InkColors.INK_3)
                setPadding(tokens.gap, 0, tokens.gap, 0)
                minimumHeight = tokens.listRowHeight
            }
        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    text: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) = Unit

                override fun afterTextChanged(editable: Editable?) {
                    onChange(editable?.toString().orEmpty())
                }
            },
        )
        return search
    }

    /**
     * 候选书目行的构建器。
     *
     * 按筛选关键词重建行列表，点击行时关闭对话框并回传书目编号。
     *
     * @param context 视图上下文
     * @param container 行容器
     * @param dialog 所属对话框，用于选中后关闭
     * @param onPick 选中回调
     */
    private class RowBuilder(
        private val context: Context,
        private val container: LinearLayout,
        private val dialog: Dialog,
        private val onPick: (String) -> Unit,
    ) {
        /**
         * 按关键词筛选并重绘候选行。
         *
         * @param candidates 全部候选书目
         * @param keyword 筛选关键词，为空时展示全部
         */
        fun render(
            candidates: List<Array<String>>,
            keyword: String,
        ) {
            container.removeAllViews()
            val tokens = DesignRuntime.tokens
            val matched =
                candidates.filter { entry ->
                    keyword.isBlank() || titleOf(entry).contains(keyword, ignoreCase = true)
                }
            if (matched.isEmpty()) {
                container.addView(
                    InkWidgets.caption(context, NO_MATCH).apply {
                        setPadding(tokens.pageGutter, tokens.gap, tokens.pageGutter, tokens.gap)
                    },
                )
                return
            }
            matched.forEach { entry ->
                container.addView(buildRow(entry, tokens))
            }
        }

        /**
         * 构建单个候选行。
         *
         * @param entry 候选书目，元素按编号与书名的顺序给出
         * @param tokens 当前设计令牌
         * @return 可点击的行视图
         */
        private fun buildRow(
            entry: Array<String>,
            tokens: InkTokens,
        ): LinearLayout {
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = tokens.listRowHeight
                    setPadding(tokens.pageGutter, tokens.gap, tokens.pageGutter, tokens.gap)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        onPick(idOf(entry))
                        dialog.dismiss()
                    }
                }
            row.addView(InkWidgets.body(context, titleOf(entry)))
            if (entry.size > 1) {
                row.addView(InkWidgets.caption(context, idOf(entry)))
            }
            row.addView(InkWidgets.divider(context))
            return row
        }

        /**
         * 读取候选书目的编号。
         *
         * @param entry 候选书目
         * @return 书目编号，缺失时返回空串
         */
        private fun idOf(entry: Array<String>): String = entry.firstOrNull().orEmpty()

        /**
         * 读取候选书目的书名。
         *
         * @param entry 候选书目
         * @return 书名，缺失时回退为编号
         */
        private fun titleOf(entry: Array<String>): String =
            entry.getOrNull(1)?.takeIf { it.isNotBlank() } ?: idOf(entry)
    }

    /** 对话框标题。 */
    private const val TITLE: String = "选择指纹书目"

    /** 关闭按钮文案。 */
    private const val CLOSE: String = "关闭"

    /** 搜索框提示文案。 */
    private const val SEARCH_HINT: String = "搜索书名"

    /** 无匹配书目时的提示文案。 */
    private const val NO_MATCH: String = "没有匹配的书目"

    /** 候选书目为空时的提示文案。 */
    private const val EMPTY_MESSAGE: String = "暂无可选的指纹书目"
}
