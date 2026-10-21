package com.shuiyu.octoberrain.core.common

import java.math.BigDecimal
import java.math.RoundingMode

/** 桌面阅读成就：复用微读墨记的档位、目标与文案，不产生网络请求。 */
object WereadAchievement {
    const val KIND_OFF: Int = 0
    const val KIND_PERFECT: Int = 1
    const val KIND_BERSERK: Int = 2
    const val KIND_CUSTOM: Int = 3

    /** 设置页以小时保存，自定义目标按原版四舍五入为整数分钟。 */
    fun customMinutes(hours: String): Int =
        runCatching {
            BigDecimal(hours.trim()).multiply(BigDecimal(60)).setScale(0, RoundingMode.HALF_UP).intValueExact()
        }.getOrDefault(0).coerceAtLeast(0)

    /** 预设周目标为 10/25 小时，月目标为 40/100 小时。 */
    fun targetMinutes(
        week: Boolean,
        kind: Int,
        customMinutes: Int,
    ): Int =
        when (kind) {
            KIND_PERFECT -> if (week) 600 else 2400
            KIND_BERSERK -> if (week) 1500 else 6000
            KIND_CUSTOM -> customMinutes.coerceAtLeast(0)
            else -> 0
        }

    /** 仅当前周/月展示；历史周期、关闭和无效目标不显示。 */
    fun text(
        appearance: WereadAppearance,
        week: Boolean,
        currentPeriod: Boolean,
        totalSeconds: Long,
    ): String? {
        val minutes = if (week) appearance.effectiveWeekMin else appearance.effectiveMonthMin
        return if (!appearance.achievementEnabled || !currentPeriod || minutes <= 0) {
            null
        } else {
            val kind = if (week) appearance.achievementWeekKind else appearance.achievementMonthKind
            val target = minutes * 60L
            val period = if (week) "本周" else "本月"
            val goal =
                if (kind == KIND_CUSTOM) {
                    "您的目标"
                } else {
                    (if (kind == KIND_BERSERK) "狂暴阅读" else "完美阅读") + (if (week) "周" else "月")
                }
            if (totalSeconds >= target) {
                if (kind == KIND_CUSTOM) "恭喜您已达成你的目标" else "恭喜您已达成${goal}成就"
            } else {
                "${period}距离${goal}还有 ${duration(target - totalSeconds.coerceAtLeast(0))}"
            }
        }
    }

    private fun duration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        return when {
            hours > 0 -> "${hours}小时${minutes}分"
            minutes > 0 -> "${minutes}分钟"
            else -> "${seconds}秒"
        }
    }
}
