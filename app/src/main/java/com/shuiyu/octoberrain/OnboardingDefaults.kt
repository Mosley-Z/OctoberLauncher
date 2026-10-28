package com.shuiyu.octoberrain

import com.shuiyu.octoberrain.core.common.DesktopMode
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.WEREAD_BOARD_WIDGET_ID

/** 首次完成引导时设置普通桌面的阅读组件，之后保留用户自行选择。 */
internal fun LauncherConfig.completeOnboarding(now: Long): LauncherConfig {
    if (onboarding.completed) return this
    val useReading =
        onboarding.wereadDecision == true &&
            integrations.wereadEnabled &&
            !integrations.wereadApiKey.isNullOrBlank() &&
            desktop.mode == DesktopMode.NORMAL
    val home =
        if (useReading) {
            desktop.copy(
                widgetId = WEREAD_BOARD_WIDGET_ID,
                wereadBoardPoolMask = desktop.wereadBoardPoolMask or (1 shl ONBOARDING_MONTH_FORM),
                negativePage =
                    desktop.negativePage.copy(
                        forms = desktop.negativePage.forms + (WEREAD_BOARD_WIDGET_ID to ONBOARDING_MONTH_FORM),
                    ),
            )
        } else {
            desktop
        }
    return copy(desktop = home, onboarding = onboarding.copy(completed = true, createdAt = now))
}

/** 微信读书形态持久化顺序：本周0、本月1、本书2、本记3、待办4。 */
private const val ONBOARDING_MONTH_FORM = 1
