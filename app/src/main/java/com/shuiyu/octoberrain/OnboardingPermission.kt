package com.shuiyu.octoberrain

internal data class OnboardingPermission(
    val key: String,
    val label: String,
    val hint: String,
)

internal fun onboardingPermissions(model: String): List<OnboardingPermission> {
    val p4 = model.equals("P4", ignoreCase = true)
    val accessibility =
        OnboardingPermission(
            "accessibility",
            "无障碍服务",
            if (p4) "影响 P4 状态栏的正常显示与交互，建议开启。" else "用于悬浮状态栏交互",
        )
    val others =
        listOf(
            OnboardingPermission("home", "主屏幕应用", "将十雨设为默认桌面"),
            OnboardingPermission("storage", "存储访问", "浏览 DCIM、Download 等本地文件夹需要存储访问；单次选图无需此权限"),
            OnboardingPermission("notification_listener", "通知读取", "用于桌面通知角标"),
        )
    return if (p4) listOf(accessibility) + others else others + accessibility
}

internal fun missingOnboardingPermissions(
    model: String,
    isHome: Boolean,
    snapshot: Map<String, Boolean>,
): List<String> =
    onboardingPermissions(model)
        .filter {
            if (it.key == "home") !isHome else snapshot[it.key] != true
        }.map { it.label }
