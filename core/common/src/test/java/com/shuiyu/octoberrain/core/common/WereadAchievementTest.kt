package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证成就设置与原版目标、边界文案一致。 */
class WereadAchievementTest {
    @Test
    fun `remaining duration follows original second minute and hour boundaries`() {
        val appearance =
            WereadAppearance(
                achievementEnabled = true,
                achievementWeekKind = WereadAchievement.KIND_CUSTOM,
                achievementWeekMin = 75,
            )
        mapOf(
            1L to "1秒",
            59L to "59秒",
            60L to "1分钟",
            3599L to "59分钟",
            3600L to "1小时0分",
        ).forEach { (remaining, expected) ->
            assertEquals(
                "本周距离您的目标还有 $expected",
                WereadAchievement.text(appearance, true, true, 4500L - remaining),
            )
        }
        assertEquals("恭喜您已达成你的目标", WereadAchievement.text(appearance, true, true, 4500L))
    }

    @Test
    fun `legacy minutes without kinds become custom goals but explicit off wins`() {
        val legacy =
            WereadAppearance.of(
                Json
                    .parseToJsonElement(
                        """{"bool":{"achv_enabled":true},"int":{"achv_week_min":75,"achv_month_min":120}}""",
                    ).jsonObject,
            )
        assertEquals(WereadAchievement.KIND_CUSTOM, legacy.achievementWeekKind)
        assertEquals(WereadAchievement.KIND_CUSTOM, legacy.achievementMonthKind)
        assertEquals(75, legacy.effectiveWeekMin)
        assertEquals(120, legacy.effectiveMonthMin)
        assertEquals("本周距离您的目标还有 15分钟", WereadAchievement.text(legacy, true, true, 3600))
        val explicitOff =
            WereadAppearance.of(
                Json
                    .parseToJsonElement(
                        """{"bool":{"cb_achv":true},"int":{"achv_week_min":75,"achv_week_kind":0}}""",
                    ).jsonObject,
            )
        assertEquals(WereadAchievement.KIND_OFF, explicitOff.achievementWeekKind)
        assertNull(WereadAchievement.text(explicitOff, true, true, 0))
    }

    @Test
    fun `settings keys supply enabled goals and custom hours`() {
        val appearance =
            WereadAppearance.of(
                Json
                    .parseToJsonElement(
                        """{
                    "bool":{"cb_achv":true},
                    "int":{"achv_week_kind":1,"achv_month_kind":3},
                    "string":{"achv_month_custom":"1.25"}
                }""",
                    ).jsonObject,
            )
        assertTrue(appearance.achievementEnabled)
        assertEquals(600, appearance.effectiveWeekMin)
        assertEquals(75, appearance.effectiveMonthMin)
    }

    @Test
    fun `disabled canonical setting wins over legacy enabled setting`() {
        val appearance =
            WereadAppearance.of(
                Json.parseToJsonElement("""{"bool":{"cb_achv":false,"achv_enabled":true}}""").jsonObject,
            )
        assertFalse(appearance.achievementEnabled)
        assertFalse(WereadAppearance().achievementEnabled)
    }

    @Test
    fun `presets preserve original week and month hours`() {
        assertEquals(600, WereadAchievement.targetMinutes(true, 1, 0))
        assertEquals(1500, WereadAchievement.targetMinutes(true, 2, 0))
        assertEquals(2400, WereadAchievement.targetMinutes(false, 1, 0))
        assertEquals(6000, WereadAchievement.targetMinutes(false, 2, 0))
        assertEquals(0, WereadAchievement.targetMinutes(true, 0, 99))
    }

    @Test
    fun `custom hours round to minutes and reject invalid values`() {
        assertEquals(75, WereadAchievement.customMinutes("1.25"))
        assertEquals(61, WereadAchievement.customMinutes("1.01"))
        listOf("", "NaN", "Infinity", "-5", "99999999999").forEach {
            assertEquals(0, WereadAchievement.customMinutes(it))
        }
    }

    @Test
    fun `preset shows remaining duration and achievement at boundary`() {
        val appearance = WereadAppearance(achievementEnabled = true, achievementWeekKind = 1)
        assertEquals("本周距离完美阅读周还有 1小时0分", WereadAchievement.text(appearance, true, true, 32400))
        assertEquals("本周距离完美阅读周还有 1秒", WereadAchievement.text(appearance, true, true, 35999))
        assertEquals("恭喜您已达成完美阅读周成就", WereadAchievement.text(appearance, true, true, 36000))
    }

    @Test
    fun `custom goal and monthly preset use corresponding wording`() {
        val custom = WereadAppearance(achievementEnabled = true, achievementMonthKind = 3, achievementMonthMin = 75)
        assertEquals("本月距离您的目标还有 15分钟", WereadAchievement.text(custom, false, true, 3600))
        assertEquals("恭喜您已达成你的目标", WereadAchievement.text(custom, false, true, 4500))
        val berserk = WereadAppearance(achievementEnabled = true, achievementMonthKind = 2)
        assertEquals("恭喜您已达成狂暴阅读月成就", WereadAchievement.text(berserk, false, true, 360000))
    }

    @Test
    fun `historical period disabled and empty goals are hidden`() {
        val enabled = WereadAppearance(achievementEnabled = true, achievementWeekKind = 1)
        assertNull(WereadAchievement.text(enabled, true, false, 999999))
        assertNull(WereadAchievement.text(enabled.copy(achievementEnabled = false), true, true, 0))
        assertNull(WereadAchievement.text(enabled.copy(achievementWeekKind = 0), true, true, 0))
        assertNull(WereadAchievement.text(enabled.copy(achievementWeekKind = 3), true, true, 0))
    }
}
