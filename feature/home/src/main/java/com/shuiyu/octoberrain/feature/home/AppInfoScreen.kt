package com.shuiyu.octoberrain.feature.home

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 单个应用的信息页。
 *
 * 供桌面的长按菜单与组件跳转使用；展示包名、版本、安装时间与占用大小，
 * 并提供打开与卸载入口。
 *
 * 参数 packageName 为目标应用包名。
 */
internal class AppInfoScreen(
    private val packageName: String,
) : Screen {
    /** 组合信息页内容。 */
    override fun createView(context: Context): View {
        val page =
            InkPage(context, "应用信息", showBack = true) {
                runCatching { AppServices.router.back() }
            }
        val packageManager = context.packageManager
        val appInfo = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
        val pkgInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val label =
            appInfo?.let {
                runCatching { packageManager.getApplicationLabel(it).toString() }.getOrDefault(packageName)
            } ?: packageName
        val isSystem = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        page.content.addView(InkWidgets.title(context, label))
        page.content.addView(InkWidgets.spacer(context, InkWidgets.dp(context, 8f)))
        page.content.addView(InkWidgets.labelRow(context, "包名", packageName))
        page.content.addView(InkWidgets.divider(context))
        page.content.addView(InkWidgets.labelRow(context, "版本", versionText(pkgInfo)))
        page.content.addView(InkWidgets.divider(context))
        page.content.addView(InkWidgets.labelRow(context, "安装时间", installTimeText(appInfo)))
        page.content.addView(InkWidgets.divider(context))
        page.content.addView(InkWidgets.labelRow(context, "占用大小", sizeText(appInfo)))
        page.content.addView(InkWidgets.divider(context))
        page.content.addView(InkWidgets.labelRow(context, "应用类型", if (isSystem) "系统应用" else "用户应用"))
        page.content.addView(InkWidgets.spacer(context, InkWidgets.dp(context, 12f)))

        page.content.addView(
            InkWidgets.row(context, "打开应用") {
                runCatching { AppServices.router.openApp(packageName) }
            },
        )
        page.content.addView(InkWidgets.divider(context))
        page.content.addView(
            InkWidgets.row(context, "返回桌面") {
                runCatching { AppServices.router.back() }
            },
        )
        if (!isSystem) {
            page.content.addView(InkWidgets.divider(context))
            page.content.addView(
                InkWidgets.row(context, "卸载应用") {
                    requestUninstall(context)
                },
            )
        }
        return page
    }

    /** 版本名与版本号组合文本。 */
    private fun versionText(info: android.content.pm.PackageInfo?): String {
        if (info == null) return "未知"
        val code =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        val name = info.versionName ?: "未知"
        return "$name ($code)"
    }

    /** 首次安装时间文本。 */
    private fun installTimeText(info: ApplicationInfo?): String {
        val installed =
            runCatching {
                AppServices.catalog.apps.value
                    .firstOrNull { it.packageName == packageName }
                    ?.firstInstallTime
            }.getOrNull() ?: return "未知"
        if (installed <= 0L) return "未知"
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return format.format(Date(installed))
    }

    /** 安装包占用大小文本，单位兆字节。 */
    private fun sizeText(info: ApplicationInfo?): String {
        val path = info?.sourceDir ?: return "未知"
        val bytes = java.io.File(path).length()
        if (bytes <= 0L) return "未知"
        return String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
    }

    /** 打开系统卸载确认界面。 */
    private fun requestUninstall(context: Context) {
        AppUninstall.request(context, packageName)
    }
}
