package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_TRAY_REFRESH
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_TRAY_STATUS

/**
 * 状态栏与墨水屏刷新设置项声明。
 *
 * 保留原tray存储分区，状态栏与自动刷新按既有页面归属读写。
 */
internal object TraySpecs {
    /** 自动刷新间隔档位，单位分钟。 */
    private val REFRESH_VALUES: List<Int> = listOf(0, 5, 10, 15, 30)

    /** 自动刷新间隔选项文本。 */
    private val REFRESH_OPTIONS: List<String> = listOf("关闭", "5 分钟", "10 分钟", "15 分钟", "30 分钟")

    /** 返回桌面后的整刷延迟档位，单位毫秒。 */
    private val EXIT_DELAY_VALUES: List<Float> = (50..500 step 25).map { it.toFloat() }

    val groups: List<SettingsGroupSpec> =
        listOf(
            SettingsGroupSpec(id = GROUP_TRAY_STATUS, title = "状态栏", subtitle = "顶部图标显示项"),
            SettingsGroupSpec(
                id = GROUP_TRAY_REFRESH,
                title = "自动刷新",
                subtitle = "整屏刷新用于清除残影",
                notes =
                    listOf(
                        "自动整屏刷新用于清除墨水屏残影；间隔较短会增加闪烁，建议按环境光照选择。" +
                            "从其他应用返回时按设定延迟执行整刷，用于等待画面合成完成。",
                    ),
            ),
        )

    val specs: List<SettingSpec> =
        listOf(
            SettingSpec(
                id = "tray.overlayEnabled",
                group = GROUP_TRAY_STATUS,
                title = "悬浮状态栏",
                hint = "在其他应用上方显示现有时间、电量与刷新入口",
                control =
                    toggleTray(
                        read = { it.overlayEnabled },
                        write = { tray, value -> tray.copy(overlayEnabled = value) },
                    ),
            ),
            SettingSpec(
                id = "tray.showBatteryPercent",
                group = GROUP_TRAY_STATUS,
                title = "显示电量百分比",
                hint = "电量图标旁显示数字",
                control =
                    toggleTray(
                        read = { it.showBatteryPercent },
                        write = { tray, value -> tray.copy(showBatteryPercent = value) },
                    ),
            ),
            SettingSpec(
                id = "tray.refreshInterval",
                group = GROUP_TRAY_REFRESH,
                title = "刷新间隔",
                hint = "0 表示关闭自动刷新",
                control =
                    choiceTray(
                        options = REFRESH_OPTIONS,
                        read = { tray -> REFRESH_VALUES.indexOf(tray.autoRefreshMinutes).takeIf { it >= 0 } ?: 0 },
                        write = { tray, index -> tray.copy(autoRefreshMinutes = REFRESH_VALUES[index]) },
                    ),
                effect = SettingEffect.REBUILD,
                afterWrite = { config -> RefreshSchedulerBridge.updateInterval(config.tray.autoRefreshMinutes) },
            ),
            SettingSpec(
                id = "tray.refreshOnExit",
                group = GROUP_TRAY_REFRESH,
                title = "从其他应用返回后刷新",
                hint = "从其他应用回到桌面时整屏刷新一次，用于清除切换过程中的残影",
                control =
                    toggleTray(
                        read = { it.refreshOnAppExit },
                        write = { tray, value -> tray.copy(refreshOnAppExit = value) },
                    ),
            ),
            SettingSpec(
                id = "tray.refreshDelay",
                group = GROUP_TRAY_REFRESH,
                title = "刷新延迟",
                hint = "返回桌面后等待该时长再闪烁，延迟过短会闪在上一帧画面上",
                control =
                    sliderTray(
                        values = EXIT_DELAY_VALUES,
                        read = { tray ->
                            EXIT_DELAY_VALUES.minByOrNull { kotlin.math.abs(it - tray.refreshOnAppExitDelayMs) }
                                ?: EXIT_DELAY_VALUES.first()
                        },
                        write = { tray, value -> tray.copy(refreshOnAppExitDelayMs = value.toInt()) },
                        format = { "${it.toInt()} 毫秒" },
                    ),
            ),
            SettingSpec(
                id = "tray.refreshSummary",
                group = GROUP_TRAY_REFRESH,
                title = "距上次整刷",
                control = SettingControl.Custom(key = "tray.refreshSummary"),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "tray.refreshNow",
                group = GROUP_TRAY_REFRESH,
                title = "立即整屏刷新",
                hint = "立即执行一次两次闪烁的整屏刷新，并重置自动刷新计时",
                control = SettingControl.Action(value = { "手动" }),
                effect = SettingEffect.NONE,
            ),
        )
}
