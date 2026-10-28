package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.wereadsync.WereadAssetRefreshState

/** 资产原位置的单一标题、刷新动作与排序选择；正文说明只在首次拉取之前展示。 */
internal class AssetSectionControls(
    context: Context,
    refresh: () -> Unit,
    reloadAll: () -> Unit,
    sort: (Int) -> Unit,
    wallpaper: () -> Unit,
) : LinearLayout(context) {
    val notice: TextView = InkWidgets.body(context, WereadAssetRefreshState.FIRST_NOTICE)

    init {
        orientation = VERTICAL
        val tokens = DesignRuntime.tokens
        val inset = DeskSectionStyle.paddingXPx(resources.displayMetrics.widthPixels)
        val header = DeskSectionStyle.buildHeader(context, "阅读资产")
        val options = listOf("时长", "原价", "收入")
        val adapter =
            object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, options) {
                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup,
                ): View = label(position).apply { text = options[position] + " ▾" }

                override fun getDropDownView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup,
                ): View = label(position)

                private fun label(position: Int): TextView =
                    InkWidgets.body(context, options[position]).apply {
                        minimumHeight = tokens.dp(44f)
                        maxLines = 1
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 0, 0, 0)
                        setTextColor(InkColors.INK)
                        setBackgroundColor(InkColors.PAPER)
                    }
            }
        val spinner =
            Spinner(context, Spinner.MODE_DROPDOWN).apply {
                contentDescription = "阅读资产排序"
                background = null
                setPadding(0, 0, 0, 0)
                this.adapter = adapter
                onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long,
                        ) = sort(position)

                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }
            }
        header.addView(spinner, LayoutParams(tokens.dp(70f), LayoutParams.WRAP_CONTENT))
        header.addView(action("刷新", refresh))
        header.addView(action("重新拉取", reloadAll))
        header.addView(action("壁纸", wallpaper))
        addView(header)
        notice.setPadding(inset, 0, inset, tokens.gap)
        notice.visibility = GONE
        addView(notice)
    }

    private fun action(
        label: String,
        click: () -> Unit,
    ): TextView =
        InkWidgets.caption(context, label).apply {
            val tokens = DesignRuntime.tokens
            setTextColor(InkColors.INK)
            gravity = Gravity.CENTER
            minimumHeight = tokens.dp(44f)
            setPadding(tokens.dp(2f), 0, tokens.dp(2f), 0)
            maxLines = 1
            setOnClickListener { click() }
        }
}
