package com.shuiyu.octoberrain.feature.files.ui

import android.content.Context
import android.graphics.Bitmap
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.ThumbCache
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import com.shuiyu.octoberrain.feature.files.data.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 缩略图预取顺序的纯函数。
 *
 * 预取范围固定为当前页与前后各一页，当前页优先，保证翻页时目标页缩略图已就绪；
 * 结果以下标序列表示，便于在本地 JVM 单元测试中核对顺序与边界。
 */
internal object ThumbPrefetchPlan {
    /**
     * 取需要预取的页号。
     *
     * 参数 page 为当前页；pageCount 为总页数。结果按当前页、上一页、下一页排列，
     * 越界页被丢弃，空列表或非法页数返回空列表。
     */
    fun pagesOf(
        page: Int,
        pageCount: Int,
    ): List<Int> {
        if (pageCount <= 0) return emptyList()
        val current = page.coerceIn(0, pageCount - 1)
        val pages = LinkedHashSet<Int>()
        pages.add(current)
        if (current - 1 >= 0) pages.add(current - 1)
        if (current + 1 < pageCount) pages.add(current + 1)
        return pages.toList()
    }

    /**
     * 按预取顺序展开条目下标。
     *
     * 参数 page 为当前页；pageSize 为每页条目数；itemCount 为条目总数。
     * 每页只展开实际存在的条目，末页不足一页时不补位；非法参数返回空列表。
     */
    fun indicesOf(
        page: Int,
        pageSize: Int,
        itemCount: Int,
    ): List<Int> {
        if (pageSize <= 0 || itemCount <= 0) return emptyList()
        val pageCount = maxOf(1, (itemCount + pageSize - 1) / pageSize)
        val indices = ArrayList<Int>()
        for (target in pagesOf(page, pageCount)) {
            val start = target * pageSize
            val end = minOf(itemCount, start + pageSize)
            for (index in start until end) {
                indices.add(index)
            }
        }
        return indices
    }
}

/**
 * 缩略图预取器。
 *
 * 绘制阶段只查询内存缓存，未命中时绘制占位；后台线程按当前页与前后页顺序读取磁盘缓存
 * 或解码，写入 [ThumbCache]。当前页全部就绪后回调一次 [onReady]，由调用方整页重绘，
 * 避免在墨水屏上逐张刷新。目标长边由调用方按单元格短边给出，缓存实例统一从
 * [ThumbCacheRegistry] 取得，模块内不自行创建缓存。
 *
 * 参数 context 用于访问缓存目录；scope 为页面级协程作用域；targetEdge 为目标长边像素；
 * pageSize 为每页条目数；onReady 在当前页缩略图处理完毕后回调，用于请求整页重绘。
 */
internal class ThumbPrefetcher(
    context: Context,
    private val scope: CoroutineScope,
    private val targetEdge: Int,
    private val pageSize: Int,
    private val onReady: () -> Unit,
) {
    private val appContext: Context = context.applicationContext
    private val cache: ThumbCache = ThumbCacheRegistry.get(appContext, targetEdge)

    private var job: Job? = null
    private var generation = 0

    /** 取条目已就绪的缩略图，仅查内存缓存，不读磁盘；非图片或未命中返回 null。 */
    fun bitmapOf(item: FileItem): Bitmap? =
        if (item.isImage) {
            ThumbCacheRegistry.peekMemory(File(item.path), targetEdge)
        } else {
            null
        }

    /**
     * 请求预取当前页及前后页。
     *
     * 参数 items 为当前目录全部条目；page 为当前页。新请求会取消尚未完成的旧请求；
     * 每页在后台线程顺序处理，当前页处理完毕后回调一次 [onReady]，其余页只写入缓存。
     */
    fun request(
        items: List<FileItem>,
        page: Int,
    ) {
        job?.cancel()
        generation += 1
        val token = generation
        val pageCount = maxOf(1, (items.size + pageSize - 1) / pageSize)
        val pages = ThumbPrefetchPlan.pagesOf(page, pageCount)
        if (pages.isEmpty() || items.isEmpty()) return
        job =
            scope.launch {
                pages.forEachIndexed { position, target ->
                    val start = target * pageSize
                    val end = minOf(items.size, start + pageSize)
                    for (index in start until end) {
                        if (!isActive || token != generation) return@launch
                        val item = items.getOrNull(index)
                        if (item != null && item.isImage) {
                            withContext(Dispatchers.IO) { load(item) }
                        }
                    }
                    if (position == 0 && isActive && token == generation) onReady()
                }
            }
    }

    /** 丢弃某路径的内存条目与磁盘缓存，供文件写入或删除后调用。 */
    fun invalidate(path: String) {
        ThumbCacheRegistry.invalidate(appContext, File(path))
    }

    /** 释放预取任务，页面销毁或重建时调用。 */
    fun release() {
        generation += 1
        job?.cancel()
        job = null
    }

    /** 从两级缓存读取，未命中时解码并写回缓存；只在后台线程调用。 */
    private fun load(item: FileItem): Bitmap? {
        val file = File(item.path)
        return cache.get(file) ?: ImageDecoder.decode(item.path, targetEdge)?.let { cache.put(file, it) }
    }
}
