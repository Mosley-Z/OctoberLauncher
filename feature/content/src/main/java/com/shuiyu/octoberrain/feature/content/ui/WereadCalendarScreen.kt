package com.shuiyu.octoberrain.feature.content.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.ContentSource
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.WereadMonth
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.ContentRegistry
import com.shuiyu.octoberrain.feature.content.ContentSourceImpl
import com.shuiyu.octoberrain.feature.content.data.ContentPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * 阅读月历页。
 *
 * 按日展示本月阅读时长热力，点击日期在下方展开当日统计。数据来自
 * [ContentSourceImpl.wereadMonth]，未配置密钥或没有月度数据时显示对应空态。
 */
@Suppress("TooManyFunctions")
class WereadCalendarScreen : Screen {
    private lateinit var hostContext: Context
    private var pageView: InkPage? = null
    private var body: LinearLayout? = null
    private var month: WereadMonth? = null
    private var selectedDay: Int? = null
    private var loading = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null

    /** 页面容器的上下文。 */
    private val context: Context
        get() = hostContext

    override fun createView(context: Context): View {
        hostContext = context
        val created = InkPage(context, "阅读月历", onBack = { goBack() })
        pageView = created
        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
        body = content
        created.content.addView(content)
        return created
    }

    override fun onResume() {
        loadMonth()
    }

    override fun onPause() {
        loadJob?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
    }

    /** 读取本月逐日阅读时长并绘制。 */
    private fun loadMonth() {
        val container = body ?: return
        container.removeAllViews()
        val source = ContentRegistry.source()
        val configured = source?.wereadClient?.isConfigured() == true
        val enabled = runCatching { ContentPaths.integrations().wereadEnabled }.getOrDefault(false)
        when {
            source == null ->
                renderMessage("微信读书尚未就绪", "桌面服务尚未完成初始化，请稍后重新进入。")
            !configured || !enabled ->
                renderMessage(
                    title = "未配置微信读书",
                    message = "填写密钥并启用来源后，月历显示每日阅读时长。",
                    actionLabel = "前往设置",
                    onAction = { navigate(Destination.Weread) },
                )
            else -> requestMonth(container, source)
        }
    }

    /** 发起月度数据读取，读取期间只允许一个任务。 */
    @Suppress("TooGenericExceptionCaught")
    private fun requestMonth(
        container: LinearLayout,
        source: ContentSource,
    ) {
        if (loading) return
        loading = true
        val indicator = ContentUi.loading(container)
        loadJob?.cancel()
        loadJob =
            scope.launch {
                try {
                    month = source.wereadMonth()
                    loading = false
                    ContentUi.removeLoading(indicator)
                    renderCalendar(container)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    loading = false
                    ContentUi.removeLoading(indicator)
                    renderMessage("读取失败", "无法获取月度数据：${error.message ?: "未知错误"}。")
                }
            }
    }

    /** 绘制标题、星期行、日期网格与当日统计。 */
    private fun renderCalendar(container: LinearLayout) {
        val data = month
        if (data == null) {
            renderMessage("暂无月历数据", "本月没有阅读记录，阅读后可查看每日热力。")
            return
        }
        val now = LocalDate.now()
        val cells = WereadSectionLogic.monthCells(now.year, now.monthValue, data.days)
        container.addView(buildMonthTitle(data, now))
        container.addView(weekdayRow())
        addMonthGrid(container, cells)
        addSelectionSummary(container, data, now)
    }

    /** 本月总时长与环比卡片。 */
    private fun buildMonthTitle(
        data: WereadMonth,
        now: LocalDate,
    ): View {
        val title = ContentUi.card(context)
        title.addView(ContentUi.cardTitle(context, "${now.year} 年 ${now.monthValue} 月"))
        title.addView(
            InkWidgets.textView(
                context,
                ContentUi.formatMinutes(data.monthMinutes),
                DesignRuntime.tokens.textHeading,
                InkColors.INK,
            ),
        )
        title.addView(
            InkWidgets
                .caption(
                    context,
                    "${WereadSectionLogic.deltaText(data.monthMinutes, data.lastMonthMinutes)}（上月 ${
                        ContentUi.formatMinutes(data.lastMonthMinutes)
                    }）",
                ).apply { setPadding(0, DesignRuntime.tokens.gap / 2, 0, 0) },
        )
        return title
    }

    /** 按七列绘制日期网格，末行用空位补齐。 */
    private fun addMonthGrid(
        container: LinearLayout,
        cells: List<CalendarCell>,
    ) {
        val tokens = DesignRuntime.tokens
        for (chunk in cells.chunked(7)) {
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                }
            for (cell in chunk) {
                row.addView(dayCell(cell))
            }
            repeat(7 - chunk.size) {
                row.addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, tokens.dp(40f), 1f)
                    },
                )
            }
            container.addView(row)
        }
    }

    /** 选中日期的统计卡片，未选中时显示操作提示。 */
    private fun addSelectionSummary(
        container: LinearLayout,
        data: WereadMonth,
        now: LocalDate,
    ) {
        val day = selectedDay
        if (day == null) {
            container.addView(
                InkWidgets.caption(context, "点击日期查看当日阅读时长。").apply {
                    setPadding(0, DesignRuntime.tokens.gap, 0, 0)
                },
            )
            return
        }
        val minutes = data.days.getOrElse(day - 1) { 0 }
        container.addView(
            ContentUi.noticeCard(
                context = context,
                message = WereadSectionLogic.daySummary(now.year, now.monthValue, day, minutes),
            ),
        )
    }

    /** 星期行，周一在前。 */
    private fun weekdayRow(): View {
        val tokens = DesignRuntime.tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams =
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    topMargin = tokens.gap
                }
            WereadSectionLogic.WEEKDAYS.forEach { label ->
                addView(
                    InkWidgets.caption(context, label).apply {
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    },
                )
            }
        }
    }

    /** 单个日期格，按热力等级映射四级灰，选中日期使用墨色底。 */
    private fun dayCell(cell: CalendarCell): View {
        val tokens = DesignRuntime.tokens
        val side = tokens.dp(40f)
        if (cell.day == 0) {
            return View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, side, 1f)
            }
        }
        val selected = cell.day == selectedDay
        return TextView(context).apply {
            text = cell.day.toString()
            gravity = Gravity.CENTER
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, tokens.textBody)
            setTextColor(if (selected || cell.level >= 4) InkColors.PAPER else InkColors.INK)
            setBackgroundColor(
                when {
                    selected -> InkColors.INK
                    cell.level >= 4 -> InkColors.INK
                    cell.level == 3 -> InkColors.INK_2
                    cell.level == 2 -> InkColors.INK_3
                    cell.level == 1 -> InkColors.INK_4
                    else -> InkColors.PAPER
                },
            )
            isClickable = true
            isFocusable = true
            layoutParams =
                LinearLayout.LayoutParams(0, side, 1f).apply {
                    marginEnd = 2
                    bottomMargin = 2
                }
            setOnClickListener {
                selectedDay = cell.day
                val container = body ?: return@setOnClickListener
                container.removeAllViews()
                renderCalendar(container)
            }
        }
    }

    /** 空态或错误提示。 */
    private fun renderMessage(
        title: String,
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        val container = body ?: return
        container.addView(
            ContentUi.emptyCard(
                context = context,
                title = title,
                message = message,
                actionLabel = actionLabel,
                onAction = onAction,
            ),
        )
    }

    /** 打开内置页面。 */
    private fun navigate(destination: Destination) {
        runCatching { AppServices.router.navigate(destination) }
    }

    /** 返回上一页。 */
    private fun goBack() {
        runCatching { AppServices.router.back() }
    }
}
