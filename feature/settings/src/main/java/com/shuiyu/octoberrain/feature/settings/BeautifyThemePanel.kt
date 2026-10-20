package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.BeautifyTheme
import com.shuiyu.octoberrain.core.common.ThemeScope
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

// 美化模式的主题与外观操作。
//
// 主题记录当前顺序、布局参数与图标映射；保存后可在主题库中应用、导出或删除，
// 导出的单文件把图片以 Base64 内嵌。另一入口「恢复默认图标」只清除图标覆盖，
// 保留排序与已保存主题。

/** 主题文件的 mime 类型。 */
private val THEME_MIME_TYPES: Array<String> =
    arrayOf("application/json", "application/octet-stream")

/** 主题文件允许的扩展名。 */
private val THEME_EXTENSIONS: Set<String> = setOf("ttp", "json")

/** 主题范围显示文本。 */
internal fun scopeLabel(scope: ThemeScope): String = if (scope == ThemeScope.HOME) "范围：首页" else "范围：全部"

/** 打开主题库全屏子页，列出名称、图标数量、范围与排序项数。 */
internal fun BeautifyScreen.showThemeLibrary() {
    val context = root?.context ?: return
    themePage = null
    SettingsUi.overlayPage(context, "主题库") { page ->
        themePage = page
        renderThemeLibraryInto(page)
    }
}

/** 主题页内容变更后就地重绘，对话框未打开时忽略。 */
internal fun BeautifyScreen.renderThemeLibrary() {
    val page = themePage ?: return
    page.post {
        if (themePage === page) {
            page.content.removeAllViews()
            renderThemeLibraryInto(page)
        }
    }
}

/** 填充主题库内容：主题行与恢复默认图标入口。 */
private fun BeautifyScreen.renderThemeLibraryInto(page: InkPage) {
    val context = page.context
    val themes = runCatching { AppServices.themes.themes() }.getOrDefault(emptyList())
    val section = SettingsUi.section(page, "已保存主题", "顺序、布局与图标映射")
    if (themes.isEmpty()) {
        section.content(
            InkWidgets.caption(context, "尚未保存主题").apply {
                val tokens = DesignRuntime.tokens
                setPadding(tokens.gap, tokens.gap, tokens.gap, tokens.gap)
            },
        )
    } else {
        themes.forEach { theme -> section.content(buildThemeRow(context, theme)) }
    }
    section.button("恢复默认图标") { confirmResetIcons() }
    SettingsUi.note(
        page,
        "保存主题会记录当前顺序、布局参数与图标映射；导出的 .ttp 文件可迁移到其他设备。",
    )
}

/** 主题单行：名称、摘要与三个操作。 */
private fun BeautifyScreen.buildThemeRow(
    context: Context,
    theme: BeautifyTheme,
): View {
    val tokens = DesignRuntime.tokens
    val row =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(tokens.gap, tokens.gap / 2, tokens.gap, tokens.gap / 2)
        }
    row.addView(
        InkWidgets.textView(context, theme.name, tokens.textBody, InkColors.INK).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        },
    )
    row.addView(
        InkWidgets
            .textView(
                context,
                "${theme.iconOverrides.size} 个图标 · ${scopeLabel(theme.scope)} · ${theme.order.size} 项排序",
                tokens.textCaption,
                InkColors.INK_2,
            ).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, tokens.gap / 4, 0, tokens.gap / 4)
            },
    )
    val actions =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }
    actions.addView(compactButton(context, "应用") { applyTheme(theme) }, thirdButtonParams(1f))
    actions.addView(gapView(context), thirdButtonParams(0f).apply { width = tokens.gap / 2 })
    actions.addView(compactButton(context, "导出") { exportTheme(theme) }, thirdButtonParams(1f))
    actions.addView(gapView(context), thirdButtonParams(0f).apply { width = tokens.gap / 2 })
    actions.addView(compactButton(context, "删除") { confirmDeleteTheme(theme) }, thirdButtonParams(1f))
    row.addView(
        actions,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            tokens.buttonHeight,
        ),
    )
    return row
}

/** 输入名称后选择保存范围并保存主题。 */
internal fun BeautifyScreen.promptSaveTheme() {
    val context = root?.context ?: return
    InkDialogs.input(context, "保存主题", hint = "例如 极简黑白") { name ->
        val trimmed = name.trim()
        val current = root?.context ?: return@input
        if (trimmed.isEmpty()) {
            SettingsUi.toast(current, "主题名称不能为空")
            return@input
        }
        InkDialogs.choose(
            current,
            "保存范围",
            listOf("保持首页", "保持全部"),
        ) { index ->
            val scopeValue = if (index == 0) ThemeScope.HOME else ThemeScope.ALL
            scope.launch {
                val theme =
                    runCatching {
                        AppServices.themes.saveTheme(trimmed, scopeValue)
                    }.getOrNull()
                if (root == null) return@launch
                if (theme == null) {
                    SettingsUi.toast(requireNotNull(root).context, "主题保存失败")
                } else {
                    SettingsUi.toast(
                        requireNotNull(root).context,
                        "已保存主题 ${theme.name}（${scopeLabel(scopeValue)}）",
                    )
                    renderThemeLibrary()
                }
            }
        }
    }
}

/** 从文件导入主题，导入后刷新主题库。 */
internal fun BeautifyScreen.importTheme() {
    val context = root?.context ?: return
    SettingsUi.pickFile(context, "导入主题文件", THEME_MIME_TYPES, THEME_EXTENSIONS) { uri ->
        scope.launch {
            val ok = runCatching { AppServices.themes.importTheme(uri) }.getOrDefault(false)
            if (root == null) return@launch
            SettingsUi.toast(
                requireNotNull(root).context,
                if (ok) "主题已导入" else "主题导入失败，请检查文件格式",
            )
            if (ok) {
                renderThemeLibrary()
                render()
            }
        }
    }
}

/** 清空仅清除自定义图标，桌面顺序与布局参数保持不变。 */
internal fun BeautifyScreen.confirmResetDesktop() {
    val context = root?.context ?: return
    InkDialogs.confirm(
        context,
        "清空图标",
        "仅清除全部自定义图标，桌面顺序与布局参数保持不变。已保存的主题不受影响。",
        confirmLabel = "清除",
        onConfirm = {
            val active = root
            if (active != null) {
                runCatching { AppServices.iconOverrides.clearAll() }
                clearOverrideFiles(active.context)
                selectedImagePath = null
                showingOverrides = false
                showStatus("已清除全部自定义图标")
                SettingsUi.refresh()
                render()
            }
        },
    )
}

/** 确认后仅恢复默认图标，保留排序与主题。 */
internal fun BeautifyScreen.confirmResetIcons() {
    val context = root?.context ?: return
    InkDialogs.confirm(
        context,
        "恢复默认图标",
        "将清除全部应用的图标覆盖，桌面图标回到系统图标。排序与已保存主题不受影响。",
        confirmLabel = "恢复默认",
        onConfirm = {
            val active = root
            if (active != null) {
                runCatching { AppServices.iconOverrides.clearAll() }
                clearOverrideFiles(active.context)
                SettingsUi.toast(active.context, "图标覆盖已清除")
                selectedImagePath = null
                showingOverrides = false
                SettingsUi.refresh()
                render()
                renderThemeLibrary()
            }
        },
    )
}

/** 删除图标覆盖的私有副本，避免清空后旧文件继续占用存储。 */
internal fun BeautifyScreen.clearOverrideFiles(context: Context) {
    scope.launch(Dispatchers.IO) {
        runCatching { File(context.filesDir, "icon-overrides").deleteRecursively() }
    }
}

/** 应用主题：写回顺序、布局与图标映射，然后整屏刷新。 */
internal fun BeautifyScreen.applyTheme(theme: BeautifyTheme) {
    scope.launch {
        val ok = runCatching { AppServices.themes.applyTheme(theme.id) }.getOrDefault(false)
        if (root == null) return@launch
        if (!ok) {
            SettingsUi.toast(requireNotNull(root).context, "主题应用失败")
            return@launch
        }
        SettingsUi.toast(requireNotNull(root).context, "已应用主题 ${theme.name}")
        pagesDirty = false
        SettingsUi.fullRefresh()
        render()
        renderThemeLibrary()
    }
}

/** 导出主题为单文件，图片以 Base64 内嵌。 */
internal fun BeautifyScreen.exportTheme(theme: BeautifyTheme) {
    val context = root?.context ?: return
    val fileName = "theme-${theme.name}.ttp"
    SettingsUi.saveFile(context, fileName, "application/json") { uri ->
        scope.launch {
            val ok =
                runCatching { AppServices.themes.exportTheme(theme.id, uri) }
                    .getOrDefault(false)
            if (root == null) return@launch
            SettingsUi.toast(
                requireNotNull(root).context,
                if (ok) "已导出 $fileName" else "主题导出失败",
            )
        }
    }
}

/** 确认后删除主题及其内嵌图标副本。 */
internal fun BeautifyScreen.confirmDeleteTheme(theme: BeautifyTheme) {
    val context = root?.context ?: return
    InkDialogs.confirm(
        context,
        "删除主题",
        "将删除「${theme.name}」及其内嵌图标副本，桌面当前使用的图标不受影响。",
        confirmLabel = "删除",
        onConfirm = {
            scope.launch {
                runCatching { AppServices.themes.deleteTheme(theme.id) }
                if (root == null) return@launch
                SettingsUi.toast(requireNotNull(root).context, "主题已删除")
                renderThemeLibrary()
            }
        },
    )
}
