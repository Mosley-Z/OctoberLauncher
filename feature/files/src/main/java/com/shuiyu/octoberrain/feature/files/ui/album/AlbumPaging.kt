package com.shuiyu.octoberrain.feature.files.ui.album

import com.shuiyu.octoberrain.feature.files.data.MediaDay

/**
 * 一页中的一段日块。
 *
 * 参数 dayIndex 指向日分组下标；itemRange 为该段包含的条目下标区间（相对整个日分组，
 * 左闭右开），空区间表示当日没有照片；continued 为 true 时表示该段是同一天的续段，
 * 标题需要标注续段。
 */
internal data class AlbumBlock(
    val dayIndex: Int,
    val itemRange: IntRange,
    val continued: Boolean,
)

/**
 * 一页的分段规划。
 *
 * 参数 blocks 按展示顺序排列，长度不超过 [AlbumPaging.BLOCKS_PER_PAGE]。
 */
internal data class AlbumPage(
    val blocks: List<AlbumBlock>,
)

/**
 * 相册分页换算。
 *
 * 一天的照片较多时按每段九张拆成多段，避免超过九张的当日照片在时间轴中无法翻到；
 * 每页最多容纳三段，因此单页最多二十七张缩略图。换算均为纯函数，便于本地用例覆盖
 * 空相册、恰好九张、跨段、跨页与页码越界等边界。
 */
internal object AlbumPaging {
    /** 每段包含的缩略图数量，对应三行三列。 */
    const val ITEMS_PER_BLOCK: Int = 9

    /** 每页容纳的段数。 */
    const val BLOCKS_PER_PAGE: Int = 3

    /** 每页最多显示的缩略图数量。 */
    const val MAX_CELLS_PER_PAGE: Int = ITEMS_PER_BLOCK * BLOCKS_PER_PAGE

    /**
     * 把日分组换算为分页规划。
     *
     * 参数 days 为按时间倒序排列的日分组。返回的分页列表按展示顺序排列；没有任何
     * 日分组时返回空列表，由调用方按一页空相册处理。
     */
    fun pages(days: List<MediaDay>): List<AlbumPage> {
        val result = ArrayList<AlbumPage>()
        var current = ArrayList<AlbumBlock>(BLOCKS_PER_PAGE)

        fun flush() {
            if (current.isEmpty()) return
            result += AlbumPage(current)
            current = ArrayList(BLOCKS_PER_PAGE)
        }

        days.forEachIndexed { dayIndex, day ->
            val count = day.items.size
            if (count == 0) {
                current += AlbumBlock(dayIndex = dayIndex, itemRange = IntRange.EMPTY, continued = false)
                if (current.size == BLOCKS_PER_PAGE) flush()
                return@forEachIndexed
            }
            var start = 0
            while (start < count) {
                val end = minOf(count, start + ITEMS_PER_BLOCK)
                current +=
                    AlbumBlock(
                        dayIndex = dayIndex,
                        itemRange = start until end,
                        continued = start > 0,
                    )
                if (current.size == BLOCKS_PER_PAGE) flush()
                start = end
            }
        }
        flush()
        return result
    }

    /**
     * 计算总页数。
     *
     * 参数 days 为日分组列表；没有日分组时返回一页，供空相册显示占位说明。
     */
    fun pageCount(days: List<MediaDay>): Int = maxOf(1, pages(days).size)

    /**
     * 夹取页码。
     *
     * 参数 pageCount 为总页数，page 为请求页码；越界时返回首页或末页。
     */
    fun clampPage(
        pageCount: Int,
        page: Int,
    ): Int = page.coerceIn(0, maxOf(1, pageCount) - 1)

    /**
     * 取指定页的分段规划。
     *
     * 参数 pageCount 为总页数，page 为页码；页码越界时按夹取后的页码返回，仍无对应
     * 页面时返回空列表。
     */
    fun pageOf(
        days: List<MediaDay>,
        page: Int,
    ): List<AlbumBlock> {
        val list = pages(days)
        if (list.isEmpty()) return emptyList()
        val index = clampPage(list.size, page)
        return list[index].blocks
    }
}
