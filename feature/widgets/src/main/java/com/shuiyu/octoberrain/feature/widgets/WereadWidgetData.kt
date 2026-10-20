package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.common.WereadRankEntry
import kotlin.math.roundToInt

/**
 * 微信读书组件的数值与文本转换集合：比例分档、指纹格、进度与排行。
 *
 * 所有函数只依赖参数，不读取 Android 上下文与全局状态，可在 JVM 用例中直接验证。
 */
internal object WereadWidgetData {
    /** 指纹图案的列数。 */
    const val FINGERPRINT_COLUMNS = 7

    /** 指纹图案的行数。 */
    const val FINGERPRINT_ROWS = 4

    /**
     * 把任意非负数值归一化为 0 到 1 的比例。
     *
     * 最大值为零时全部返回 0，避免除零；输入负值按零处理。
     */
    fun ratios(values: List<Int>): List<Float> {
        val max = values.maxOrNull() ?: 0
        return if (max <= 0) {
            values.map { 0f }
        } else {
            values.map { value -> (value.coerceAtLeast(0).toFloat() / max).coerceIn(0f, 1f) }
        }
    }

    /** 把比例映射为四档灰度：0 为空、1 为浅、2 为中、3 为深。 */
    fun levelOf(ratio: Float): Int =
        when {
            ratio <= 0f -> 0
            ratio <= 0.34f -> 1
            ratio <= 0.67f -> 2
            else -> 3
        }

    /** 取逐日数据末尾的 [count] 项，不足时在左侧补零。 */
    fun lastDays(
        days: List<Int>,
        count: Int,
    ): List<Int> =
        when {
            count <= 0 -> emptyList()
            days.size >= count -> days.takeLast(count)
            else -> List(count - days.size) { 0 } + days
        }

    /**
     * 生成指纹图案的灰度等级。
     *
     * 参数 days 为逐日分钟数；列数与行数固定为七列四行，共 28 格。取末尾 28 天，
     * 不足时左侧补零；每格按当日分钟数相对峰值的比例映射为四档灰度。
     */
    fun fingerprintCells(days: List<Int>): List<Int> {
        val window = lastDays(days, FINGERPRINT_COLUMNS * FINGERPRINT_ROWS)
        return ratios(window).map { levelOf(it) }
    }

    /** 把阅读进度限制在 0 到 1，用于进度条与百分比文本。 */
    fun clampProgress(progress: Float): Float = progress.coerceIn(0f, 1f)

    /** 取进度百分比整数，四舍五入。 */
    fun progressPercent(progress: Float): Int = (clampProgress(progress) * 100f).roundToInt()

    /** 按进度降序取前 [limit] 条排行，limit 小于 1 时返回空列表。 */
    fun rankEntries(
        entries: List<WereadRankEntry>,
        limit: Int,
    ): List<WereadRankEntry> = if (limit < 1) emptyList() else entries.sortedByDescending { it.progress }.take(limit)

    /**
     * 把笔记正文压成单行摘要。
     *
     * 参数 text 为划线或想法原文；换行与连续空白折叠为一个空格，超出 [maxLength]
     * 时截断并追加省略号。
     */
    fun noteSummary(
        text: String,
        maxLength: Int = 60,
    ): String {
        val compact = text.replace(Regex("\\s+"), " ").trim()
        val limit = maxLength.coerceAtLeast(2)
        return if (compact.length <= limit) compact else compact.take(limit - 1) + "…"
    }

    /** 生成阅读指纹的一句话标签，例如「连续 7 天 · 日均 45 分钟」。 */
    fun fingerprintLabel(
        streak: Int,
        averageMinutes: Int,
    ): String {
        val streakText = if (streak > 0) "连续 $streak 天" else WereadWidgetLogic.MESSAGE_NO_RECORD
        return "$streakText · 日均 ${WereadWidgetLogic.formatMinutes(averageMinutes)}"
    }
}
