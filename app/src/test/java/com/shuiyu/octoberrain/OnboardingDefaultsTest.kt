package com.shuiyu.octoberrain

import com.shuiyu.octoberrain.core.common.DesktopMode
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.PageWidgetConfig
import com.shuiyu.octoberrain.core.common.WEREAD_BOARD_WIDGET_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingDefaultsTest {
    private fun connected(): LauncherConfig {
        val config = LauncherConfig()
        return config.copy(
            onboarding = config.onboarding.copy(wereadDecision = true),
            integrations = config.integrations.copy(wereadEnabled = true, wereadApiKey = "test-placeholder"),
        )
    }

    @Test fun normalConnectedHomeStartsWithMonthAndRetainsOtherPages() {
        val base = connected()
        val before =
            base.copy(
                desktop =
                    base.desktop.copy(
                        pageWidgets = mapOf(1 to PageWidgetConfig(widgetId = "calendar")),
                        wereadBoardPoolMask = 1,
                    ),
            )
        val after = before.completeOnboarding(123L)
        assertEquals(WEREAD_BOARD_WIDGET_ID, after.desktop.widgetId)
        assertEquals(1, after.desktop.negativePage.forms[WEREAD_BOARD_WIDGET_ID])
        assertTrue(after.desktop.wereadBoardPoolMask and 2 != 0)
        assertEquals(before.desktop.pageWidgets, after.desktop.pageWidgets)
        assertTrue(after.onboarding.completed)
        assertEquals(123L, after.onboarding.createdAt)
    }

    @Test fun incompleteConnectionAndImmersiveRetainOriginalComponent() {
        val base = connected()
        val cases =
            listOf(
                base.copy(onboarding = base.onboarding.copy(wereadDecision = false)),
                base.copy(integrations = base.integrations.copy(wereadEnabled = false)),
                base.copy(integrations = base.integrations.copy(wereadApiKey = "  ")),
                base.copy(desktop = base.desktop.copy(mode = DesktopMode.IMMERSIVE)),
            )
        cases.forEach { before ->
            val after = before.completeOnboarding(123L)
            assertEquals(before.desktop, after.desktop)
            assertTrue(after.onboarding.completed)
        }
    }

    @Test fun completedOnboardingNeverOverridesLaterSelection() {
        val base = connected()
        val before = base.copy(onboarding = base.onboarding.copy(completed = true, createdAt = 10L))
        assertSame(before, before.completeOnboarding(123L))
    }
}
