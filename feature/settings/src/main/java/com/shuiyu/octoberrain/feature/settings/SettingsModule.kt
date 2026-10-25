package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherModule
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.ScreenProvider
import com.shuiyu.octoberrain.core.common.ScreenRegistry
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry

/**
 * 设置与外观模块入口。
 *
 * 由 app 通过反射实例化并调用 [install]，安装时向 ScreenRegistry 注册
 * 设置中心、桌面偏好、外观、桌面布局、锁屏、权限、关于、更新、美化模式与各类选择器页面。
 * 自有设置分类统一在本模块注册，托盘模块负责实际托盘显示与交互。
 */
class SettingsModule : LauncherModule {
    override fun install(context: Context) {
        ScreenRegistry.register(SettingsProvider())
        configureThumbnailCache()
    }

    /** 把配置中的缩略图质量与抖动开关注入渲染层，失败时保留渲染层默认值。 */
    private fun configureThumbnailCache() {
        val appearance = runCatching { AppServices.config.state.value.appearance }.getOrNull() ?: return
        ThumbCacheRegistry.configure(appearance.effectiveThumbnailQuality, appearance.thumbnailDither)
    }
}

/** 设置模块的页面提供者，按 Destination 分发页面实例。 */
private class SettingsProvider : ScreenProvider {
    override fun create(destination: Destination): Screen? =
        when (destination) {
            Destination.Settings -> SettingsHomeScreen()
            Destination.Beautify -> BeautifyScreen()
            Destination.BeautifyPreview -> PreviewScreen()
            Destination.SystemSettings -> SettingsSystemScreen()
            Destination.LauncherPrefs -> LauncherPrefsScreen()
            Destination.TrayPrefs -> TrayPrefsScreen()
            Destination.ProjectRefresh -> TrayPrefsScreen(refreshOnly = true)
            Destination.ProjectContent -> ProjectContentScreen()
            Destination.ProjectAbout -> AboutScreen()
            Destination.Appearance -> AppearanceScreen()
            Destination.DesktopPrefs -> DesktopPrefsScreen()
            Destination.NegativeSettings -> NegativeSettingsScreen()
            Destination.LockSettings -> LockSettingsScreen()
            Destination.Permissions -> PermissionsScreen()
            Destination.About -> AboutScreen()
            Destination.Update -> UpdateScreen()
            Destination.IconPackPicker -> IconPackPickerScreen()
            Destination.FontPicker -> FontPickerScreen()
            Destination.Backup -> BackupScreen()
            is Destination.IconPicker -> IconPickerScreen(destination.targetId)
            else -> null
        }
}
