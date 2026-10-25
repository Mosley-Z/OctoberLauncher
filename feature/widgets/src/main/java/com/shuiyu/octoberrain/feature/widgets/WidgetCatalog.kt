package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import com.shuiyu.octoberrain.core.common.Capability
import com.shuiyu.octoberrain.core.common.WEREAD_BOARD_WIDGET_ID
import com.shuiyu.octoberrain.core.widget.WidgetCategory
import com.shuiyu.octoberrain.core.widget.WidgetProvider
import com.shuiyu.octoberrain.core.widget.WidgetRefreshPolicy
import com.shuiyu.octoberrain.core.widget.WidgetView

/**
 * 组件清单：集中声明标识、名称、说明、分类、刷新策略、能力依赖、支持形态与构造方式。
 *
 * 标识写入配置后长期有效，发布后不可修改；新增组件追加到列表末尾即可。
 * 被整体卡片与内置页面吸收的旧组件不再登记，旧标识由配置迁移统一改写。
 */
object WidgetCatalog {
    /**
     * 组件支持的形态描述，供选择页说明与后续设置项使用。
     *
     * @property id 形态标识，写入配置后保持不变
     * @property label 形态名称
     * @property hint 形态说明，无说明时为空串
     */
    data class WidgetForm(
        val id: String,
        val label: String,
        val hint: String = "",
    )

    /**
     * 单个组件的声明。
     *
     * @property id 配置标识
     * @property label 选择页标题
     * @property description 说明文字
     * @property category 选择页分组
     * @property refreshPolicy 整分刷新的适用范围
     * @property requiredCapabilities 组件依赖的系统能力
     * @property forms 组件支持的形态，按展示顺序排列
     * @property factory 创建组件视图的工厂
     */
    class Entry(
        override val id: String,
        override val label: String,
        override val description: String,
        override val category: WidgetCategory,
        override val refreshPolicy: WidgetRefreshPolicy,
        override val requiredCapabilities: Set<Capability> = emptySet(),
        val forms: List<WidgetForm> = emptyList(),
        private val factory: (Context) -> WidgetView,
    ) : WidgetProvider {
        override fun create(context: Context): WidgetView = factory(context)
    }

    /**
     * 时钟组件，内部定时器每分钟重绘。
     *
     * 常规布局为钟面在上、日期在下；组件高度低于 150dp 时自动采用单行紧凑布局，
     * 原紧凑时钟条目的两种形态由此保留。
     */
    private val clock =
        Entry(
            id = "clock",
            label = "时钟",
            description = "",
            category = WidgetCategory.TIME,
            refreshPolicy = WidgetRefreshPolicy.MANUAL,
            forms =
                listOf(
                    WidgetForm("stacked", "常规", "钟面在上，日期在下"),
                    WidgetForm("compact", "紧凑", "高度低于 150dp 时自动采用单行布局"),
                ),
        ) { ClockWidget(it) }

    /** 六款固定钟面，组件库与顺序清单直接从同一目录读取。 */
    private val clockStyles =
        ClockFace.entries.filter { it.includesDate }.map { face ->
            Entry(
                id = "clock_${face.id}",
                label = face.label,
                description = "",
                category = WidgetCategory.TIME,
                refreshPolicy = WidgetRefreshPolicy.MANUAL,
            ) { ClockWidget(it, face) }
        }

    /** 月历组件，整分刷新时重绘以更新当天标记；显示名改称月历，标识保持不变。 */
    private val calendar =
        Entry(
            id = "calendar",
            label = "月历",
            description = "本月月历，突出当天",
            category = WidgetCategory.TIME,
            refreshPolicy = WidgetRefreshPolicy.MINUTE,
        ) { CalendarWidget(it) }

    /** 待办周历组件，内容变化或页面恢复时重读共享待办清单。 */
    private val todoWeek =
        Entry(
            id = "todo_week",
            label = "待办周历",
            description = "七天待办，与微信读书待办共用同一份数据",
            category = WidgetCategory.TIME,
            refreshPolicy = WidgetRefreshPolicy.MINUTE,
        ) { TodoWeekWidget(it) }

    /** 自定义图片组件，支持多张图片轮播。 */
    private val image =
        Entry(
            id = "image",
            label = "图片",
            description = "显示自选图片，多张时按轮播间隔依次切换",
            category = WidgetCategory.LOCAL,
            refreshPolicy = WidgetRefreshPolicy.MANUAL,
            requiredCapabilities = setOf(Capability.STORAGE),
            forms = listOf(WidgetForm("single", "单张"), WidgetForm("carousel", "轮播")),
        ) { ImageWidget(it) }

    /** 占位组件。 */
    private val placeholder =
        Entry(
            id = "placeholder",
            label = "占位",
            description = "虚线占位框，点击打开组件库",
            category = WidgetCategory.OTHER,
            refreshPolicy = WidgetRefreshPolicy.MANUAL,
        ) { PlaceholderWidget(it) }

    /** 微信读书整体卡片组件，一张卡片承载五种形态。 */
    private val wereadBoard =
        Entry(
            id = WEREAD_BOARD_WIDGET_ID,
            label = "微信读书",
            description = "本周、本月、本书、本记与待办五形态",
            category = WidgetCategory.WEREAD,
            refreshPolicy = WidgetRefreshPolicy.EVENT,
            requiredCapabilities = setOf(Capability.WEREAD),
            forms =
                listOf(
                    WidgetForm("week", "本周"),
                    WidgetForm("month", "本月"),
                    WidgetForm("book", "本书"),
                    WidgetForm("note", "本记"),
                    WidgetForm("todo", "待办"),
                ),
        ) { WereadBoardWidget(it) }

    /** 全部组件，按注册顺序排列。 */
    val providers: List<WidgetProvider> =
        listOf(
            clock,
            calendar,
            todoWeek,
            image,
            placeholder,
            wereadBoard,
        ) + clockStyles

    /** 按分类组织的组件，供选择页分组展示。 */
    val byCategory: List<Pair<WidgetCategory, List<WidgetProvider>>> =
        listOf(
            WidgetCategory.TIME to (listOf(clock) + clockStyles + listOf(calendar, todoWeek)),
            WidgetCategory.LOCAL to listOf(image),
            WidgetCategory.WEREAD to listOf(wereadBoard),
            WidgetCategory.OTHER to listOf(placeholder),
        )

    /** 按标识查找组件声明。 */
    fun find(id: String): WidgetProvider? = providers.firstOrNull { it.id == id }
}
