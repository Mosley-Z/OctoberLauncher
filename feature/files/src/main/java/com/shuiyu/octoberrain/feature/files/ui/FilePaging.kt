package com.shuiyu.octoberrain.feature.files.ui

import kotlin.math.abs

/**
 * 分页交互常量与纯函数。
 *
 * 墨水屏不做惯性滚动与长列表滚动：一屏固定一页，翻页由底栏按钮、上下滑与音量键完成。
 * 本对象只保存阈值与页码换算，供分页视图与单元测试共用，不依赖 Android 视图。
 */
object FilePaging {
    /** 长按判定时长，与既有阅读应用的操作习惯保持一致。 */
    const val LONG_PRESS_MS = 350L

    /** 上下滑翻页的最小位移，单位 dp。 */
    const val SWIPE_THRESHOLD_DP = 48f

    /**
     * 计算总页数。
     *
     * 参数 itemCount 为条目总数；pageSize 为每页条目数。
     * 任一参数非正时返回 1，保证空列表也有一页可显示。
     */
    fun pageCount(
        itemCount: Int,
        pageSize: Int,
    ): Int {
        if (itemCount <= 0 || pageSize <= 0) return 1
        return maxOf(1, (itemCount + pageSize - 1) / pageSize)
    }

    /** 把页码夹取到合法范围，pageCount 至少为 1。 */
    fun clampPage(
        page: Int,
        pageCount: Int,
    ): Int = page.coerceIn(0, maxOf(0, pageCount - 1))

    /** 是否为上下滑翻页手势，位移需以纵向为主且超过阈值。 */
    fun isVerticalSwipe(
        dx: Int,
        dy: Int,
        thresholdPx: Int,
    ): Boolean = abs(dy) > abs(dx) && abs(dy) > thresholdPx

    /** 是否为点击手势，两个方向的位移都不超过触摸容差。 */
    fun isTap(
        dx: Int,
        dy: Int,
        slopPx: Int,
    ): Boolean = abs(dx) <= slopPx && abs(dy) <= slopPx
}

/**
 * 分页状态。
 *
 * 保存条目总数、每页条目数与当前页，页码在数据量变化时自动夹取到合法范围。
 * 本类不持有视图，可在本地 JVM 单元测试中直接构造。
 *
 * 参数 pageSize 为每页条目数，非正值按 1 处理。
 */
class FilePagerState(
    pageSize: Int,
) {
    /** 每页条目数，数据量变化后可重新设置。 */
    var pageSize: Int = pageSize.coerceAtLeast(1)
        set(value) {
            field = value.coerceAtLeast(1)
            page = FilePaging.clampPage(page, pageCount)
        }

    /** 条目总数。 */
    var itemCount: Int = 0
        private set

    /** 当前页编号，从 0 开始。 */
    var page: Int = 0
        private set

    /** 总页数，至少为 1。 */
    val pageCount: Int get() = FilePaging.pageCount(itemCount, pageSize)

    /** 当前页首条目下标。 */
    val firstIndex: Int get() = page * pageSize

    /** 当前页末条目下标，不含。 */
    val lastIndex: Int get() = minOf(itemCount, firstIndex + pageSize)

    /** 更新条目总数，页码越界时回退到末页。 */
    fun updateItemCount(count: Int) {
        itemCount = count.coerceAtLeast(0)
        page = FilePaging.clampPage(page, pageCount)
    }

    /** 跳到指定页，越界自动收敛。 */
    fun setPage(target: Int) {
        page = FilePaging.clampPage(target, pageCount)
    }

    /** 翻到下一页，已在末页时无动作。 */
    fun nextPage() {
        setPage(page + 1)
    }

    /** 翻到上一页，已在首页时无动作。 */
    fun prevPage() {
        setPage(page - 1)
    }
}

/**
 * 触控命中块。
 *
 * 自绘界面没有现成控件，命中区域在布局时登记、在触摸时反查；
 * 后登记的块压在先登记的块之上，与绘制顺序一致。
 *
 * 参数 id 为调用方定义的编号；left、top、right、bottom 为屏幕坐标。
 */
data class Hit(
    val id: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    /** 判断坐标是否落在本命中块内，右边界与下边界不包含。 */
    fun contains(
        x: Int,
        y: Int,
    ): Boolean = x >= left && x < right && y >= top && y < bottom
}
