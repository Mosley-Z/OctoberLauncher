package com.shuiyu.octoberrain.feature.content.ui.negative

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.R

/** 唯一书架标题行：书架、行数步进、书源单选、刷新。 */
internal fun buildShelfHeader(
    context: Context,
    onChanged: () -> Unit,
    onRefresh: () -> Unit,
): LinearLayout {
    val tokens = DesignRuntime.tokens
    val page = AppServices.config.state.value.desktop.negativePage
    val row = DeskSectionStyle.buildHeader(context, "书架", "刷新", onAction = onRefresh)

    fun control(
        text: String,
        idValue: Int,
        action: () -> Unit,
    ): TextView =
        InkWidgets.caption(context, text).apply {
            id = idValue
            gravity = Gravity.CENTER
            minWidth = tokens.dp(40f)
            isSingleLine = true
            minHeight = tokens.dp(40f)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    fun step(delta: Int) {
        AppServices.config.edit { config ->
            val old = config.desktop.negativePage
            config.copy(
                desktop =
                    config.desktop.copy(
                        negativePage =
                            old.copy(
                                shelfRows =
                                    (
                                        old.effectiveShelfRows +
                                            delta
                                    ).coerceIn(1, 5),
                            ),
                    ),
            )
        }
        onChanged()
    }
    val controls =
        listOf(
            control("−", R.id.shelf_rows_minus) { step(-1) }.apply { contentDescription = "减少书架行数" },
            InkWidgets.caption(context, page.effectiveShelfRows.toString()).apply {
                id = R.id.shelf_rows_count
                gravity = Gravity.CENTER
                minWidth = tokens.dp(24f)
            },
            control("＋", R.id.shelf_rows_plus) { step(1) }.apply { contentDescription = "增加书架行数" },
            control("${ShelfSourceSupport.label(page.shelfSource)} ▾", R.id.shelf_source) {
                val sources = listOf(ShelfSourceSupport.WEREAD, ShelfSourceSupport.LOCAL)
                AlertDialog
                    .Builder(context)
                    .setTitle("书架书源")
                    .setSingleChoiceItems(
                        sources.map(ShelfSourceSupport::label).toTypedArray(),
                        sources.indexOf(page.effectiveShelfSource),
                    ) { dialog, index ->
                        AppServices.config.edit { config ->
                            config.copy(
                                desktop =
                                    config.desktop.copy(
                                        negativePage = config.desktop.negativePage.copy(shelfSource = sources[index]),
                                    ),
                            )
                        }
                        dialog.dismiss()
                        onChanged()
                    }.setNegativeButton("取消", null)
                    .show()
            },
        )
    controls.forEach { row.addView(it, row.childCount - 1) }
    (row.getChildAt(row.childCount - 1) as TextView).apply {
        id = R.id.shelf_refresh
        minWidth = tokens.dp(40f)
        minHeight = tokens.dp(40f)
        gravity = Gravity.CENTER
    }
    return row
}
