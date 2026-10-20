package com.shuiyu.octoberrain.core.device

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * 使用情况访问权限判定。
 *
 * Android 10 起 AppOpsManager 提供 `unsafeCheckOpNoThrow`，Android 8 与 9 仅有已废弃的
 * `checkOpNoThrow`，两者语义相同。调用点统一经过本对象，版本分支集中在一处维护。
 */
object UsageAccessChecker {
    private const val OP = AppOpsManager.OPSTR_GET_USAGE_STATS

    /**
     * 判断应用是否具备使用情况访问权限。
     *
     * 参数 context 为任意上下文，内部取应用上下文；系统服务缺失或调用抛出异常时返回 false。
     */
    fun isGranted(context: Context): Boolean {
        val appContext = context.applicationContext
        val appOps = appContext.getSystemService(AppOpsManager::class.java) ?: return false
        return runCatching {
            val mode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(OP, Process.myUid(), appContext.packageName)
                } else {
                    legacyMode(appOps, appContext.packageName)
                }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    /** Android 8 与 9 的分支，调用已废弃的 checkOpNoThrow，结果语义与新方法一致。 */
    @Suppress("DEPRECATION")
    private fun legacyMode(
        appOps: AppOpsManager,
        packageName: String,
    ): Int = appOps.checkOpNoThrow(OP, Process.myUid(), packageName)
}
