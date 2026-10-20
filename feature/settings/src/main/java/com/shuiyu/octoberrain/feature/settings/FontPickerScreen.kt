package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.FontInfo
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkPage
import kotlinx.coroutines.launch
import java.io.File

/**
 * 字体管理页面。
 *
 * 上半部分选择桌面应用名称字体，下半部分选择钟面字体；
 * 支持导入 TTF 或 OTF 字体文件，导入后保存在应用私有目录。
 */
class FontPickerScreen : SettingsBaseScreen("字体") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val fonts = runCatching { AppServices.fonts.fonts() }.getOrDefault(FontInfo.BUILTIN)
        val imported = fonts.filter { it.path.isNotBlank() }
        val labelPath = config.appearance.labelFontPath

        val labelSection =
            SettingsUi.section(
                page,
                "应用名称字体",
                "字体文件导入后可选，作用于桌面图标名称",
            )
        labelSection.action(
            label = if (labelPath == null) "● 系统默认" else "系统默认",
            value = if (labelPath == null) "当前" else null,
            hint = "不加载外部字体，使用系统默认字型",
        ) {
            if (labelPath != null) applyLabelFont(null)
        }
        imported.forEach { font ->
            val isCurrent = labelPath == font.path
            labelSection.action(
                label = if (isCurrent) "● ${font.label}" else font.label,
                value = if (isCurrent) "当前" else null,
                hint = File(font.path).name,
            ) {
                if (!isCurrent) applyLabelFont(font.path)
            }
        }
        labelSection.button("导入字体文件") { importFont() }

        val clockSection =
            SettingsUi.section(
                page,
                "钟面字体",
                "影响大时钟组件与锁屏时间显示",
            )
        fonts.forEach { font ->
            val isCurrent = config.appearance.clockFont == font.id
            clockSection.action(
                label = if (isCurrent) "● ${font.label}" else font.label,
                value = if (isCurrent) "当前" else null,
                hint = if (font.path.isBlank()) "内置字体" else File(font.path).name,
            ) {
                if (!isCurrent) {
                    SettingsUi.edit { it.copy(appearance = it.appearance.copy(clockFont = font.id)) }
                    SettingsUi.applyAppearance(SettingsUi.settings().appearance)
                    rebuildLater()
                }
            }
        }

        SettingsUi.note(
            page,
            "字体文件只保存在应用私有目录，不会写入系统字体。导入失败时请确认文件为有效的 TTF 或 OTF 格式。",
        )
    }

    /** 写入应用名称字体路径，传 null 表示恢复系统默认。 */
    private fun applyLabelFont(path: String?) {
        SettingsUi.edit { it.copy(appearance = it.appearance.copy(labelFontPath = path)) }
        SettingsUi.applyAppearance(SettingsUi.settings().appearance)
        rebuildLater()
    }

    /** 选择字体文件并导入；优先使用系统文件选择器。 */
    private fun importFont() {
        val context = contextOrNull() ?: return
        SettingsUi.pickFile(context, "导入字体", FONT_MIME_TYPES, FONT_EXTENSIONS) { uri ->
            scope.launch {
                val info =
                    runCatching {
                        AppServices.fonts.importFont(uri)
                    }.getOrNull()
                if (info == null) {
                    SettingsUi.toast(context, "字体导入失败，请确认文件格式")
                } else {
                    SettingsUi.toast(context, "字体已导入：${info.label}")
                    SettingsUi.applyAppearance(SettingsUi.settings().appearance)
                    rebuildLater()
                }
            }
        }
    }

    private companion object {
        /** 字体文件扩展名，用于扫描回退。 */
        val FONT_EXTENSIONS: Set<String> = setOf("ttf", "otf")

        /** 字体选择的 mime 类型，覆盖部分设备上报的通用二进制类型。 */
        val FONT_MIME_TYPES: Array<String> =
            arrayOf(
                "font/ttf",
                "font/otf",
                "application/x-font-ttf",
                "application/octet-stream",
            )
    }
}
