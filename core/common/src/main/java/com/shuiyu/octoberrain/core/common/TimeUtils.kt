package com.shuiyu.octoberrain.core.common

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 时间与日期工具。
 *
 * 所有格式化方法显式传入 Locale.CHINA 或十二小时制开关，避免依赖系统区域设置。
 */
object TimeUtils {
    /** 当前毫秒时间。 */
    fun nowMillis(): Long = System.currentTimeMillis()

    /**
     * 格式化时分。
     *
     * 参数 hour 与 minute 为 24 小时制数值；twelveHour 为 true 时输出十二小时制。
     */
    fun formatTime(
        hour: Int,
        minute: Int,
        twelveHour: Boolean,
    ): String =
        if (twelveHour) {
            val suffix = if (hour < 12) "上午" else "下午"
            val h =
                when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
            String.format(Locale.CHINA, "%s %d:%02d", suffix, h, minute)
        } else {
            String.format(Locale.CHINA, "%02d:%02d", hour, minute)
        }

    /** 当前时分字符串。 */
    fun currentTime(twelveHour: Boolean): String {
        val calendar = Calendar.getInstance()
        return formatTime(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), twelveHour)
    }

    /** 日期字符串，例如 10月19日 星期日。 */
    fun formatDate(millis: Long = nowMillis()): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val week = weekLabel(calendar.get(Calendar.DAY_OF_WEEK))
        return String.format(
            Locale.CHINA,
            "%d月%d日 %s",
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
            week,
        )
    }

    /** 完整日期与时间，例如 2026-10-19 03:14。 */
    fun formatDateTime(millis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))

    /** 日期部分，例如 2026-10-19。 */
    fun formatDay(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(millis))

    /** 星期标签。 */
    fun weekLabel(dayOfWeek: Int): String =
        when (dayOfWeek) {
            Calendar.MONDAY -> "星期一"
            Calendar.TUESDAY -> "星期二"
            Calendar.WEDNESDAY -> "星期三"
            Calendar.THURSDAY -> "星期四"
            Calendar.FRIDAY -> "星期五"
            Calendar.SATURDAY -> "星期六"
            else -> "星期日"
        }

    /** 短星期标签。 */
    fun weekShort(dayOfWeek: Int): String =
        when (dayOfWeek) {
            Calendar.MONDAY -> "一"
            Calendar.TUESDAY -> "二"
            Calendar.WEDNESDAY -> "三"
            Calendar.THURSDAY -> "四"
            Calendar.FRIDAY -> "五"
            Calendar.SATURDAY -> "六"
            else -> "日"
        }

    /**
     * 把分钟数格式化为中文时长。
     *
     * 参数 minutes 为分钟数，负数按 0 处理。
     */
    fun formatDuration(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val rest = safe % 60
        return when {
            hours <= 0 -> "$rest 分钟"
            rest == 0 -> "$hours 小时"
            else -> "$hours 小时 $rest 分"
        }
    }

    /** 相对时间描述，用于最近阅读与通知时间。 */
    fun relative(
        millis: Long,
        now: Long = nowMillis(),
    ): String {
        val delta = (now - millis).coerceAtLeast(0)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 24 * 60 -> "${minutes / 60} 小时前"
            minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} 天前"
            else -> formatDay(millis)
        }
    }
}

/**
 * 月历网格。
 *
 * 参数 days 为 42 格，空格为 null；todayIndex 为今日格下标；year 与 month 为显示月份。
 */
data class MonthGrid(
    val year: Int,
    val month: Int,
    val days: List<Int?>,
    val todayIndex: Int,
) {
    companion object {
        /**
         * 生成 42 格月历，以周一为起始。
         *
         * 参数 year 与 month 为目标月份，month 取值 1 到 12。
         */
        fun build(
            year: Int,
            month: Int,
        ): MonthGrid {
            val calendar =
                Calendar.getInstance().apply {
                    clear()
                    set(year, month - 1, 1)
                }
            val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val mondayOffset = (firstDayOfWeek + 6) % 7
            val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val today = Calendar.getInstance()
            val isCurrentMonth =
                today.get(Calendar.YEAR) == year &&
                    today.get(Calendar.MONTH) + 1 == month
            val todayDay = today.get(Calendar.DAY_OF_MONTH)

            val days = ArrayList<Int?>(42)
            for (index in 0 until 42) {
                val day = index - mondayOffset + 1
                days.add(if (day in 1..maxDay) day else null)
            }
            val todayIndex =
                if (isCurrentMonth && todayDay in 1..maxDay) {
                    mondayOffset + todayDay - 1
                } else {
                    -1
                }
            return MonthGrid(year, month, days, todayIndex)
        }

        /** 生成当前月网格。 */
        fun current(): MonthGrid {
            val calendar = Calendar.getInstance()
            return build(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
        }
    }
}

/**
 * 分钟对齐的时钟触发器。
 *
 * 每个整分回调一次，用于状态栏、钟面与组件刷新；回调在主线程执行。
 */
class MinuteTicker(
    private val onTick: (Long) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val runnable =
        object : Runnable {
            override fun run() {
                if (!running) return
                onTick(System.currentTimeMillis())
                val now = System.currentTimeMillis()
                val delay = 60_000L - (now % 60_000L)
                handler.postDelayed(this, delay)
            }
        }

    /** 启动；宿主已主动刷新时可跳过立即回调，直接等待下一整分。 */
    fun start(immediate: Boolean = true) {
        if (running) return
        running = true
        if (immediate) {
            handler.post(runnable)
        } else {
            handler.postDelayed(
                runnable,
                60_000L - System.currentTimeMillis() % 60_000L,
            )
        }
    }

    /** 停止并移除回调。 */
    fun stop() {
        running = false
        handler.removeCallbacks(runnable)
    }
}

/**
 * 对齐到指定间隔的定时触发器，间隔以毫秒计。
 *
 * 用于天气、微信读书等低频数据，回调在主线程执行。
 */
class IntervalTicker(
    private val intervalMs: Long,
    private val onTick: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val runnable =
        object : Runnable {
            override fun run() {
                if (!running) return
                onTick()
                handler.postDelayed(this, intervalMs)
            }
        }

    /** 启动，延迟一个间隔后首次触发。 */
    fun start() {
        if (running) return
        running = true
        handler.postDelayed(runnable, intervalMs)
    }

    /** 停止。 */
    fun stop() {
        running = false
        handler.removeCallbacks(runnable)
    }
}
