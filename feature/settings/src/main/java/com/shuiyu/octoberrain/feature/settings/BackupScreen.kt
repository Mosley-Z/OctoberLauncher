package com.shuiyu.octoberrain.feature.settings

import android.net.Uri
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkPage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 配置备份与恢复页面。
 *
 * 提供两个动作：
 * - 导出备份：把完整配置、外观与主题令牌写入单个 zip；
 * - 从文件恢复：整体覆盖当前配置，恢复前二次确认；
 *
 * 文件选择经 [SettingsUi.pickFile] 与 [SettingsUi.saveFile]，优先使用系统文件界面，
 * 不可用时回退到内置扫描与应用私有导出目录。操作结果同时写入页内“上次操作”分组
 * 并以轻提示反馈。
 */
class BackupScreen : SettingsBaseScreen("配置备份与恢复") {
    /** 上次操作的结果文本，空串表示尚未执行任何操作。 */
    private var lastResult: String = ""

    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val backup =
            SettingsUi.section(
                page,
                "备份",
                "备份包为 zip，包含完整配置、外观与主题令牌、导出清单",
            )
        backup.action(
            "导出备份",
            value = "zip 格式",
            hint = "写入配置、外观、主题令牌与导出时间，可保存到任意目录",
        ) { exportBackup() }
        backup.action(
            "从文件恢复",
            value = "覆盖当前配置",
            hint = "读取备份包并整体覆盖配置，恢复前需要确认",
        ) { pickBackupForRestore() }
        val theme = SettingsUi.section(page, "主题与令牌", "当前主题与配色来源")
        theme.info("主题", SettingsUi.themeLabel(config.appearance.themeId))
        theme.info(
            "配色来源",
            if (config.appearance.themeTokensJson.isBlank()) "内置预设" else "自定义令牌",
        )
        theme.info(
            "图标包",
            SettingsUi.iconPackLabel(config.appearance.iconPackId),
        )

        if (lastResult.isNotBlank()) {
            val status = SettingsUi.section(page, "上次操作", null)
            status.info("结果", lastResult)
        }

        SettingsUi.note(
            page,
            "备份包含外观、桌面、状态栏、锁屏与通知等全部配置，恢复前可在导出目录保留旧备份。",
        )
    }

    /** 选择导出位置并写入备份包。 */
    private fun exportBackup() {
        val context = contextOrNull() ?: return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        SettingsUi.saveFile(context, "octoberrain-backup-$stamp.zip", "application/zip") { uri ->
            scope.launch {
                val exported = runCatching { AppServices.backup.export(uri) }.getOrDefault(false)
                lastResult =
                    if (exported) {
                        "备份已导出"
                    } else {
                        "导出失败，请更换保存位置后重试"
                    }
                SettingsUi.toast(context, lastResult)
                rebuildLater()
            }
        }
    }

    /** 选择备份包，确认后整体恢复配置。 */
    private fun pickBackupForRestore() {
        val context = contextOrNull() ?: return
        SettingsUi.pickFile(context, "选择备份包", BACKUP_MIME_TYPES, BACKUP_EXTENSIONS) { uri ->
            InkDialogs.confirm(
                context,
                "恢复配置",
                "将用备份包覆盖当前全部配置与主题，当前设置无法撤销。",
                confirmLabel = "恢复",
                onConfirm = { restoreBackup(context, uri) },
            )
        }
    }

    /** 后台恢复配置，完成后刷新外观并重建设置页。 */
    private fun restoreBackup(
        context: android.content.Context,
        uri: Uri,
    ) {
        scope.launch {
            SettingsUi.toast(context, "正在恢复备份…")
            val result = runCatching { AppServices.backup.import(uri) }.getOrNull()
            if (result == null) {
                lastResult = "恢复失败，备份文件无法读取"
            } else {
                lastResult =
                    buildString {
                        append(result.message)
                        if (result.sections.isNotEmpty()) {
                            append("（")
                            append(result.sections.joinToString("、"))
                            append("）")
                        }
                    }
                applyRestoredAppearance()
            }
            SettingsUi.toast(context, lastResult)
            rebuildLater()
        }
    }

    /** 恢复完成后把外观重新写入设计令牌并请求整屏刷新。 */
    private fun applyRestoredAppearance() {
        val appearance = AppServices.config.state.value.appearance
        SettingsUi.applyAppearance(appearance)
        AppServices.refresh.requestFullRefresh()
    }

    private companion object {
        /** 备份包选择的 mime 类型。 */
        val BACKUP_MIME_TYPES: Array<String> = arrayOf("application/zip", "application/octet-stream")

        /** 备份包候选扩展名，用于扫描回退。 */
        val BACKUP_EXTENSIONS: Set<String> = setOf("zip")
    }
}
