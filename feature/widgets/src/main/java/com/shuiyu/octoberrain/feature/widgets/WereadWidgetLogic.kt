package com.shuiyu.octoberrain.feature.widgets

import kotlin.math.roundToInt

/**
 * 微信读书组件的三态。
 *
 * UNCONFIGURED 表示密钥为空或来源被关闭；EMPTY 表示已配置但当前没有可展示的数据；
 * READY 表示存在可绘制数据。
 */
enum class WereadWidgetState {
    UNCONFIGURED,
    EMPTY,
    READY,
}

/**
 * 微信读书组件的纯逻辑集合。
 *
 * 所有函数只依赖参数，不读取 Android 上下文与全局状态，便于在 JVM 用例中验证
 * 分桶、周期起点、空态选择与指纹生成。
 */
internal object WereadWidgetLogic {
    /** 未配置密钥或来源关闭时的提示文案。 */
    const val MESSAGE_UNCONFIGURED = "微信读书未配置"

    /** 已配置但无数据时的提示文案。 */
    const val MESSAGE_EMPTY = "暂无阅读数据"

    /** 有效阅读天数无法计算时的兜底文案。 */
    const val MESSAGE_NO_RECORD = "尚无阅读记录"

    /** 月度账单缺少上月数据时展示的比较结果。 */
    const val DELTA_NONE = "无上月数据"

    /** 月度账单与上月持平时展示的比较结果。 */
    const val DELTA_FLAT = "与上月持平"

    /**
     * 解析组件的三态。
     *
     * 参数 key 为微信读书密钥；enabled 为集成开关；shelfEnabled 为书架侧开关；
     * hasData 表示本次读取是否得到可展示数据。密钥为空或任一开关关闭时优先返回
     * [WereadWidgetState.UNCONFIGURED]，其余按数据有无返回 EMPTY 或 READY。
     */
    fun stateOf(
        key: String,
        enabled: Boolean,
        shelfEnabled: Boolean,
        hasData: Boolean,
    ): WereadWidgetState =
        when {
            key.isBlank() || !enabled || !shelfEnabled -> WereadWidgetState.UNCONFIGURED
            hasData -> WereadWidgetState.READY
            else -> WereadWidgetState.EMPTY
        }

    /** 返回三态对应的提示文案，READY 时不产生文案。 */
    fun messageOf(state: WereadWidgetState): String =
        when (state) {
            WereadWidgetState.UNCONFIGURED -> MESSAGE_UNCONFIGURED
            WereadWidgetState.EMPTY -> MESSAGE_EMPTY
            WereadWidgetState.READY -> ""
        }

    /** 把分钟数格式化为「小时 + 分钟」文本，零值显示为「0 分钟」。 */
    fun formatMinutes(minutes: Int): String {
        if (minutes <= 0) return "0 分钟"
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "$rest 分钟"
            rest == 0 -> "$hours 小时"
            else -> "$hours 小时 $rest 分钟"
        }
    }

    /**
     * 计算本月相对上月的增幅百分比。
     *
     * 参数 current 为本月分钟数，last 为上月分钟数。上月为零时返回 null，
     * 由调用方展示 [DELTA_NONE]；结果为四舍五入后的整数百分比。
     */
    fun deltaPercent(
        current: Int,
        last: Int,
    ): Int? {
        if (last <= 0) return null
        return ((current - last) * 100f / last).roundToInt()
    }

    /** 把增幅百分比格式化为带符号文本；无法比较时返回 [DELTA_NONE]。 */
    fun formatDelta(
        current: Int,
        last: Int,
    ): String {
        val percent = deltaPercent(current, last) ?: return DELTA_NONE
        return when {
            percent > 0 -> "+$percent%"
            percent < 0 -> "$percent%"
            else -> DELTA_FLAT
        }
    }

    /** 统计逐日分钟数中大于零的天数。 */
    fun activeDays(days: List<Int>): Int = days.count { it > 0 }

    /** 计算逐日分钟数中的最长连续阅读天数；零值或空列表返回 0。 */
    fun longestStreak(days: List<Int>): Int {
        var best = 0
        var current = 0
        for (minutes in days) {
            if (minutes > 0) {
                current++
                if (current > best) best = current
            } else {
                current = 0
            }
        }
        return best
    }

    /** 返回逐日分钟数中的峰值，空列表返回 0。 */
    fun peakMinutes(days: List<Int>): Int = days.maxOrNull() ?: 0
}
