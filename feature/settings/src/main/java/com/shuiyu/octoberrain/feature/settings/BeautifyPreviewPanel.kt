package com.shuiyu.octoberrain.feature.settings

import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopLayoutSnapshot
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.LauncherEntry

// 美化模式的分页装载与预览入口。
//
// 页结构与容量统一来自 DesktopPagination，本文件只负责把内存顺序与快照对齐，
// 以及把当前顺序交给预览页。这样美化模式与预览页不再各自换算分页。

/** 切换页码：只刷新左栏应用列表，右栏图片保持不动。 */
internal fun BeautifyScreen.switchPage(index: Int) {
    val view = root ?: return
    view.post {
        if (root === view) switchPageNow(index)
    }
}

/** 立即切换页码并重建左栏；页码行随左栏头部一起刷新。 */
internal fun BeautifyScreen.switchPageNow(index: Int) {
    if (index == currentPage || index !in workingPages.indices) return
    currentPage = index
    refreshLeftNow()
}

/** 请求重建左栏，已附着时推迟到下一帧。 */
internal fun BeautifyScreen.refreshLeft() {
    val view = root ?: return
    view.post {
        if (root === view) refreshLeftNow()
    }
}

/** 立即按当前页与选中项重建左栏卡片。 */
internal fun BeautifyScreen.refreshLeftNow() {
    val row = cardRow ?: return
    val card = leftCard ?: return
    val context = row.context
    val snapshot = snapshot(AppServices.config.state.value)
    loadPages(snapshot)
    val entries = currentEntries(snapshot)
    selectedKey = entries.firstOrNull { it.key == selectedKey }?.key
        ?: entries.firstOrNull()?.key
    val index = row.indexOfChild(card)
    if (index < 0) return
    val fresh = buildAppColumn(context, entries, snapshot)
    row.removeViewAt(index)
    row.addView(
        fresh,
        index,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.52f),
    )
    leftCard = fresh
}

/**
 * 从桌面快照装载完整页顺序。
 *
 * 尚未保存改动时保留内存顺序，只补齐新增条目并清理已移除条目；
 * 否则直接采用快照顺序，保证配置变化后页码与页内容一致。
 */
internal fun BeautifyScreen.loadPages(snapshot: DesktopLayoutSnapshot) {
    val structure = DesktopPagination.keysOf(snapshot).map { keys -> keys.toMutableList() }.toMutableList()
    if (structure.isEmpty()) structure.add(mutableListOf())
    if (pagesDirty && workingPages.isNotEmpty()) {
        // 保留跨页移动后的归属：以内存页结构为准，只清理失效条目并补齐新增条目。
        val snapshotKeys = structure.flatten().toSet()
        val next =
            workingPages
                .map { keys -> keys.filter { it in snapshotKeys }.toMutableList() }
                .toMutableList()
        while (next.size < structure.size) next.add(mutableListOf())
        while (next.size > structure.size) {
            val removed = next.removeAt(next.lastIndex)
            if (next.isNotEmpty()) next.last().addAll(removed)
        }
        val missing = structure.flatten().filter { key -> next.none { key in it } }
        if (missing.isNotEmpty()) next.last().addAll(missing)
        workingPages = next
    } else {
        workingPages = structure
    }
    if (currentPage !in workingPages.indices) currentPage = 0
}

/** 当前页条目，按内存顺序排列。 */
internal fun BeautifyScreen.currentEntries(snapshot: DesktopLayoutSnapshot): List<LauncherEntry> {
    val byKey = snapshot.pages.flatten().associateBy { it.key }
    return workingPages.getOrNull(currentPage).orEmpty().mapNotNull { byKey[it] }
}

/** 指定页的容量：列数乘以该页行数，首页行数可能与后续页不同。 */
internal fun BeautifyScreen.pageCapacity(
    snapshot: DesktopLayoutSnapshot,
    pageIndex: Int,
): Int = DesktopPagination.pageCapacity(snapshot, pageIndex)

/** 读取桌面分页快照：布局提供方可用时直接复用，缺失时由 DesktopPagination 兜底。 */
internal fun BeautifyScreen.snapshot(config: LauncherConfig): DesktopLayoutSnapshot = DesktopPagination.snapshot(config)

/** 页结构与内存顺序不一致时要求重建，应用安装或布局参数变化会触发。 */
internal fun BeautifyScreen.pageContentChanged(config: LauncherConfig): Boolean {
    val snapshotPages = DesktopPagination.keysOf(snapshot(config))
    if (snapshotPages.size != workingPages.size) return true
    return snapshotPages.indices.any { index ->
        snapshotPages[index] != workingPages[index].toList()
    }
}

/** 预览：把完整内存顺序写入进程内状态，再打开整屏预览页。 */
internal fun BeautifyScreen.openPreview() {
    val context = root?.context ?: return
    val snapshot = snapshot(AppServices.config.state.value)
    loadPages(snapshot)
    val labels =
        buildMap {
            snapshot.pages.forEach { page ->
                page.forEach { entry -> put(entry.key, entry.label) }
            }
        }
    BeautifyPreviewState.put(
        pageIndex = currentPage,
        workingKeys = workingPages.getOrElse(currentPage) { mutableListOf() }.toList(),
        pages = workingPages.map { page -> page.toList() },
        labels = labels,
    )
    runCatching { AppServices.router.navigate(Destination.BeautifyPreview) }
        .onFailure { SettingsUi.toast(context, "无法打开预览") }
}
