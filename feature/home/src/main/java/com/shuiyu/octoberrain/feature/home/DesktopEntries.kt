package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.EntryType
import com.shuiyu.octoberrain.core.common.InstalledApp
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.common.SortMode
import com.shuiyu.octoberrain.core.common.SpecialEntry

/**
 * 桌面入口组装。
 *
 * 依据已安装应用与桌面配置生成最终入口序列：先过滤隐藏包名，
 * 再按排序模式排序，随后插入未被配置隐藏的内置特殊入口，最后套用用户自定义顺序。
 */
internal object DesktopEntries {
    /** 内置特殊入口的定义：持久化键、展示名与入口标识。 */
    private data class SpecialSpec(
        val key: String,
        val label: String,
        val entry: SpecialEntry,
    )

    /** 内置特殊入口的固定顺序，与上游桌面保持一致。 */
    private val specials =
        listOf(
            SpecialSpec("special:weread", "微信读书", SpecialEntry.WEREAD),
            SpecialSpec("special:koreader", "KOReader", SpecialEntry.KOREADER),
            SpecialSpec("special:files", "文件", SpecialEntry.FILES),
            SpecialSpec("special:bookshelf", "书架", SpecialEntry.BOOKSHELF),
            SpecialSpec("special:settings", "设置", SpecialEntry.SETTINGS),
        )

    /**
     * 生成入口列表。
     *
     * 参数 apps 为应用目录快照；desktop 为桌面配置。
     * 返回值按用户顺序排列，未出现在 order 中的条目排在已排序条目之后。
     */
    fun build(
        apps: List<InstalledApp>,
        desktop: DesktopConfig,
    ): List<LauncherEntry> {
        val byPackage = apps.associateBy { it.packageName }
        val visible = apps.filter { it.packageName !in desktop.hiddenPackages }
        val appEntries =
            visible.map { app ->
                LauncherEntry(
                    key = "app:${app.packageName}",
                    type = EntryType.APP,
                    label = app.label,
                    packageName = app.packageName,
                )
            }
        val sortedApps =
            when (desktop.sortMode) {
                SortMode.INSTALL_TIME ->
                    appEntries.sortedWith(
                        // 首次进入桌面按安装时间升序排列，最早安装的应用排在前面。
                        compareBy<LauncherEntry> { byPackage[it.packageName]?.firstInstallTime ?: 0L }
                            .thenBy { it.label }
                            .thenBy { it.packageName ?: "" },
                    )
                SortMode.LABEL ->
                    appEntries.sortedWith(
                        compareBy<LauncherEntry> { it.label }.thenBy { it.packageName ?: "" },
                    )
                SortMode.PACKAGE -> appEntries.sortedBy { it.packageName ?: "" }
                SortMode.MANUAL -> appEntries
            }
        // 按配置过滤内置入口；默认配置隐藏微信读书、文件与书架三个入口。
        val specialEntries =
            specials.filter { it.key !in desktop.hiddenSpecials }.map { spec ->
                LauncherEntry(
                    key = spec.key,
                    type = EntryType.SPECIAL,
                    label = spec.label,
                    special = spec.entry,
                )
            }
        val base = sortedApps + specialEntries
        if (desktop.order.isEmpty()) return base
        // 稳定排序：未记录顺序的条目保留原有相对次序，并排在已记录顺序的条目之后。
        val rank = desktop.order.withIndex().associate { (index, key) -> key to index }
        return base.sortedWith(compareBy { rank[it.key] ?: Int.MAX_VALUE })
    }
}
