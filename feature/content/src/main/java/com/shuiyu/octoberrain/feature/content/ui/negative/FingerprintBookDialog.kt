package com.shuiyu.octoberrain.feature.content.ui.negative

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.WereadDeskConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.ui.ContentUi

/** 本地书目多选；搜索与清空只修改草稿，确认后才写回。 */
internal object FingerprintBookDialog {
    fun show(
        context: Context,
        candidates: List<Array<String>>,
        selected: List<String>,
        onConfirm: (List<String>) -> Unit,
    ) {
        if (candidates.isEmpty() && selected.isEmpty()) {
            AppServices.router.toast("暂无可选的指纹书目")
            return
        }
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(InkColors.PAPER)
                val tokens = DesignRuntime.tokens
                setPadding(tokens.pageGutter, tokens.gap, tokens.pageGutter, tokens.pageBottom)
            }
        root.addView(InkWidgets.heading(context, "选择指纹书目"))
        val search =
            EditText(context).apply {
                hint = "搜索书名"
                setSingleLine(true)
                setTextColor(InkColors.INK)
                setHintTextColor(InkColors.INK_3)
            }
        root.addView(search)
        val info = InkWidgets.caption(context, "")
        root.addView(info)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            ScrollView(context).apply { addView(list) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        val bar = LinearLayout(context)
        val selection = Selection(selected)
        val confirm =
            ContentUi.actionText(context, "确定") {
                onConfirm(selection.ids.toList())
                dialog.dismiss()
            }
        val renderer = Rows(context, candidates, list, info, confirm, selection)
        bar.addView(
            ContentUi.actionText(context, "清空") {
                selection.ids.clear()
                renderer.render(search.text.toString())
            },
        )
        bar.addView(ContentUi.actionText(context, "取消") { dialog.dismiss() })
        bar.addView(confirm)
        root.addView(bar)
        watchSearch(search, renderer)
        dialog.setContentView(root)
        renderer.render("")
        dialog.show()
        dialog.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun watchSearch(
        search: EditText,
        renderer: Rows,
    ) {
        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) = Unit

                override fun afterTextChanged(s: Editable?) {
                    renderer.render(s?.toString().orEmpty())
                }
            },
        )
    }

    private class Selection(
        selected: List<String>,
    ) {
        val ids = LinkedHashSet(WereadDeskConfig(fpBookIds = selected).effectiveFpBookIds)
    }

    /** 限制单次构建的行数，并如实显示总数、匹配数和可见数。 */
    private class Rows(
        private val context: Context,
        private val candidates: List<Array<String>>,
        private val list: LinearLayout,
        private val info: TextView,
        private val confirm: TextView,
        private val selection: Selection,
    ) {
        private var countText = ""

        fun render(keyword: String) {
            list.removeAllViews()
            val matched =
                candidates.filter {
                    title(it).contains(keyword.trim(), ignoreCase = true)
                }
            countText = "共 ${candidates.size} 本，匹配 ${matched.size} 本，显示 ${matched.take(ROW_LIMIT).size} 本"
            if (matched.size > ROW_LIMIT) countText += "（上限 300，请搜索缩小范围）"
            matched.take(ROW_LIMIT).forEach { entry ->
                val id = entry.firstOrNull().orEmpty()
                if (id.isNotBlank()) list.addView(checkBox(id, title(entry)))
            }
            if (matched.isEmpty()) list.addView(InkWidgets.caption(context, "没有匹配的书目"))
            updateCount()
        }

        private fun checkBox(
            id: String,
            title: String,
        ): CheckBox =
            CheckBox(context).apply {
                text = title
                setTextColor(InkColors.INK)
                minimumHeight = DesignRuntime.tokens.listRowHeight
                isChecked = id in selection.ids
                setOnCheckedChangeListener { button, checked ->
                    if (checked && selection.ids.size >= WereadDeskConfig.FP_BOOK_LIMIT) {
                        button.isChecked = false
                        AppServices.router.toast("最多选择 50 本书")
                    } else {
                        if (checked) selection.ids.add(id) else selection.ids.remove(id)
                    }
                    updateCount()
                }
            }

        private fun updateCount() {
            info.text = "$countText；已选 ${selection.ids.size} 本"
            confirm.text = "确定（${selection.ids.size}）"
        }

        private fun title(entry: Array<String>): String =
            entry.getOrNull(1)?.takeIf { it.isNotBlank() } ?: entry.firstOrNull().orEmpty()
    }

    private const val ROW_LIMIT = 300
}
