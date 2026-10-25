package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.ScreenProvider
import com.shuiyu.octoberrain.core.common.editWidgetPage
import com.shuiyu.octoberrain.core.common.forWidgetPage
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 组件库页面：按分类以两列卡片列出全部组件，点选后写入桌面配置并返回。
 *
 * 同一时刻只启用一个组件；卡片使用细描边与设计令牌圆角，
 * 选中项整卡反白，名称与说明的层级由字号区分。末尾提供关闭项。
 */
class WidgetPickerScreen(
    private val title: String = "选择组件",
    private val pageIndex: Int = 0,
) : Screen {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onDestroy() {
        scope.cancel()
    }

    override fun createView(context: Context): View {
        val page = InkPage(context, title, showBack = true) { AppServices.router.back() }
        fill(context, page.content)
        return page
    }

    /** 把分组与网格条目填入页面内容容器。 */
    private fun fill(
        context: Context,
        content: LinearLayout,
    ) {
        val tokens = DesignRuntime.tokens
        val currentId =
            AppServices.config.state.value.desktop
                .forWidgetPage(pageIndex)
                .widgetId

        for ((category, providers) in WidgetCatalog.byCategory) {
            content.addView(InkWidgets.heading(context, category.title))
            content.addView(grid(context, providers.map { Option(it.id, it.label, it.description) }, currentId))
            content.addView(InkWidgets.spacer(context, tokens.gap))
        }

        if (currentId == "image") {
            content.addView(
                InkWidgets.button(context, "图片组件设置") { ImageWidgetSettings.show(context, scope, pageIndex) },
            )
        }
        content.addView(InkWidgets.heading(context, "关闭"))
        content.addView(
            grid(context, listOf(Option("", "不使用组件", "桌面只显示应用宫格")), currentId),
        )
        content.addView(InkWidgets.spacer(context, tokens.gap))
    }

    /** 组件卡片所需的展示数据。 */
    private data class Option(
        val id: String,
        val label: String,
        val description: String,
    )

    /** 把一组组件排成两列网格，每行最多两个条目。 */
    private fun grid(
        context: Context,
        options: List<Option>,
        currentId: String,
    ): View {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        var index = 0
        while (index < options.size) {
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                }
            for (slot in 0 until COLUMNS) {
                val option = options.getOrNull(index + slot)
                if (option == null) {
                    row.addView(
                        LinearLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                        },
                    )
                } else {
                    row.addView(cell(context, option, option.id == currentId) { select(option.id) })
                }
            }
            column.addView(row)
            index += COLUMNS
        }
        return column
    }

    /** 创建单个组件卡片：1px 级细描边与令牌圆角，选中时整卡反白。 */
    private fun cell(
        context: Context,
        option: Option,
        selected: Boolean,
        onClick: () -> Unit,
    ): View {
        val tokens = DesignRuntime.tokens
        val padding = tokens.dp(12f)
        // 选中项以反白徽标与反相边框区分，卡片底色保持纸白，避免出现灰底
        val surface = InkColors.PAPER
        val primary = InkColors.INK
        val secondary = InkColors.INK_3
        val border = if (selected) InkColors.INK else InkColors.INK_3

        val cellView =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = tokens.gap
                        bottomMargin = tokens.gap
                    }
                minimumHeight = tokens.listRowHeight * 2
                setPadding(padding, padding, padding, padding)
                background = InkWidgets.background(tokens.radius, surface, tokens.stroke, border)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }

        val titleRow =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        titleRow.addView(
            InkWidgets.textView(context, option.label, tokens.textHeading, primary).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                maxLines = 1
                paint.isFakeBoldText = selected
            },
        )
        if (selected) {
            // 右上角反白徽标：墨底白字，与卡片细边框共同表示当前选中项
            titleRow.addView(
                (InkWidgets.textView(context, "已选", tokens.textSmall, InkColors.PAPER) as TextView).apply {
                    setPadding(tokens.gap, tokens.dp(1f), tokens.gap, tokens.dp(1f))
                    background = InkWidgets.background(tokens.radiusSmall, InkColors.INK, 0)
                },
            )
        }
        cellView.addView(titleRow)
        if (option.description.isNotBlank()) {
            cellView.addView(
                (InkWidgets.textView(context, option.description, tokens.textCaption, secondary) as TextView).apply {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
        }
        return cellView
    }

    /** 写入桌面组件标识并请求桌面局部重绘。 */
    private fun select(id: String) {
        AppServices.config.edit { config ->
            config.copy(
                desktop =
                    config.desktop.editWidgetPage(
                        pageIndex,
                    ) { it.copy(widgetId = id, widgetCycleIds = emptyList()) },
            )
        }
        AppServices.refresh.requestFullRefresh()
        AppServices.router.back()
    }

    companion object {
        /** 网格列数，窄屏也保持两列。 */
        const val COLUMNS = 2

        /** 注册到页面注册表的工厂。 */
        val Provider: ScreenProvider =
            object : ScreenProvider {
                override fun create(destination: Destination): Screen? =
                    when (destination) {
                        Destination.WidgetPicker -> WidgetPickerScreen()
                        is Destination.PageWidgetPicker -> WidgetPickerScreen(pageIndex = destination.page)
                        Destination.ProjectWidgets -> WidgetPickerScreen("桌面组件")
                        else -> null
                    }
            }
    }
}
