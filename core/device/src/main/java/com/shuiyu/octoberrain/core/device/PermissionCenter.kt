package com.shuiyu.octoberrain.core.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.shuiyu.octoberrain.core.common.Permissions

/**
 * 权限中心：采集运行状态并提供跳转动作。
 *
 * 设置页与系统控件只读取本对象，权限请求由设置页统一发起。
 *
 * 参数 context 为应用上下文；properties 为键值对形式的能力快照。
 */
class PermissionCenter(
    private val context: Context,
) : Permissions {
    /**
     * 采集当前权限状态。
     *
     * 返回 map 中包含通知监听、无障碍、悬浮窗、通知运行时权限、电池白名单与精确闹钟。
     */
    override fun snapshot(): Map<String, Boolean> {
        val result = linkedMapOf<String, Boolean>()
        result[KEY_NOTIFICATION_LISTENER] = hasNotificationListener()
        result[KEY_ACCESSIBILITY] = hasAccessibility()
        result[KEY_OVERLAY] = hasOverlay()
        result[KEY_NOTIFICATION_PERMISSION] = hasNotificationPermission()
        result[KEY_EXACT_ALARM] = hasExactAlarm()
        result[KEY_USAGE_ACCESS] = hasUsageAccess()
        result[KEY_STORAGE] = hasAllFilesAccess()
        return result
    }

    /** 是否具备使用情况访问权限，判定逻辑见 [UsageAccessChecker]。 */
    fun hasUsageAccess(): Boolean = UsageAccessChecker.isGranted(context)

    /** 是否具备所有文件访问权限。 */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** 是否已开启通知监听服务。 */
    fun hasNotificationListener(): Boolean {
        val flat =
            Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
        return flat.contains(context.packageName)
    }

    /** 是否已开启本应用的无障碍服务。 */
    fun hasAccessibility(): Boolean {
        val enabled =
            try {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                )
            } catch (e: Exception) {
                0
            }
        if (enabled != 1) return false
        val services =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
        return services.split(':').any { it.startsWith(context.packageName) }
    }

    /** 是否具备悬浮窗权限。 */
    fun hasOverlay(): Boolean = Settings.canDrawOverlays(context)

    /** 是否具备通知运行时权限，Android 12 以下视为已授予。 */
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** 是否具备精确闹钟权限。 */
    fun hasExactAlarm(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val alarm = context.getSystemService(android.app.AlarmManager::class.java)
                alarm?.canScheduleExactAlarms() ?: false
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }

    /** 打开通知监听设置。 */
    override fun openNotificationListenerSettings() {
        start(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    /** 打开无障碍设置。 */
    override fun openAccessibilitySettings() {
        start(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    /** 打开悬浮窗设置。 */
    override fun openOverlaySettings() {
        start(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    }

    /** 打开应用通知设置。 */
    override fun openAppNotificationSettings() {
        start(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    /** 打开电池优化白名单设置。 */
    override fun openBatterySettings() {
        start(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    /** 打开使用情况访问设置。 */
    override fun openUsageSettings() {
        start(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    /** 打开存储管理设置。 */
    override fun openStorageSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            start(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        } else {
            start(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
    }

    /** 打开本应用设置页。 */
    override fun openAppSettings() {
        start(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }

    /** 申请通知运行时权限。 */
    fun requestNotificationPermission(
        activity: android.app.Activity,
        requestCode: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode)
        }
    }

    /**
     * 启动系统设置页。
     *
     * 参数 action 为设置动作；data 为可选 Uri；启动失败时回退到应用设置页。
     */
    private fun start(
        action: String,
        data: Uri? = null,
    ) {
        val intent =
            Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (data != null) this.data = data
            }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (ignored: Exception) {
                // 设备未提供系统设置入口时保持静默，由设置页提示
            }
        }
    }

    companion object {
        const val KEY_NOTIFICATION_LISTENER = "notification_listener"
        const val KEY_ACCESSIBILITY = "accessibility"
        const val KEY_OVERLAY = "overlay"
        const val KEY_NOTIFICATION_PERMISSION = "notification_permission"
        const val KEY_EXACT_ALARM = "exact_alarm"
        const val KEY_USAGE_ACCESS = "usage_access"
        const val KEY_STORAGE = "storage"
    }
}
