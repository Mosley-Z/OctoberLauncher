package com.shuiyu.octoberrain.feature.tray

import android.content.Context
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.device.PermissionCenter

/**
 * 托盘设置页：开关托盘、调整通知条数与媒体入口、配置状态栏显示项与悬浮层。
 *
 * 每次点击后重建内容，保证配置值与权限状态实时刷新；页面内容可滚动，
 * 适配短屏设备。返回键由宿主路由统一处理。
 */
class TrayPrefsScreen : Screen {
    private lateinit var page: InkPage

    override fun createView(context: Context): View {
        page =
            InkPage(context, "快捷托盘") {
                runCatching { AppServices.router.back() }
            }
        build()
        return page
    }

    override fun onResume() {
        if (::page.isInitialized) build()
    }

    /** 依据当前配置与权限重建全部行。 */
    private fun build() {
        val config = AppServices.config.state.value
        val context = page.context
        val content = page.content
        content.removeAllViews()

        content.addView(InkWidgets.caption(context, "托盘与状态栏"))
        content.addView(
            toggleRow(context, "启用快捷托盘", config.tray.enabled) {
                edit { it.copy(tray = it.tray.copy(enabled = !config.tray.enabled)) }
            },
        )
        content.addView(
            valueRow(context, "通知条数", "${config.tray.notificationLimit} 条") {
                val next = nextNotificationLimit(config.tray.notificationLimit)
                edit { it.copy(tray = it.tray.copy(notificationLimit = next)) }
            },
        )
        content.addView(
            toggleRow(context, "媒体控制按钮", config.tray.mediaEnabled) {
                edit { it.copy(tray = it.tray.copy(mediaEnabled = !config.tray.mediaEnabled)) }
            },
        )
        content.addView(
            toggleRow(context, "悬浮状态栏", config.tray.overlayEnabled) {
                edit { it.copy(tray = it.tray.copy(overlayEnabled = !config.tray.overlayEnabled)) }
            },
        )
        content.addView(
            toggleRow(context, "托盘显示 WiFi 卡片", config.tray.showWifi) {
                edit { it.copy(tray = it.tray.copy(showWifi = !config.tray.showWifi)) }
            },
        )
        content.addView(
            toggleRow(context, "托盘显示蓝牙卡片", config.tray.showBluetooth) {
                edit { it.copy(tray = it.tray.copy(showBluetooth = !config.tray.showBluetooth)) }
            },
        )
        content.addView(
            toggleRow(context, "状态栏显示电量百分比", config.tray.showBatteryPercent) {
                edit { it.copy(tray = it.tray.copy(showBatteryPercent = !config.tray.showBatteryPercent)) }
            },
        )
        content.addView(
            valueRow(context, "自动整屏刷新", refreshLabel(config.tray.autoRefreshMinutes)) {
                val next = nextRefreshMinutes(config.tray.autoRefreshMinutes)
                edit { it.copy(tray = it.tray.copy(autoRefreshMinutes = next)) }
            },
        )
        content.addView(
            toggleRow(context, "音量键翻转", config.tray.volumeKeyFlip) {
                edit { it.copy(tray = it.tray.copy(volumeKeyFlip = !config.tray.volumeKeyFlip)) }
            },
        )

        content.addView(InkWidgets.spacer(context, InkWidgets.dp(context, 10f)))
        content.addView(InkWidgets.caption(context, "权限与维护"))
        val permissions = runCatching { AppServices.permissions.snapshot() }.getOrDefault(emptyMap())
        content.addView(
            valueRow(
                context,
                "通知读取权限",
                if (permissions[PermissionCenter.KEY_NOTIFICATION_LISTENER] == true) "已授权" else "未授权",
            ) {
                runCatching { AppServices.notifications.openAccessSettings() }
            },
        )
        content.addView(
            valueRow(
                context,
                "无障碍服务",
                if (permissions[PermissionCenter.KEY_ACCESSIBILITY] == true) "已开启" else "未开启",
            ) {
                runCatching { AppServices.permissions.openAccessibilitySettings() }
            },
        )
        content.addView(
            valueRow(context, "立即整屏刷新", "执行") {
                runCatching { AppServices.refresh.requestFullRefresh() }
            },
        )
        content.addView(
            valueRow(context, "清空全部通知", "执行") {
                runCatching { AppServices.notifications.dismissAll() }
            },
        )
        content.addView(
            InkWidgets.caption(context, "托盘展开时按返回键或下滑可收起；离开桌面后的悬浮状态栏需开启无障碍服务。"),
        )
    }

    /** 应用配置修改并重建界面。 */
    private fun edit(transform: (LauncherConfig) -> LauncherConfig) {
        runCatching { AppServices.config.edit(transform) }
        build()
    }

    /** 开关行。 */
    private fun toggleRow(
        context: Context,
        label: String,
        value: Boolean,
        onClick: () -> Unit,
    ): View = InkWidgets.row(context, label, if (value) "开" else "关", onClick)

    /** 带当前值的动作行。 */
    private fun valueRow(
        context: Context,
        label: String,
        value: String,
        onClick: () -> Unit,
    ): View = InkWidgets.row(context, label, value, onClick)

    /** 通知条数循环档位。 */
    private fun nextNotificationLimit(current: Int): Int =
        when (current) {
            2 -> 4
            4 -> 6
            6 -> 8
            else -> 2
        }

    /** 自动刷新间隔循环档位。 */
    private fun nextRefreshMinutes(current: Int): Int =
        when (current) {
            0 -> 10
            10 -> 30
            30 -> 60
            else -> 0
        }

    /** 自动刷新间隔文案。 */
    private fun refreshLabel(minutes: Int): String = if (minutes <= 0) "关闭" else "$minutes 分钟"
}
