package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.DesktopMode
import com.shuiyu.octoberrain.core.common.DesktopOrientation
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkPage

/** 模式与常用栏的二级设置；组件选择由组件库决定，所有状态沿现有配置落盘。 */
internal object DesktopModeSettings {
    fun entry(
        page: InkPage,
        beforeChange: () -> Unit,
        onModeChanged: () -> Unit,
    ) {
        buildLayout(page, beforeChange, onModeChanged)
    }

    private fun edit(change: (DesktopConfig) -> DesktopConfig) {
        SettingsUi.edit { it.copy(desktop = change(it.desktop)) }
    }

    private fun buildLayout(
        page: InkPage,
        beforeChange: () -> Unit,
        onModeChanged: () -> Unit,
    ) {
        val desktop = SettingsUi.settings().desktop
        val modes = listOf(DesktopMode.NORMAL, DesktopMode.IMMERSIVE)
        val orientations =
            listOf(DesktopOrientation.PORTRAIT, DesktopOrientation.LANDSCAPE, DesktopOrientation.REVERSE_LANDSCAPE)
        val section = SettingsUi.section(page, "桌面模式", "普通布局始终保留；沉浸固定本书")
        section.choose("模式", listOf("普通", "沉浸本书"), { modes.indexOf(desktop.mode) }) { index ->
            beforeChange()
            edit { it.copy(mode = modes[index]) }
            onModeChanged()
        }
        section.choose(
            "方向",
            listOf("竖屏", "横屏 90°", "横屏 270°"),
            { orientations.indexOf(SettingsUi.settings().desktop.orientation) },
        ) { index ->
            beforeChange()
            edit { it.copy(orientation = orientations[index]) }
        }
        if (desktop.mode == DesktopMode.IMMERSIVE) {
            val favorites = SettingsUi.section(page, "常用应用栏", "实际数量按屏幕空间和可用应用调整")
            favorites.choose(
                "应用数量",
                listOf("3", "4", "5", "6"),
                { desktop.favoriteCount.coerceIn(3, 6) - 3 },
            ) { index ->
                edit { it.copy(favoriteCount = index + 3) }
            }
            favorites.action("选择与排序", "${desktop.favoriteEntryKeys.size} 项") { showFavorites(page.context) }
        }
    }

    private fun favoriteCandidates(): List<Pair<String, String>> {
        val desktop = SettingsUi.settings().desktop
        val apps =
            AppServices.catalog.apps.value
                .filter { it.packageName !in desktop.hiddenPackages }
                .sortedBy { it.label }
                .map { "app:${it.packageName}" to it.label }
        val specials =
            listOf(
                "special:weread" to "微信读书",
                "special:files" to "文件",
                "special:settings" to "设置",
            )
        return apps + specials.filter { it.first !in desktop.hiddenSpecials }
    }

    private fun showFavorites(context: Context) {
        SettingsUi.overlayPage(context, "常用应用与顺序") { page ->
            fun build() {
                page.content.removeAllViews()
                val candidates = favoriteCandidates()
                val selected = SettingsUi.settings().desktop.favoriteEntryKeys
                val section = SettingsUi.section(page, "已选顺序", "点击条目可上移、下移或移除；空列表使用桌面前几项")
                selected.forEachIndexed { index, key ->
                    val label = candidates.firstOrNull { it.first == key }?.second ?: "不可用应用"
                    section.action("${index + 1}. $label") {
                        InkDialogs.choose(context, label, listOf("上移", "下移", "移除")) { action ->
                            edit { old -> old.copy(favoriteEntryKeys = reorder(old.favoriteEntryKeys, key, action)) }
                            page.post { build() }
                        }
                    }
                }
                val remaining = candidates.filter { it.first !in selected }
                section.action("添加应用") {
                    if (remaining.isNotEmpty()) {
                        InkDialogs.choose(context, "添加常用应用", remaining.map { it.second }) { index ->
                            edit { it.copy(favoriteEntryKeys = it.favoriteEntryKeys + remaining[index].first) }
                            page.post { build() }
                        }
                    }
                }
            }
            build()
        }
    }

    private fun reorder(
        values: List<String>,
        key: String,
        action: Int,
    ): List<String> {
        val result = values.toMutableList()
        val index = result.indexOf(key)
        if (index < 0) return values
        if (action == 2) {
            result.removeAt(index)
        } else {
            val target = (index + if (action == 0) -1 else 1).coerceIn(0, result.lastIndex)
            result.removeAt(index)
            result.add(target, key)
        }
        return result
    }
}
