package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.device.PermissionCenter

/**
 * 权限与授权页面。
 *
 * 展示七项权限的当前状态，点击条目跳转系统设置；
 * 电池优化单独列出一行，便于关闭后台限制。
 */
class PermissionsScreen : SettingsBaseScreen("权限与授权") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val permissions = AppServices.permissions
        val snapshot = runCatching { permissions.snapshot() }.getOrDefault(emptyMap())
        val granted = snapshot.count { it.value }

        val section =
            SettingsUi.section(
                page,
                "权限状态",
                "已授权 $granted / ${PERMISSION_ITEMS.size} 项",
            )

        PERMISSION_ITEMS.forEach { item ->
            val allowed = snapshot[item.key] == true
            section.action(
                label = item.label,
                value = if (allowed) "已授权" else "未授权",
                hint = item.hint,
            ) {
                runCatching { item.open(permissions) }
            }
        }

        val system = SettingsUi.section(page, "系统", "后台与电池")
        system.action(
            label = "电池优化",
            value = "系统设置",
            hint = "关闭电池优化可避免刷新与托盘被系统冻结",
        ) {
            runCatching { permissions.openBatterySettings() }
        }
        system.action(
            label = "应用设置",
            value = "系统设置",
            hint = "通知、权限与默认启动器设置入口",
        ) {
            runCatching { permissions.openAppSettings() }
        }

        SettingsUi.note(
            page,
            "授权状态在页面恢复时刷新；从系统设置返回后再次进入本页可看到最新结果。所有权限均可在系统设置中收回。",
        )
    }

    /** 单项权限描述。 */
    private class PermissionItem(
        val key: String,
        val label: String,
        val hint: String,
        val open: (com.shuiyu.octoberrain.core.common.Permissions) -> Unit,
    )

    private companion object {
        /** 七项权限条目，顺序与快照一致。 */
        val PERMISSION_ITEMS =
            listOf(
                PermissionItem(
                    key = PermissionCenter.KEY_NOTIFICATION_LISTENER,
                    label = "通知读取权限",
                    hint = "托盘显示通知内容需要监听通知的服务",
                ) { it.openNotificationListenerSettings() },
                PermissionItem(
                    key = PermissionCenter.KEY_ACCESSIBILITY,
                    label = "无障碍服务",
                    hint = "返回键与托盘手势在部分设备上依赖此服务",
                ) { it.openAccessibilitySettings() },
                PermissionItem(
                    key = PermissionCenter.KEY_OVERLAY,
                    label = "悬浮窗权限",
                    hint = "悬浮球与锁屏覆盖层需要此权限",
                ) { it.openOverlaySettings() },
                PermissionItem(
                    key = PermissionCenter.KEY_NOTIFICATION_PERMISSION,
                    label = "应用通知权限",
                    hint = "从 Android 13 起需单独授予通知显示权限",
                ) { it.openAppNotificationSettings() },
                PermissionItem(
                    key = PermissionCenter.KEY_EXACT_ALARM,
                    label = "精确闹钟",
                    hint = "定时刷新与提醒需要精确闹钟权限",
                ) { it.openAppSettings() },
                PermissionItem(
                    key = PermissionCenter.KEY_USAGE_ACCESS,
                    label = "使用情况访问",
                    hint = "使用统计与最近使用记录需要此权限",
                ) { it.openUsageSettings() },
                PermissionItem(
                    key = PermissionCenter.KEY_STORAGE,
                    label = "存储访问",
                    hint = "导入壁纸、字体与电子书需要访问文件",
                ) { it.openStorageSettings() },
            )
    }
}
