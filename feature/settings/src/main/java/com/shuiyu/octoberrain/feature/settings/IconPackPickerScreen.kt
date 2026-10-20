package com.shuiyu.octoberrain.feature.settings

import android.content.Intent
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkPage

/**
 * 图标包选择页面。
 *
 * 通过桌面主题约定的 action 枚举已安装图标包，包括 ADW 兼容包与 GO 桌面兼容包，
 * 选择结果写入外观配置。图标包只影响桌面图标绘制，未命中的图标回落内置线稿。
 */
class IconPackPickerScreen : SettingsBaseScreen("图标包") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val currentId = config.appearance.iconPackId
        val packs = installedIconPacks(page)
        val installedIds = packs.map { it.packageName }.toSet()

        SettingsUi.note(
            page,
            "图标包通过 appfilter 映射替换桌面图标，未命中的图标回落内置线稿。",
        )
        if (currentId != BUILTIN_ID && currentId !in installedIds) {
            SettingsUi.note(page, "当前记录的图标包未安装，桌面图标已回退内置线稿。")
        }

        val list =
            SettingsUi.section(
                page,
                "已安装图标包",
                "选择后立即生效，图标缓存按图标包区分",
            )
        list.action(
            label = if (currentId == BUILTIN_ID) "● 内置线稿" else "内置线稿",
            value = if (currentId == BUILTIN_ID) "当前" else null,
            hint = "不使用图标包，全部图标由内置线稿绘制",
        ) {
            if (currentId != BUILTIN_ID) apply(BUILTIN_ID)
        }
        packs.forEach { pack ->
            val isCurrent = pack.packageName == currentId
            list.action(
                label = if (isCurrent) "● ${pack.label}" else pack.label,
                value = if (isCurrent) "当前" else null,
                hint = pack.packageName,
            ) {
                if (!isCurrent) apply(pack.packageName)
            }
        }
        if (packs.isEmpty()) {
            SettingsUi.note(
                page,
                "未检测到已安装图标包。安装 ADW 或 GO 桌面兼容图标包后重新进入本页。",
            )
        }

        SettingsUi.note(
            page,
            "图标包只影响桌面图标绘制，应用名称字体与钟面字体在字体页面调整。",
        )
    }

    /** 写入图标包标识并刷新外观运行时与当前页面。 */
    private fun apply(packId: String) {
        SettingsUi.edit { it.copy(appearance = it.appearance.copy(iconPackId = packId)) }
        SettingsUi.applyAppearance(SettingsUi.settings().appearance)
        rebuildLater()
    }

    /**
     * 枚举已安装图标包。
     *
     * 分别查询 ADW 兼容与 GO 桌面兼容的图标包 action，按包名去重，
     * 应用标签读取失败时以包名代替。
     */
    private fun installedIconPacks(page: InkPage): List<IconPackEntry> {
        val context = page.context
        val packageManager = context.packageManager
        val found = LinkedHashMap<String, String>()
        for (action in THEME_ACTIONS) {
            val resolved =
                runCatching {
                    packageManager.queryIntentActivities(Intent(action), 0)
                }.getOrDefault(emptyList())
            for (info in resolved) {
                val activityInfo = info.activityInfo ?: continue
                val packageName = activityInfo.packageName ?: continue
                if (packageName == context.packageName) continue
                if (found.containsKey(packageName)) continue
                val label =
                    runCatching {
                        activityInfo.loadLabel(packageManager).toString()
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
                found[packageName] = label
            }
        }
        return found
            .map { (packageName, label) -> IconPackEntry(packageName, label) }
            .sortedBy { it.label }
    }

    /** 图标包条目：包名用于写入配置，名称用于展示。 */
    private data class IconPackEntry(
        val packageName: String,
        val label: String,
    )

    private companion object {
        /** 内置线稿图标包标识。 */
        const val BUILTIN_ID = "builtin"

        /** 常见桌面主题图标包声明的 action。 */
        val THEME_ACTIONS =
            listOf(
                "org.adw.launcher.THEMES",
                "com.gau.go.launcherex.theme",
            )
    }
}
