package com.shuiyu.octoberrain.feature.home

/**
 * 添加应用选择器的分页规则。
 *
 * 全部为纯函数，不依赖 Android 框架，便于对边界条件做 JVM 单元测试。
 * 选择集合由界面独立维护，与页码无关：翻页与搜索只影响当前页的候选切片。
 */
internal object PickerPagination {
    /** 单页可容纳的候选行数；可用高度不足一行时仍保留 1 行。 */
    fun pageSize(
        availableHeightPx: Int,
        rowHeightPx: Int,
    ): Int {
        if (availableHeightPx <= 0 || rowHeightPx <= 0) return 1
        return maxOf(1, availableHeightPx / rowHeightPx)
    }

    /** 候选总数对应的页数；空结果按 1 页展示，使页码文案保持「第 1/1 页」。 */
    fun pageCount(
        total: Int,
        pageSize: Int,
    ): Int {
        if (total <= 0 || pageSize <= 0) return 1
        return (total + pageSize - 1) / pageSize
    }

    /** 把页码夹紧到有效范围，越界值归入首页或末页。 */
    fun clampPage(
        page: Int,
        total: Int,
        pageSize: Int,
    ): Int {
        val last = pageCount(total, pageSize) - 1
        return page.coerceIn(0, maxOf(0, last))
    }

    /** 当前页的候选；页码越界或容量非法时返回空列表。 */
    fun slice(
        candidates: List<FolderCandidate>,
        page: Int,
        pageSize: Int,
    ): List<FolderCandidate> {
        val start = page.toLong() * pageSize
        val total = candidates.size.toLong()
        val end = minOf(start + pageSize, total)
        if (page < 0 || pageSize <= 0 || start >= total) return emptyList()
        return candidates.subList(start.toInt(), end.toInt()).toList()
    }
}
