package com.shuiyu.octoberrain.feature.wereadapp

import com.inkread.weekread.kernel.PeriodCalendar

/**
 * 阅读页展示的五种卡片形态。
 *
 * 与内核 [PeriodCalendar] 的模式常量一一对应，顺序与五档下拉一致。
 *
 * @param mode 内核形态标识
 * @param label 形态简称，用于页签与下拉
 */
internal enum class WereadCardForm(
    val mode: String,
    val label: String,
) {
    /** 本周形态。 */
    WEEKLY(PeriodCalendar.WEEKLY, "本周"),

    /** 本月形态。 */
    MONTHLY(PeriodCalendar.MONTHLY, "本月"),

    /** 本书形态。 */
    BOOK(PeriodCalendar.BOOK, "本书"),

    /** 本记形态。 */
    NOTE(PeriodCalendar.NOTE, "本记"),

    /** 待办形态。 */
    TODO(PeriodCalendar.TODO, "待办"),
    ;

    companion object {
        /** 按内核模式标识查找形态，未知标识返回 null。 */
        fun ofMode(mode: String): WereadCardForm? = values().firstOrNull { it.mode == mode }
    }
}
