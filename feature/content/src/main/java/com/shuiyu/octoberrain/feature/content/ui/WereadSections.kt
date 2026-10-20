package com.shuiyu.octoberrain.feature.content.ui

import com.shuiyu.octoberrain.core.common.WereadNoteSnapshot
import com.shuiyu.octoberrain.core.common.WereadSection
import com.shuiyu.octoberrain.core.common.WereadStatsPeriod
import java.time.YearMonth

/** 笔记分组：同一本书的划线聚合在一起。 */
internal data class NoteGroup(
    val bookTitle: String,
    val notes: List<WereadNoteSnapshot>,
)

/**
 * 月历单元格。
 *
 * @property day 当月日期，1 起；0 表示月初用于对齐星期的空位
 * @property minutes 当日阅读分钟数
 * @property level 热力等级，取值 0 到 4
 */
internal data class CalendarCell(
    val day: Int,
    val minutes: Int,
    val level: Int,
)

/**
 * 微信读书页面的纯逻辑。
 *
 * 提供分段与周期名称、可见性判断、空态文案、笔记分组、热力等级与月历排布，
 * 供页面与测试共用；不依赖 Android 上下文。
 */
internal object WereadSectionLogic {
    /** 分段顺序，与顶部页签一致。 */
    val SECTIONS: List<WereadSection> =
        listOf(
            WereadSection.OVERVIEW,
            WereadSection.STATS,
            WereadSection.NOTES,
            WereadSection.BILL,
            WereadSection.RANK,
        )

    /** 统计分段支持的周期，顺序为周、月、年、累计。 */
    val PERIODS: List<WereadStatsPeriod> =
        listOf(
            WereadStatsPeriod.WEEKLY,
            WereadStatsPeriod.MONTHLY,
            WereadStatsPeriod.ANNUALLY,
            WereadStatsPeriod.OVERALL,
        )

    /** 星期标签，周一在前。 */
    val WEEKDAYS: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

    /**
     * 返回分段的中文名称。
     *
     * @param section 目标分段
     */
    fun label(section: WereadSection): String =
        when (section) {
            WereadSection.OVERVIEW -> "总览"
            WereadSection.STATS -> "统计"
            WereadSection.NOTES -> "笔记"
            WereadSection.BILL -> "账单"
            WereadSection.RANK -> "排行"
        }

    /**
     * 返回可见分段；未配置密钥或来源关闭时不显示页签。
     *
     * @param configured 密钥是否已配置
     * @param enabled 微信读书来源是否启用
     */
    fun visibleSections(
        configured: Boolean,
        enabled: Boolean,
    ): List<WereadSection> = if (configured && enabled) SECTIONS else emptyList()

    /**
     * 返回周期中文名称。
     *
     * @param period 统计周期
     */
    fun periodLabel(period: WereadStatsPeriod): String =
        when (period) {
            WereadStatsPeriod.WEEKLY -> "本周"
            WereadStatsPeriod.MONTHLY -> "本月"
            WereadStatsPeriod.ANNUALLY -> "本年"
            WereadStatsPeriod.OVERALL -> "累计"
        }

    /**
     * 返回分段在无数据时的提示文案。
     *
     * @param section 当前分段
     * @param period 统计分段当前的周期，其他分段忽略
     */
    fun emptyMessage(
        section: WereadSection,
        period: WereadStatsPeriod = WereadStatsPeriod.WEEKLY,
    ): String =
        when (section) {
            WereadSection.OVERVIEW -> "暂无月度统计与书架数据，可稍后刷新重试。"
            WereadSection.STATS -> "${periodLabel(period)}统计暂无数据。"
            WereadSection.NOTES -> "暂无笔记，划线同步后显示在此处。"
            WereadSection.BILL -> "暂无账单数据，本月阅读后生成明细。"
            WereadSection.RANK -> "暂无排行，书架有阅读进度后生成。"
        }

    /**
     * 按书名分组笔记，组内保持输入顺序。
     *
     * 书名缺失的笔记归入「未知书籍」，分组顺序按首次出现的书名排列。
     *
     * @param notes 笔记列表
     */
    fun groupNotes(notes: List<WereadNoteSnapshot>): List<NoteGroup> {
        val order = LinkedHashMap<String, MutableList<WereadNoteSnapshot>>()
        notes.forEach { note ->
            val title = note.bookTitle.trim().ifEmpty { "未知书籍" }
            order.getOrPut(title) { mutableListOf() }.add(note)
        }
        return order.map { (title, items) -> NoteGroup(title, items) }
    }

    /**
     * 计算热力等级，按峰值等分四档。
     *
     * @param minutes 当日阅读分钟数
     * @param peak 当月单日峰值，小于 1 时按 1 处理
     */
    fun heatLevel(
        minutes: Int,
        peak: Int,
    ): Int {
        if (minutes <= 0) return 0
        val top = peak.coerceAtLeast(1)
        val percent = (minutes.toLong() * 100L / top.toLong()).toInt()
        return when {
            percent <= 25 -> 1
            percent <= 50 -> 2
            percent <= 75 -> 3
            else -> 4
        }
    }

    /**
     * 生成整月单元格，月初按星期一为首日对齐。
     *
     * @param year 年份
     * @param month 月份，1 到 12
     * @param minutes 当月逐日分钟数，缺失日期按 0 处理
     */
    fun monthCells(
        year: Int,
        month: Int,
        minutes: List<Int>,
    ): List<CalendarCell> {
        val monthLength = YearMonth.of(year, month).lengthOfMonth()
        val leading =
            YearMonth
                .of(year, month)
                .atDay(1)
                .dayOfWeek.value - 1
        val peak = minutes.maxOrNull() ?: 0
        val cells = MutableList(leading) { CalendarCell(day = 0, minutes = 0, level = 0) }
        for (day in 1..monthLength) {
            val value = minutes.getOrElse(day - 1) { 0 }.coerceAtLeast(0)
            cells += CalendarCell(day = day, minutes = value, level = heatLevel(value, peak))
        }
        return cells
    }

    /**
     * 返回单日统计说明。
     *
     * @param year 年份
     * @param month 月份
     * @param day 日期
     * @param minutes 当日分钟数
     */
    fun daySummary(
        year: Int,
        month: Int,
        day: Int,
        minutes: Int,
    ): String {
        val time = ContentUi.formatMinutes(minutes)
        return "$year 年 $month 月 $day 日阅读 $time"
    }

    /**
     * 返回与上期相比的说明文案。
     *
     * @param current 本期分钟数
     * @param last 上期分钟数
     */
    fun deltaText(
        current: Int,
        last: Int,
    ): String {
        val delta = current - last
        return when {
            delta > 0 -> "比上月多 ${ContentUi.formatMinutes(delta)}"
            delta < 0 -> "比上月少 ${ContentUi.formatMinutes(-delta)}"
            else -> "与上月持平"
        }
    }

    /**
     * 把阅读进度转换为百分比文案。
     *
     * @param progress 进度，取值 0 到 1，超出范围时夹取
     */
    fun progressText(progress: Float): String {
        val percent = (progress.coerceIn(0f, 1f) * 100f).toInt()
        return "已读 $percent%"
    }
}
