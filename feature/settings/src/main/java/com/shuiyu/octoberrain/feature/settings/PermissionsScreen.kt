package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.device.PermissionCenter

/**
 * 权限与授权页面。
 *
 * 展示三项权限的当前状态，点击条目跳转系统设置；
 * 电池优化单独列出一行，便于关闭后台限制。
 */
class PermissionsScreen : SettingsBaseScreen("权限与授权") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val permissions = AppServices.permissions
        val snapshot = runCatching { permissions.snapshot() }.getOrDefault(emptyMap())
        val granted = PERMISSION_ITEMS.count { snapshot[it.key] == true }

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
            hint = "关闭电池优化可避免刷新与悬浮状态栏被系统冻结",
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
    }

    /** 单项权限描述。 */
    private class PermissionItem(
        val key: String,
        val label: String,
        val hint: String,
        val open: (com.shuiyu.octoberrain.core.common.Permissions) -> Unit,
    )

    private companion object {
        /** 三项权限条目，顺序与快照一致。 */
        val PERMISSION_ITEMS =
            listOf(
                PermissionItem(
                    key = PermissionCenter.KEY_NOTIFICATION_LISTENER,
                    label = "通知读取权限",
                    hint = "桌面通知角标需要通知监听服务",
                ) { it.openNotificationListenerSettings() },
                PermissionItem(
                    key = PermissionCenter.KEY_ACCESSIBILITY,
                    label = "无障碍服务",
                    hint = "悬浮状态栏与音量键翻转需要此服务",
                ) { it.openAccessibilitySettings() },
                PermissionItem(
                    key = PermissionCenter.KEY_STORAGE,
                    label = "存储访问",
                    hint = "导入壁纸、字体与电子书需要访问文件",
                ) { it.openStorageSettings() },
            )
    }
}
