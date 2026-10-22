package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.R

/** 负一屏书架设置：本地目录单行；微信读书与桌面本书共用刷新。 */
internal class ShelfSettingsPanel(
    context: Context,
    private val onChanged: () -> Unit,
) : LinearLayout(context) {
    private val tokens = DesignRuntime.tokens

    init {
        orientation = VERTICAL
        tag = "bookshelf-settings"
        render()
    }

    private fun render() {
        removeAllViews()
        addView(directoryRow())
    }

    private fun row(
        label: String,
        value: String,
        targetId: Int,
        click: () -> Unit,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = tokens.dp(44f)
            addView(InkWidgets.body(context, label), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(
                InkWidgets.caption(context, value).apply {
                    id = targetId
                    minHeight = tokens.dp(40f)
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { click() }
                },
            )
        }

    private fun directoryRow(): LinearLayout =
        row("本地目录", "修改", R.id.shelf_directory) {
            InkDialogs.input(
                context,
                "本地目录",
                hint = "例如 Books 或 Download/Books",
                initial = AppServices.config.state.value.integrations.booksDir,
            ) { raw ->
                val value = raw.trim()
                if (value.isEmpty()) {
                    AppServices.router.toast("目录名称不能为空")
                } else {
                    AppServices.config.edit { it.copy(integrations = it.integrations.copy(booksDir = value)) }
                    onChanged()
                    render()
                }
            }
        }.apply {
            addView(
                InkWidgets.caption(context, AppServices.config.state.value.integrations.booksDir).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    setPadding(tokens.gap, 0, tokens.gap, 0)
                },
                1,
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
            )
        }
}
