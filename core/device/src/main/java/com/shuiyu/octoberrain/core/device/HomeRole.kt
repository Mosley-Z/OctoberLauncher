package com.shuiyu.octoberrain.core.device

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * 默认桌面角色管理。
 *
 * Android 10 起通过 RoleManager 申请 HOME 角色，低版本依赖系统的默认桌面设置页。
 * 启动器只读取状态并引导用户设置，不主动更换角色。
 */
object HomeRole {
    /** 判断当前应用是否已是默认桌面。 */
    fun isDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(RoleManager::class.java)
            if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return manager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        // 低版本通过解析 HOME Intent 判断
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, 0)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** 申请 HOME 角色的 Intent；系统不支持角色时返回 null。 */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(RoleManager::class.java)
            if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return null
    }

    /**
     * 发起默认桌面设置。
     *
     * 优先使用角色请求弹窗，其次打开系统主屏幕设置页。
     */
    fun request(
        activity: Activity,
        requestCode: Int,
    ) {
        val intent = requestIntent(activity)
        if (intent != null) {
            runCatching { activity.startActivityForResult(intent, requestCode) }
                .onFailure { openHomeSettings(activity) }
            return
        }
        openHomeSettings(activity)
    }

    /** 打开系统的默认主屏幕设置页。 */
    fun openHomeSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_HOME_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
