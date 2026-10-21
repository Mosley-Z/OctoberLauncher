package com.shuiyu.octoberrain.feature.widgets

/**
 * 把累计阅读秒数格式化为「Xh Ym」形式。
 *
 * 不足一分钟按一分钟计算，保证时长非零时不会显示为「0m」；数据缺失返回空串，
 * 由调用方隐藏时长部分。
 *
 * @param seconds 累计阅读秒数
 * @return 时长文案，可为空串
 */
internal fun readingDurationText(seconds: Int): String {
    if (seconds <= 0) return ""
    val totalMinutes = (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * 把进度百分数格式化为「已读 N%」形式。
 *
 * @param percent 阅读进度百分数，越界时按 0 到 100 截断
 * @return 已读文案
 */
internal fun readingProgressText(percent: Int): String = "已读 ${percent.coerceIn(0, PERCENT_MAX)}%"

/**
 * 拼接底部信息带的文字部分，百分比与时长以间隔点分隔。
 *
 * @param percent 阅读进度百分数
 * @param seconds 累计阅读秒数，为非正数时省略时长
 * @return 底部信息文案
 */
internal fun readingInfoText(
    percent: Int,
    seconds: Int,
): String {
    val duration = readingDurationText(seconds)
    val progress = readingProgressText(percent)
    return if (duration.isEmpty()) progress else "$progress$INFO_SEPARATOR$duration"
}

/**
 * 整理划线正文：合并连续空白并去除首尾空白。
 *
 * 划线原文可能带换行与缩进，绘制前统一为单行文本，交由排版按宽度换行。
 *
 * @param raw 原始划线正文
 * @return 整理后的正文，可为空串
 */
internal fun markTextOf(raw: String?): String = raw.orEmpty().replace(WHITESPACE, " ").trim()

/**
 * 生成 24 小时制时间文案。
 *
 * @param hourOfDay 小时，0 到 23
 * @param minute 分钟，0 到 59
 * @return 形如「22:24」的时间文案
 */
internal fun clockTextOf(
    hourOfDay: Int,
    minute: Int,
): String = "${hourOfDay.toString().padStart(DIGITS, '0')}:${minute.toString().padStart(DIGITS, '0')}"

/**
 * 生成中文日期文案。
 *
 * @param month 月份，1 到 12
 * @param day 日期，1 到 31
 * @param dayOfWeek 星期序号，1 表示周日，7 表示周六，与日历字段一致
 * @return 形如「星期三，10月7日」的日期文案
 */
internal fun dateTextOf(
    month: Int,
    day: Int,
    dayOfWeek: Int,
): String {
    val name = WEEKDAY_NAMES.getOrElse(dayOfWeek - 1) { WEEKDAY_NAMES.first() }
    return "星期$name，${month}月${day}日"
}

/** 一分钟包含的秒数。 */
private const val SECONDS_PER_MINUTE: Int = 60

/** 一小时包含的分钟数。 */
private const val MINUTES_PER_HOUR: Int = 60

/** 百分数上限。 */
private const val PERCENT_MAX: Int = 100

/** 两位补零的位数。 */
private const val DIGITS: Int = 2

/** 底部信息带中百分比与时长之间的间隔符号。 */
private const val INFO_SEPARATOR: String = "  ·  "

/** 空白字符匹配，用于整理划线正文。 */
private val WHITESPACE: Regex = Regex("\\s+")

/** 星期名称，下标零对应周日，与日历字段减一后对齐。 */
private val WEEKDAY_NAMES: List<String> = listOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 生成笔记来源文案，统一使用书名并以长破折号开头。
 *
 * @param title 书名
 * @return 来源文案
 */
internal fun noteSourceText(title: String): String = "— 《$title》"
