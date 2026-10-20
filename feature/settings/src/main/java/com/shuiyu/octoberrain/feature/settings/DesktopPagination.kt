package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopLayoutSnapshot
import com.shuiyu.octoberrain.core.common.EntryType
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.LauncherEntry

/**
 * 桌面分页的统一来源。
 *
 * 美化模式与预览页都需要「当前桌面分成几页、每页有哪些条目」，
 * 分页算法只应存在一份：优先读取 feature/home 实现的 DesktopLayoutProvider，
 * 提供方缺失时按配置列数与行数在本地换算，换算部分为纯函数，可在本地单元测试中验证。
 */
internal object DesktopPagination {
    /** 提供方缺失时的兜底列数。 */
    const val DEFAULT_COLUMNS = 5

    /** 提供方缺失时的兜底行数。 */
    const val DEFAULT_ROWS = 5

    /**
     * 读取桌面分页快照。
     *
     * 参数 config 为当前配置，仅在布局提供方缺失时用于本地换算。
     * 提供方抛出异常时同样回退到本地换算，避免美化模式与预览页整页不可用。
     */
    fun snapshot(config: LauncherConfig): DesktopLayoutSnapshot {
        if (AppServices.hasLayoutProvider()) {
            runCatching { AppServices.layoutProvider.snapshot() }.getOrNull()?.let { return it }
        }
        return fallbackSnapshot(config)
    }

    /**
     * 按配置列数与行数换算分页快照，不读取布局提供方。
     *
     * 参数 config 提供桌面列数、行数与首页行数；参数 entries 为按顺序排列的条目，
     * 供单元测试直接注入，生产代码使用应用目录。首页按首页行数切分，
     * 后续页按其余页行数切分。
     */
    fun fallbackSnapshot(
        config: LauncherConfig,
        entries: List<LauncherEntry> = appEntries(),
    ): DesktopLayoutSnapshot {
        val desktop = config.desktop
        val columns = desktop.columns.takeIf { it > 0 } ?: DEFAULT_COLUMNS
        val rows = desktop.rows.takeIf { it > 0 } ?: DEFAULT_ROWS
        val firstPageRows = desktop.firstPageRows.takeIf { it > 0 } ?: rows
        val pages = paginate(entries, columns, firstPageRows, rows)
        return DesktopLayoutSnapshot(
            columns = columns,
            firstPageRows = firstPageRows,
            rowsPerPage = rows,
            pages = pages.ifEmpty { listOf(emptyList()) },
        )
    }

    /**
     * 把顺序条目按列数与行数切分为多页。
     *
     * 参数 entries 为完整条目顺序；columns 为列数；firstPageRows 为首页行数；
     * rowsPerPage 为其余页行数。列数或行数小于 1 时按 1 处理，
     * 条目为空时返回空列表，由调用方决定是否补一个空页。
     */
    fun paginate(
        entries: List<LauncherEntry>,
        columns: Int,
        firstPageRows: Int,
        rowsPerPage: Int,
    ): List<List<LauncherEntry>> {
        val safeColumns = columns.coerceAtLeast(1)
        val safeFirstRows = firstPageRows.coerceAtLeast(1)
        val safeRows = rowsPerPage.coerceAtLeast(1)
        val pages = mutableListOf<List<LauncherEntry>>()
        var index = 0
        var pageSize = safeColumns * safeFirstRows
        while (index < entries.size) {
            val end = (index + pageSize).coerceAtMost(entries.size)
            pages.add(entries.subList(index, end))
            index = end
            pageSize = safeColumns * safeRows
        }
        return pages
    }

    /**
     * 读取指定页可容纳的条目数。
     *
     * 参数 snapshot 为分页快照；pageIndex 为页下标，首页使用首页行数，
     * 其余页使用通用行数。行列无效时返回至少 1，避免除零与空页容量。
     */
    fun pageCapacity(
        snapshot: DesktopLayoutSnapshot,
        pageIndex: Int,
    ): Int {
        val rows = if (pageIndex == 0) snapshot.firstPageRows else snapshot.rowsPerPage
        return (snapshot.columns * rows.coerceAtLeast(1)).coerceAtLeast(1)
    }

    /** 把快照的每页条目换算为条目键，供美化模式装载页结构。 */
    fun keysOf(snapshot: DesktopLayoutSnapshot): List<List<String>> = snapshot.pages.map { page -> page.map { it.key } }

    /**
     * 用当前页的内存顺序替换快照对应页后展开为一维顺序。
     *
     * 参数 pages 为完整页结构，pageIndex 为当前页下标，workingKeys 为当前页顺序。
     * 页下标越界或页结构为空时直接使用 workingKeys，供预览与保存顺序共用。
     */
    fun mergeOrder(
        pages: List<List<String>>,
        pageIndex: Int,
        workingKeys: List<String>,
    ): List<String> {
        if (pages.isEmpty()) return workingKeys
        val merged =
            pages
                .mapIndexed { index, page ->
                    if (index == pageIndex) workingKeys else page
                }.flatten()
        return merged.ifEmpty { workingKeys }
    }

    /** 应用目录中的条目，按目录顺序排列，与桌面新建顺序一致。 */
    private fun appEntries(): List<LauncherEntry> =
        AppServices.catalog.apps.value.map { app ->
            LauncherEntry(
                key = app.packageName,
                type = EntryType.APP,
                label = app.label,
                packageName = app.packageName,
            )
        }
}
