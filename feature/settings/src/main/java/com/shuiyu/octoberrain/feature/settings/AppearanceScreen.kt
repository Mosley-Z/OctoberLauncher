package com.shuiyu.octoberrain.feature.settings

import android.net.Uri
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.FontInfo
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import kotlinx.coroutines.launch
import java.io.File

/**
 * 外观与主题页面。
 *
 * 行控件全部由 [SettingsCatalog.appearanceGroups] 的声明生成，
 * 本页只提供文件选择、主题与字体入口等动作，以及壁纸清除的确认流程。
 */
class AppearanceScreen : SettingsBaseScreen("外观与主题") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        SettingsSpecRenderer.renderPage(
            page = page,
            groups = SettingsCatalog.appearanceGroups,
            config = config,
            sink = settingEffectSink(),
            actions = appearanceActions(),
        )
    }

    /** 外观页的动作表，键为设置项标识。 */
    private fun appearanceActions(): Map<String, () -> Unit> =
        mapOf(
            "appearance.wallpaper.pick" to { pickWallpaper() },
            "appearance.wallpaper.clear" to { clearWallpaper() },
            "appearance.lockWallpaper.pick" to { pickLockWallpaper() },
            "appearance.lockWallpaper.clear" to { clearLockWallpaper() },
            "appearance.theme.open" to { navigate(Destination.ThemePicker) },
            "appearance.iconPack.open" to { navigate(Destination.IconPackPicker) },
            "appearance.font.label" to { navigate(Destination.FontPicker) },
            "appearance.font.clock" to { pickClockFont() },
            "appearance.image.directory.pick" to {
                contextOrNull()?.let { context -> changeImageDirectory(context) { rebuildLater() } }
            },
            "appearance.image.cache.clear" to {
                contextOrNull()?.let { context -> clearThumbCache(context) { rebuildLater() } }
            },
        )

    /** 跳转到设置模块内的子页；导航失败时提示。 */
    private fun navigate(destination: Destination) {
        runCatching { AppServices.router.navigate(destination) }
            .onFailure {
                val context = contextOrNull() ?: return@onFailure
                SettingsUi.toast(context, "无法打开页面")
            }
    }

    /** 更换壁纸：调用系统文件选择器，选中后写入外观配置。 */
    private fun pickWallpaper() {
        val context = contextOrNull() ?: return
        SettingsUi.pickFile(context, "选择壁纸", IMAGE_MIME_TYPES, IMAGE_EXTENSIONS) { uri ->
            applyWallpaper(uri)
        }
    }

    /** 异步应用壁纸，完成后清理旧缓存、重绘并刷新摘要。 */
    private fun applyWallpaper(uri: Uri) {
        val context = contextOrNull() ?: return
        val previous = SettingsUi.settings().appearance.wallpaperPath
        SettingsUi.toast(context, "正在设置壁纸…")
        scope.launch {
            val applied =
                runCatching {
                    AppServices.appearance.setWallpaper(uri)
                }.getOrDefault(false)
            if (applied) {
                previous?.takeIf { it.isNotBlank() }?.let { old ->
                    ThumbCacheRegistry.invalidate(context, File(old))
                }
                RefreshScheduler.notifyContentChanged()
                SettingsUi.toast(context, "壁纸已更新")
                AppServices.refresh.requestFullRefresh()
                rebuildLater()
            } else {
                SettingsUi.toast(context, "壁纸读取失败，请换一张图片")
            }
        }
    }

    /** 选择锁屏壁纸：调用系统文件选择器，选中后写入锁屏壁纸配置。 */
    private fun pickLockWallpaper() {
        val context = contextOrNull() ?: return
        SettingsUi.pickFile(context, "选择锁屏壁纸", IMAGE_MIME_TYPES, IMAGE_EXTENSIONS) { uri ->
            applyLockWallpaper(uri)
        }
    }

    /** 异步应用锁屏壁纸，完成后刷新摘要并请求整屏重绘。 */
    private fun applyLockWallpaper(uri: Uri) {
        val context = contextOrNull() ?: return
        SettingsUi.toast(context, "正在设置锁屏壁纸…")
        scope.launch {
            val applied =
                runCatching {
                    AppServices.appearance.setLockWallpaper(uri)
                }.getOrDefault(false)
            if (applied) {
                SettingsUi.toast(context, "锁屏壁纸已更新")
                AppServices.refresh.requestFullRefresh()
                rebuildLater()
            } else {
                SettingsUi.toast(context, "锁屏壁纸读取失败，请换一张图片")
            }
        }
    }

    /** 清除锁屏壁纸，锁屏界面恢复纸色背景；未设置时直接提示。 */
    private fun clearLockWallpaper() {
        val context = contextOrNull() ?: return
        val appearance = SettingsUi.settings().appearance
        if (appearance.lockWallpaperPath.isBlank()) {
            SettingsUi.toast(context, "当前没有设置锁屏壁纸")
            return
        }
        InkDialogs.confirm(
            context,
            "清除锁屏壁纸",
            "锁屏密码界面将恢复为纸色背景。",
            confirmLabel = "清除",
            onConfirm = {
                runCatching { AppServices.appearance.clearLockWallpaper() }
                AppServices.refresh.requestFullRefresh()
                rebuildLater()
            },
        )
    }

    /** 清除壁纸并恢复纯白纸面。 */
    private fun clearWallpaper() {
        val context = contextOrNull() ?: return
        val config = SettingsUi.settings()
        if (config.appearance.wallpaperPath.isNullOrBlank()) {
            SettingsUi.toast(context, "当前没有设置壁纸")
            return
        }
        InkDialogs.confirm(
            context,
            "清除壁纸",
            "桌面将恢复为纯白纸面。",
            confirmLabel = "清除",
            onConfirm = {
                runCatching { AppServices.appearance.clearWallpaper() }
                AppServices.refresh.requestFullRefresh()
                rebuildLater()
            },
        )
    }

    /** 选择钟面字体：内置字体与已导入字体统一列出。 */
    private fun pickClockFont() {
        val context = contextOrNull() ?: return
        val faces = runCatching { AppServices.fonts.fonts() }.getOrDefault(FontInfo.BUILTIN)
        InkDialogs.choose(context, "钟面字体", faces.map { it.label }) { index ->
            val id = faces[index].id
            SettingsUi.edit { it.copy(appearance = it.appearance.copy(clockFont = id)) }
            SettingsUi.applyAppearance(SettingsUi.settings().appearance)
            rebuildLater()
        }
    }

    private companion object {
        /** 壁纸候选扩展名，用于系统选择器不可用时的扫描回退。 */
        val IMAGE_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp", "bmp")

        /** 壁纸选择的 mime 类型。 */
        val IMAGE_MIME_TYPES: Array<String> = arrayOf("image/*")
    }
}
