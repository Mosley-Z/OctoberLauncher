package com.shuiyu.octoberrain.feature.settings

import android.app.Activity
import android.view.Gravity
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.Permissions
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.device.HomeRole
import com.shuiyu.octoberrain.core.device.PermissionCenter

/**
 * 权限与备份页面。
 *
 * 权限状态、系统设置入口与本地备份入口集中在本页：权限分组的标题右侧显示
 * 授权计数，页面末尾保留「备份与恢复」入口行。
 * 每次进入页面重新读取权限快照，从系统设置返回后计数随之更新。
 */
internal class ProjectContentScreen : SettingsBaseScreen("权限与备份") {
    override fun needsRebuild(): Boolean = true

    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val permissions = AppServices.permissions
        val snapshot = runCatching { permissions.snapshot() }.getOrDefault(emptyMap())
        val granted = PERMISSION_ITEMS.count { snapshot[it.key] == true }

        val section = permissionSection(page, "已授权 $granted / ${PERMISSION_ITEMS.size} 项")
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
            label = "主屏幕应用",
            value = if (HomeRole.isDefault(page.context)) "已设为默认" else "未设为默认",
            hint = "将十雨桌面设为默认主屏幕应用",
        ) {
            val activity = page.context as? Activity
            if (activity != null) {
                HomeRole.request(activity, 16601)
            } else {
                HomeRole.openHomeSettings(page.context)
            }
        }
        system.action(
            label = "电池优化",
            value = "系统设置",
            hint = "关闭电池优化可避免刷新与透明状态栏被系统冻结",
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

        page.content.addView(InkWidgets.spacer(page.context, DesignRuntime.tokens.gapLarge))
        page.content.addView(
            InkWidgets.row(page.context, "备份与恢复", "本地备份") {
                AppServices.router.navigate(Destination.Backup)
            },
        )
    }

    /**
     * 建立权限分组：纯文字标题行加带描边的行容器。
     *
     * 参数 page 为承载分组的页面；count 为标题右侧的授权计数说明。
     * 标题行使用标题尺寸文字且不响应点击，说明文字与容器尺寸取自设计令牌；
     * 本页首块内容即为该分组，标题上方不再追加间距。
     */
    private fun permissionSection(
        page: InkPage,
        count: CharSequence,
    ): SettingsSection {
        val context = page.context
        val tokens = DesignRuntime.tokens
        page.content.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    InkWidgets.heading(context, "十雨所需权限与授权").apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f,
                            )
                    },
                )
                addView(InkWidgets.caption(context, count))
            },
        )
        val box =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background =
                    InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
                layoutParams =
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = tokens.gap }
            }
        page.content.addView(box)
        return SettingsSection(page, box)
    }

    /** 单项权限描述。 */
    private class PermissionItem(
        val key: String,
        val label: String,
        val hint: String,
        val open: (Permissions) -> Unit,
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
                    hint = "透明状态栏交互需要此服务",
                ) { it.openAccessibilitySettings() },
                PermissionItem(
                    key = PermissionCenter.KEY_STORAGE,
                    label = "存储访问",
                    hint = "浏览 DCIM、Download 等本地文件夹；单次选图无需此权限",
                ) { it.openStorageSettings() },
            )
    }
}
