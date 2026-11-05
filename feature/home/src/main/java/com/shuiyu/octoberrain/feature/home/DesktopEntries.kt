package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.EntryKeys
import com.shuiyu.octoberrain.core.common.EntryType
import com.shuiyu.octoberrain.core.common.FolderRules
import com.shuiyu.octoberrain.core.common.InstalledApp
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.common.SortMode
import com.shuiyu.octoberrain.core.common.SpecialEntry

/**
 * 桌面入口组装。
 *
 * 依据已安装应用与桌面配置生成最终入口序列：先过滤隐藏包名与文件夹成员，
 * 再按排序模式排序，随后插入未被配置隐藏的内置特殊入口，最后套用用户自定义顺序。
 *
 * 应用入口键使用组件键 `app:<包名>/<Activity>`，同一包名的多个启动入口因此各自
 * 独立排序、独立参与文件夹成员关系；包名仍用于隐藏、卸载与应用信息查询。
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
            SpecialSpec("special:files", "十雨文件", SpecialEntry.FILES),
            SpecialSpec("special:settings", "十雨设置", SpecialEntry.SETTINGS),
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
        val normalized = FolderRules.normalize(desktop)
        val memberKeys = normalized.folders.flatMap { it.memberKeys }.toSet()
        val visible =
            apps.filter {
                it.packageName !in normalized.hiddenPackages && EntryKeys.appEntryKey(it) !in memberKeys
            }
        val appEntries = buildAppEntries(visible, normalized.sortMode)
        // 按配置过滤内置入口；默认配置隐藏微信读书与文件入口；旧书架键不再生成可见条目。
        val specialEntries =
            specials.filter { it.key !in desktop.hiddenSpecials }.map { spec ->
                LauncherEntry(
                    key = spec.key,
                    type = EntryType.SPECIAL,
                    label = spec.label,
                    special = spec.entry,
                )
            }
        val folderEntries =
            normalized.folders.map { folder ->
                LauncherEntry(
                    key = "folder:${folder.id}",
                    type = EntryType.FOLDER,
                    label = folder.name.ifBlank { "文件夹" },
                    folder = folder,
                )
            }
        val base = appEntries + folderEntries + specialEntries
        if (desktop.order.isEmpty()) return base
        // 稳定排序：未记录顺序的条目保留原有相对次序，并排在已记录顺序的条目之后。
        val rank = normalized.order.withIndex().associate { (index, key) -> key to index }
        return base.sortedWith(compareBy { rank[it.key] ?: Int.MAX_VALUE })
    }

    /**
     * 按排序模式生成应用入口。
     *
     * 参数 visible 为已剔除隐藏包名与文件夹成员的目录条目，sortMode 为桌面排序模式；
     * 排序直接作用于目录条目，安装时间因此按条目所属应用取。
     */
    private fun buildAppEntries(
        visible: List<InstalledApp>,
        sortMode: SortMode,
    ): List<LauncherEntry> =
        sortApps(visible, sortMode).map { app ->
            LauncherEntry(
                key = EntryKeys.appEntryKey(app),
                type = EntryType.APP,
                label = app.label,
                packageName = app.packageName,
                activityName = app.activityName,
            )
        }

    /** 按排序模式排列目录条目；手动排序保持目录给定顺序。 */
    private fun sortApps(
        apps: List<InstalledApp>,
        sortMode: SortMode,
    ): List<InstalledApp> =
        when (sortMode) {
            // 首次进入桌面按安装时间升序排列，最早安装的应用排在前面。
            SortMode.INSTALL_TIME ->
                apps.sortedWith(
                    compareBy<InstalledApp> { it.firstInstallTime }
                        .thenBy { it.label }
                        .thenBy { it.packageName }
                        .thenBy { it.activityName.orEmpty() },
                )
            SortMode.LABEL ->
                apps.sortedWith(
                    compareBy<InstalledApp> { it.label }
                        .thenBy { it.packageName }
                        .thenBy { it.activityName.orEmpty() },
                )
            SortMode.PACKAGE ->
                apps.sortedWith(
                    compareBy<InstalledApp> { it.packageName }
                        .thenBy { it.activityName.orEmpty() },
                )
            SortMode.MANUAL -> apps
        }
}
