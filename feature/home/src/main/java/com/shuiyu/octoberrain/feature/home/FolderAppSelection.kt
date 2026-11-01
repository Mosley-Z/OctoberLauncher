package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.DesktopFolder
import com.shuiyu.octoberrain.core.common.EntryKeys
import com.shuiyu.octoberrain.core.common.FolderChangeSummary
import com.shuiyu.octoberrain.core.common.InstalledApp
import java.util.Locale

data class FolderCandidate(
    val key: String,
    val label: String,
    val packageName: String,
    val installed: Boolean,
    val parentFolderId: String? = null,
    val parentFolderName: String? = null,
)

/**
 * 文件夹成员选择的纯数据快照与计划。
 *
 * 候选条目以组件键标识，同一包名的多个启动入口各自独立出现在候选列表与文件夹成员中；
 * 包名只用于隐藏过滤、安装状态判断与搜索。
 */
object FolderAppSelection {
    fun candidates(
        apps: List<InstalledApp>,
        folders: List<DesktopFolder>,
        hiddenPackages: Set<String>,
        targetFolderId: String?,
    ): List<FolderCandidate> {
        val parents =
            folders
                .flatMap { folder -> folder.memberKeys.map { it to folder } }
                .groupBy({ it.first }, { it.second })
        return apps
            .filter { it.packageName !in hiddenPackages }
            .sortedWith(
                compareBy<InstalledApp> { it.label.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName }
                    .thenBy { it.activityName.orEmpty() }
                    .thenBy { it.label },
            ).distinctBy { EntryKeys.appEntryKey(it) }
            .map { app ->
                val key = EntryKeys.appEntryKey(app)
                // 组件键命中优先；旧包级成员键仍指向同一个应用的既有文件夹
                val owners = parents[key].orEmpty().ifEmpty { parents[EntryKeys.packageKey(app.packageName)].orEmpty() }
                val parent = owners.firstOrNull { it.id == targetFolderId } ?: owners.firstOrNull()
                FolderCandidate(key, app.label, app.packageName, true, parent?.id, parent?.name)
            }
    }

    fun filter(
        candidates: List<FolderCandidate>,
        query: String,
    ): List<FolderCandidate> {
        val term = query.trim()
        return candidates.filter { it.label.contains(term, true) || it.packageName.contains(term, true) }
    }

    /** 两参数版本无法校验候选资格，界面使用带候选集的重载。 */
    fun toggle(
        selected: Set<String>,
        key: String,
    ): Set<String> =
        when {
            key in selected -> selected - key
            !EntryKeys.isAppKey(key) -> selected
            else -> selected + key
        }

    fun toggle(
        selected: Set<String>,
        key: String,
        candidates: List<FolderCandidate>,
        targetFolderId: String?,
    ): Set<String> =
        if (candidates.any { it.key == key && eligible(it, targetFolderId) }) toggle(selected, key) else selected - key

    fun selectedKeys(
        candidates: List<FolderCandidate>,
        selected: Set<String>,
        targetFolderId: String?,
    ): List<String> = candidates.filter { it.key in selected && eligible(it, targetFolderId) }.map { it.key }.distinct()

    fun plan(
        candidates: List<FolderCandidate>,
        selected: Set<String>,
        targetFolderId: String?,
    ): FolderChangeSummary {
        val seen = HashSet<String>()
        var added = 0
        var moved = 0
        var skipped = selected.count { key -> candidates.none { it.key == key } }
        candidates.filter { it.key in selected }.forEach {
            if (!seen.add(it.key) || !eligible(it, targetFolderId)) {
                skipped++
            } else if (it.parentFolderId == null) {
                added++
            } else {
                moved++
            }
        }
        return FolderChangeSummary(added, moved, skipped)
    }

    /**
     * 复核已保存的成员键：包名仍在安装列表中的组件键保留，其余计入失效数量。
     *
     * 参数 candidateKeys 为待复核的入口键；参数 installedPackages 为当前已安装包名。
     */
    fun revalidate(
        candidateKeys: List<String>,
        installedPackages: Set<String>,
    ): Pair<List<String>, Int> {
        val valid =
            candidateKeys
                .filter { key ->
                    val packageName = EntryKeys.packageOf(key) ?: return@filter false
                    packageName in installedPackages
                }.distinct()
        return valid to (candidateKeys.size - valid.size)
    }

    private fun eligible(
        candidate: FolderCandidate,
        targetFolderId: String?,
    ): Boolean =
        candidate.installed &&
            EntryKeys.isAppKey(candidate.key) &&
            candidate.packageName.isNotBlank() &&
            (targetFolderId == null || candidate.parentFolderId != targetFolderId)
}
