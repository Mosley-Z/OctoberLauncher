package com.shuiyu.octoberrain.feature.settings

import android.net.Uri
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
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
class AppearanceScreen : SettingsBaseScreen("外观与个性化") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        SettingsSpecRenderer.renderPage(
            page = page,
            groups = SettingsCatalog.appearanceGroups.filterNot { it.id.startsWith("project.") },
            config = config,
            sink = settingEffectSink(),
            actions = appearanceActions(),
        )
        val icons = SettingsUi.section(page, "图标与应用名称", "尺寸为自动时按布局空间适配")
        val sizes = listOf(0, 40, 48, 56, 64, 72, 80)
        icons.stepper(
            "图标尺寸",
            sizes.map { if (it == 0) "自动" else "$it dp" },
            { sizes.indexOf(SettingsUi.settings().desktop.iconSizeDp).coerceAtLeast(0) },
        ) { index ->
            SettingsUi.edit { it.copy(desktop = it.desktop.copy(iconSizeDp = sizes[index])) }
            SettingsUi.refresh()
        }
        val labels = listOf(0f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
        icons.stepper(
            "应用名字号",
            labels.map { if (it == 0f) "跟随主题" else "${it.toInt()} sp" },
            { labels.indexOf(SettingsUi.settings().desktop.labelTextSp).coerceAtLeast(0) },
        ) { index ->
            SettingsUi.edit { it.copy(desktop = it.desktop.copy(labelTextSp = labels[index])) }
            SettingsUi.refresh()
        }
        icons.action("美化编辑器", "打开") { navigate(Destination.Beautify) }
    }

    /** 外观页的动作表，键为设置项标识。 */
    private fun appearanceActions(): Map<String, () -> Unit> =
        mapOf(
            "appearance.wallpaper.pick" to { pickWallpaper() },
            "appearance.wallpaper.clear" to { clearWallpaper() },
            "appearance.iconPack.open" to { navigate(Destination.IconPackPicker) },
            "appearance.font.label" to { navigate(Destination.FontPicker) },
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
        SettingsUi.pickFile(context, "选择壁纸", IMAGE_MIME_TYPES, IMAGE_EXTENSIONS, forImport = true) { uri ->
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

    private companion object {
        /** 壁纸候选扩展名，用于系统选择器不可用时的扫描回退。 */
        val IMAGE_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp", "bmp")

        /** 壁纸选择的 mime 类型。 */
        val IMAGE_MIME_TYPES: Array<String> = arrayOf("image/*")
    }
}
