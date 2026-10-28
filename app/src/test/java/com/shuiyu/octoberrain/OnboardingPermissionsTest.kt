package com.shuiyu.octoberrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPermissionsTest {
    @Test fun p4AccessibilityComesFirstWithStatusBarExplanation() {
        val entries = onboardingPermissions("P4")
        assertEquals("accessibility", entries.first().key)
        assertTrue(entries.first().hint.contains("状态栏"))
        assertTrue(entries.first().hint.contains("建议开启"))
        assertEquals("home", onboardingPermissions("S4").first().key)
    }

    @Test fun confirmationNamesOnlyMissingSettings() {
        assertEquals(
            listOf("无障碍服务", "通知读取"),
            missingOnboardingPermissions("P4", true, mapOf("storage" to true)),
        )
        assertTrue(
            missingOnboardingPermissions(
                "P4",
                true,
                mapOf("storage" to true, "notification_listener" to true, "accessibility" to true),
            ).isEmpty(),
        )
    }
}
