package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkPage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主题选择页面。
 *
 * 列出内置主题与主题目录中追加的主题，点击后写入外观配置并立即重绘；
 * 同时提供主题文件的导入与导出，文件选择走宿主 FilePicker，
 * 宿主未装配选择器时回退到模块内扫描列表。
 */
class ThemePickerScreen : SettingsBaseScreen("主题") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val themes =
            runCatching { AppServices.appearance.themes() }
                .getOrDefault(emptyList())
        val currentId = config.appearance.themeId

        val list = SettingsUi.section(page, "主题列表", "点击条目立即应用，当前主题显示为「当前」")
        themes.forEach { theme ->
            val isCurrent = theme.id == currentId
            list.action(
                label = if (isCurrent) "● ${theme.label}" else theme.label,
                value = if (isCurrent) "当前" else null,
                hint = theme.description,
            ) {
                if (!isCurrent) {
                    runCatching { AppServices.appearance.applyTheme(theme.id) }
                    SettingsUi.applyAppearance(SettingsUi.settings().appearance)
                    rebuildLater()
                }
            }
        }

        // 主题文件导入导出：导入后主题进入列表，导出生成 .ttp 文件供备份或分享。
        val files = SettingsUi.section(page, "主题文件", "主题文件扩展名为 .ttp，内容为 JSON")
        files.button("导入主题文件") { importTheme() }
        files.button("导出当前主题") { exportTheme() }

        SettingsUi.note(
            page,
            "主题只承载壁纸、白色压层与图标映射，不改变设置页样式；导入或删除主题文件后重新进入本页即可刷新列表。",
        )
    }

    /** 选择主题文件并导入，成功后立即应用新主题。 */
    private fun importTheme() {
        val context = contextOrNull() ?: return
        SettingsUi.pickFile(context, "导入主题", THEME_MIME_TYPES, THEME_EXTENSIONS) { uri ->
            scope.launch {
                val imported =
                    runCatching {
                        AppServices.appearance.importTheme(uri)
                    }.getOrDefault(false)
                if (imported) {
                    SettingsUi.toast(context, "主题已导入")
                    SettingsUi.applyAppearance(SettingsUi.settings().appearance)
                    rebuildLater()
                } else {
                    SettingsUi.toast(context, "主题导入失败，请确认文件内容")
                }
            }
        }
    }

    /** 选择保存位置并导出当前外观为主题文件。 */
    private fun exportTheme() {
        val context = contextOrNull() ?: return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "theme-$stamp.ttp"
        SettingsUi.saveFile(context, fileName, "application/json") { uri ->
            scope.launch {
                val exported =
                    runCatching {
                        AppServices.appearance.exportTheme(uri)
                    }.getOrDefault(false)
                if (exported) {
                    SettingsUi.toast(context, "主题已导出：$fileName")
                } else {
                    SettingsUi.toast(context, "主题导出失败，请更换保存位置")
                }
            }
        }
    }

    private companion object {
        /** 主题选择的 mime 类型，覆盖部分设备上报的通用类型。 */
        val THEME_MIME_TYPES: Array<String> =
            arrayOf(
                "application/json",
                "application/octet-stream",
                "text/plain",
            )

        /** 主题文件扩展名，用于扫描回退。 */
        val THEME_EXTENSIONS: Set<String> = setOf("ttp", "json")
    }
}
