package com.shuiyu.octoberrain.service.wallpaper

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.delay

/**
 * 壁纸会话的界面导航。
 *
 * 负责两类收尾动作：在多个目标之间退回厂商主界面，以及会话结束后回到十雨桌面。
 * 返回动作按当前前台包名判断，只对厂商应用发返回键，避免影响其他界面。
 */
internal object WallpaperNavigator {
    /** 从结果页退回厂商主界面，供同一次请求的后续目标继续点击。 */
    suspend fun backToVendorMain(accessibility: AccessibilityService) {
        repeat(BACK_LIMIT) {
            val root = runCatching { accessibility.rootInActiveWindow }.getOrNull() ?: return@repeat
            var atMain = false
            try {
                val packageName = root.packageName?.toString().orEmpty()
                if (packageName != VENDOR_PACKAGE) return@repeat
                val texts = WallpaperNodes.collectTexts(root)
                atMain = texts.any { it == MAIN_STANDBY || it == MAIN_SHUTDOWN }
            } finally {
                WallpaperNodes.recycle(root)
            }
            if (atMain) return@repeat
            runCatching { accessibility.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
            delay(BACK_GAP_MS)
        }
    }

    /** 按包名退出厂商应用并回到十雨桌面；无障碍断开时只做回桌面。 */
    suspend fun returnHome(
        context: Context,
        accessibility: AccessibilityService?,
    ) {
        if (accessibility != null) {
            repeat(BACK_LIMIT) {
                val root = runCatching { accessibility.rootInActiveWindow }.getOrNull() ?: return@repeat
                val packageName = root.packageName?.toString().orEmpty()
                WallpaperNodes.recycle(root)
                if (packageName != VENDOR_PACKAGE) return@repeat
                runCatching { accessibility.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
                delay(BACK_GAP_MS)
            }
        }
        val foreground = accessibility?.let { runCatching { it.rootInActiveWindow }.getOrNull() }
        val packageName = foreground?.packageName?.toString().orEmpty()
        if (foreground != null) WallpaperNodes.recycle(foreground)
        if (packageName != context.packageName) {
            val intent =
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setClassName(context.packageName, MAIN_ACTIVITY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            runCatching { context.startActivity(intent) }
        }
    }

    /** 厂商包名。 */
    private const val VENDOR_PACKAGE = "com.wetao.wallpaper"

    /** 十雨桌面的主活动。 */
    private const val MAIN_ACTIVITY = "com.shuiyu.octoberrain.MainActivity"

    /** 厂商界面休眠入口文案。 */
    private const val MAIN_STANDBY = "锁屏壁纸"

    /** 厂商界面关机入口文案。 */
    private const val MAIN_SHUTDOWN = "关机壁纸"

    /** 连续返回的最大层数。 */
    private const val BACK_LIMIT = 6

    /** 每次返回之间的间隔。 */
    private const val BACK_GAP_MS = 200L
}
