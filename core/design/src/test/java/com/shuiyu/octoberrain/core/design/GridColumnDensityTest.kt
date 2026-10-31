package com.shuiyu.octoberrain.core.design

import com.shuiyu.octoberrain.core.device.DeviceProfile
import com.shuiyu.octoberrain.core.device.DisplayClass
import com.shuiyu.octoberrain.core.device.HeightClass
import com.shuiyu.octoberrain.core.device.SizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class GridColumnDensityTest {
    @Test
    fun automaticColumnsRemainStableForSameDpWindowAcrossDensities() {
        val cases = listOf(351 to 4, 412 to 4, 421 to 4, 599 to 4, 600 to 6, 839 to 6, 840 to 8)
        for ((widthDp, expectedColumns) in cases) {
            for (density in listOf(1f, 1.36875f, 1.5f, 2f, 3f)) {
                val widthPx = Math.round(widthDp * density)
                val heightPx = Math.round(1000 * density)
                val profile =
                    DeviceProfile(
                        widthPx = widthPx,
                        heightPx = heightPx,
                        widthDp = widthDp,
                        heightDp = 1000,
                        density = density,
                        sizeClass = SizeClass.fromWidth(widthDp),
                        heightClass = HeightClass.Tall,
                        display = DisplayClass.EinkFast,
                        hasFrontLight = false,
                        statusBarInsetPx = 0,
                        navigationInsetPx = 0,
                        longEdgePx = heightPx,
                        shortEdgePx = widthPx,
                    )
                val tokens = InkTokens(profile)
                verifyTextScaling(profile, density, tokens)
                assertEquals(4, profile.gridColumns(tokens.cellMinWidthDp, availableWidthDp = 412))
                assertEquals(8, profile.gridColumns(tokens.cellMinWidthDp, availableWidthDp = 840))
                assertEquals(
                    "widthDp=$widthDp density=$density",
                    expectedColumns,
                    profile.gridColumns(tokens.cellMinWidthDp),
                )
            }
        }
    }

    private fun verifyTextScaling(
        profile: DeviceProfile,
        density: Float,
        tokens: InkTokens,
    ) {
        for (appScale in listOf(0.85f, 1f, 1.6f)) {
            for (systemScale in listOf(1f, 1.3f, 2f)) {
                var convertedSp = 0f
                val scaled =
                    InkTokens(profile, appScale) {
                        convertedSp = it
                        it * density * systemScale
                    }
                assertEquals(14f * appScale * density * systemScale, scaled.textSize(14f), 0.001f)
                assertEquals(14f * appScale, convertedSp, 0.001f)
                assertEquals(tokens.dp(48f), scaled.dp(48f))
                org.junit.Assert.assertTrue(scaled.buttonHeight >= scaled.dp(48f))
                org.junit.Assert.assertTrue(scaled.labelHeight >= scaled.textBody)
            }
        }
    }
}
