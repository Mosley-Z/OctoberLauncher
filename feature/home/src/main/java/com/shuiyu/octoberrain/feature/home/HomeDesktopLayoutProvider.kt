package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopLayoutProvider
import com.shuiyu.octoberrain.core.common.DesktopLayoutSnapshot
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.device.DeviceRuntime
import kotlin.math.min

/**
 * 桌面布局快照服务。
 *
 * 美化模式需要与桌面真实排布一致的分页结果，而布局算法只存在于本模块。
 * 桌面视图每次完成条目构建与版面计算后调用 [update] 写入最新快照；调用方通过
 * [snapshot] 读取。桌面视图尚未创建时，按设备画像与当前配置即时计算一次作为兜底。
 */
internal object HomeDesktopLayoutProvider : DesktopLayoutProvider {
    /** 最近一次由桌面视图写入的快照；未写入时为空，读取时走兜底计算。 */
    @Volatile
    private var cached: DesktopLayoutSnapshot? = null

    /** 读取快照：优先返回桌面视图写入的最新结果，缺失时即时推算。 */
    override fun snapshot(): DesktopLayoutSnapshot = cached ?: fallback()

    /**
     * 写入最新快照。
     *
     * 参数 metrics 为桌面当前版面度量，参数 entries 为桌面当前条目顺序；
     * 两者共同决定分页与每页容量。
     */
    fun update(
        metrics: DesktopMetrics,
        entries: List<LauncherEntry>,
    ) {
        cached = build(metrics, entries)
    }

    /**
     * 兜底计算。
     *
     * 桌面前台视图不存在时，按设备画像的屏幕尺寸扣除状态栏高度作为可用高度，
     * 用同一套度量与条目构建逻辑推算快照；任一环节失败时返回仅含一页的空快照。
     */
    private fun fallback(): DesktopLayoutSnapshot =
        runCatching {
            val config = AppServices.config.state.value
            val apps = AppServices.catalog.apps.value
            val entries = DesktopEntries.build(apps, config.desktop)
            val tokens = DesignRuntime.tokens
            val width = DeviceRuntime.profile.widthPx.coerceAtLeast(1)
            val height = (DeviceRuntime.profile.heightPx - tokens.statusBarHeight).coerceAtLeast(1)
            build(DesktopMetrics.compute(width, height, config), entries)
        }.getOrElse {
            DesktopLayoutSnapshot(
                columns = 1,
                firstPageRows = 1,
                rowsPerPage = 1,
                pages = emptyList(),
            )
        }

    /**
     * 按桌面绘制公式把条目切分为页。
     *
     * 首页容量为列数乘首页行数，其余页容量为列数乘常规行数；首页组件槽不占条目，
     * 只通过首页行数体现。页数同时受配置上限与条目数量限制，条目为空时返回空页列表。
     */
    private fun build(
        metrics: DesktopMetrics,
        entries: List<LauncherEntry>,
    ): DesktopLayoutSnapshot {
        val columns = if (metrics.isList) 1 else metrics.columns.coerceAtLeast(1)
        val firstRows = metrics.rowsOf(0).coerceAtLeast(1)
        val rowsPerPage = metrics.fullRows.coerceAtLeast(1)
        val firstCapacity = metrics.capacity(0).coerceAtLeast(1)
        val perPage = (columns * rowsPerPage).coerceAtLeast(1)
        val pages = ArrayList<List<LauncherEntry>>()
        val limit = metrics.pageLimit.coerceAtLeast(1)
        var index = 0
        while (pages.size < limit && index < entries.size) {
            val capacity = if (pages.isEmpty()) firstCapacity else perPage
            val end = min(index + capacity, entries.size)
            pages.add(entries.subList(index, end).toList())
            index = end
        }
        return DesktopLayoutSnapshot(
            columns = columns,
            firstPageRows = firstRows,
            rowsPerPage = rowsPerPage,
            pages = pages,
        )
    }
}
