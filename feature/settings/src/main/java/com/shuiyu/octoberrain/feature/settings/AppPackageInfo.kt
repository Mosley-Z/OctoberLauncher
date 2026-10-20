package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build

/**
 * 读取本应用包信息的辅助方法，供关于页与更新页共用。
 */
internal object AppPackageInfo {
    /** 读取版本名，读取失败时返回空字符串。 */
    fun versionName(context: Context): String = info(context)?.versionName ?: ""

    /** 读取版本号，兼容新版本的长整型字段。 */
    fun versionCode(context: Context): Long {
        val packageInfo = info(context) ?: return 0L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    /** 判断当前是否为可调试构建。 */
    fun isDebuggable(context: Context): Boolean {
        val flags = context.applicationInfo.flags
        return flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    /** 读取包信息，失败时返回 null。 */
    private fun info(context: Context): PackageInfo? =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
}
