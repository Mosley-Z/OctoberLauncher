package com.shuiyu.octoberrain.core.design

import com.shuiyu.octoberrain.core.device.DeviceProfile
import com.shuiyu.octoberrain.core.device.DisplayClass
import com.shuiyu.octoberrain.core.device.HeightClass
import com.shuiyu.octoberrain.core.device.SizeClass
import org.junit.Assert.assertTrue
import org.junit.Test

class SmallTextTest {
    @Test fun auxiliaryTextRemainsReadableAtMinimumScale() {
        for (density in listOf(1f, 1.36875f, 2f)) {
            val profile =
                DeviceProfile(
                    480,
                    800,
                    350,
                    584,
                    density,
                    SizeClass.Compact,
                    HeightClass.Normal,
                    DisplayClass.EinkFast,
                    0,
                    0,
                    800,
                    480,
                )
            val requested = InkTokens(profile, statusBarHeightDp = 52)
            org.junit.Assert.assertEquals(requested.dp(52f), requested.statusBarHeight)
            val short = InkTokens(profile.copy(heightClass = HeightClass.Short))
            assertTrue(short.statusBarHeight <= InkTokens(profile).statusBarHeight)
            val small = InkTokens(profile, 0.85f)
            val large = InkTokens(profile, 1.6f)
            assertTrue(small.textSmall >= 18f)
            assertTrue(small.textCaption >= 20f)
            assertTrue(large.textCaption >= small.textCaption)
        }
    }
}
