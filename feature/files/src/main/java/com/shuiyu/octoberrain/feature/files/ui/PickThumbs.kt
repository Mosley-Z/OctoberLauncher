package com.shuiyu.octoberrain.feature.files.ui

import android.content.Context
import android.graphics.Bitmap
import com.shuiyu.octoberrain.feature.files.data.FileItem
import kotlinx.coroutines.CoroutineScope

/**
 * 选择页缩略图通道。
 *
 * 选择页在列表与网格之间切换时，缩略图目标长边与每页条目数同时变化，
 * 直接持有 [ThumbPrefetcher] 会在切换时写入错误尺寸的缓存实例。
 * 本类按目标尺寸与每页条目数维护唯一的预取器，尺寸一致时保持实例不变，
 * 尺寸变化时释放旧实例并按新参数重建，绘制阶段只查询内存缓存。
 *
 * 参数 context 为宿主上下文；scope 为选择页级协程作用域；
 * onReady 在当前页缩略图处理完毕后回调，用于请求整页重绘。
 */
internal class PickThumbs(
    context: Context,
    private val scope: CoroutineScope,
    private val onReady: () -> Unit,
) {
    private val appContext: Context = context.applicationContext
    private var prefetcher: ThumbPrefetcher? = null
    private var targetEdge: Int = 0
    private var pageSize: Int = 0

    /**
     * 按本次布局与视图形态对齐预取参数。
     *
     * 参数 edge 为缩略图目标长边像素；size 为每页条目数。
     * 两个参数与现有预取器一致时不重建，避免切换无关几何时丢弃已解码结果。
     */
    fun update(
        edge: Int,
        size: Int,
    ) {
        val normalizedEdge = edge.coerceAtLeast(1)
        val normalizedSize = size.coerceAtLeast(1)
        if (prefetcher != null && normalizedEdge == targetEdge && normalizedSize == pageSize) return
        prefetcher?.release()
        targetEdge = normalizedEdge
        pageSize = normalizedSize
        prefetcher =
            ThumbPrefetcher(
                context = appContext,
                scope = scope,
                targetEdge = normalizedEdge,
                pageSize = normalizedSize,
                onReady = onReady,
            )
    }

    /** 请求预取当前页与前后页，参数含全部条目与当前页编号。 */
    fun request(
        items: List<FileItem>,
        page: Int,
    ) {
        prefetcher?.request(items, page)
    }

    /** 取条目已就绪的缩略图，仅查询内存缓存，未命中或非图片返回 null。 */
    fun bitmapOf(item: FileItem): Bitmap? = prefetcher?.bitmapOf(item)

    /** 释放预取任务，选择页销毁时调用。 */
    fun release() {
        prefetcher?.release()
        prefetcher = null
        targetEdge = 0
        pageSize = 0
    }
}
