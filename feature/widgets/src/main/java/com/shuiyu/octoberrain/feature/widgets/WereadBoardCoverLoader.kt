package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.shuiyu.octoberrain.core.render.WereadCoverStore
import java.util.concurrent.Executors

/**
 * 封面位图缓存与按需解码。
 *
 * 只为可见的封面层提交解码，解码边长按组件宽度换算，结果按书目编号缓存；
 * 缓存以位图字节数计量，超出进程内存预算时先回收最久未用项。
 * 同一书目在解码完成前不重复提交，避免滑动时堆积任务。
 *
 * @param context 上下文，用于访问封面缓存目录
 * @param widthProvider 提供当前组件宽度
 * @param onChanged 位图就绪或需要重绘时调用
 */
internal class WereadBoardCoverLoader(
    private val context: Context,
    private val widthProvider: () -> Int,
    private val onChanged: () -> Unit,
) {
    /** 主线程消息队列，用于把解码结果与下载回调投回绘制线程。 */
    private val handler = Handler(Looper.getMainLooper())

    /** 已解码封面，按书目编号缓存。 */
    private val covers =
        object : LruCache<String, Bitmap>(cacheBytes()) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount

            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: Bitmap,
                newValue: Bitmap?,
            ) {
                if (!oldValue.isRecycled) oldValue.recycle()
            }
        }

    /** 正在解码的书目编号。 */
    private val requests = HashSet<String>()

    /** 单线程执行器，串行解码与下载。 */
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, LOADER_NAME) }

    /** 是否已释放，释放后到达的结果直接丢弃。 */
    @Volatile
    private var released: Boolean = false

    /**
     * 取已解码的封面。
     *
     * @param bookId 书目编号
     * @return 位图，尚未就绪时为空
     */
    fun bitmap(bookId: String): Bitmap? = covers.get(bookId)

    /**
     * 为可见封面层提交解码，不可见书目不解码。
     *
     * @param books 书目列表
     * @param slots 可见封面层
     */
    fun requestVisible(
        books: List<WereadBookCard>,
        slots: List<WereadCoverSlot>,
    ) {
        if (books.isEmpty() || released) return
        val target = (widthProvider() * COVER_TARGET_RATIO).toInt().coerceIn(MIN_COVER_PX, MAX_COVER_PX)
        slots.forEach { slot -> books.getOrNull(slot.index)?.let { submit(it, target) } }
    }

    /**
     * 提交单本书的解码任务，已缓存或解码中时跳过。
     *
     * @param book 书目
     * @param target 目标解码边长，单位像素
     */
    private fun submit(
        book: WereadBookCard,
        target: Int,
    ) {
        val bookId = book.bookId
        val path = book.coverPath ?: return
        if (covers.get(bookId) != null || !requests.add(bookId)) return
        executor.execute {
            val decoded =
                WereadCoverStore.bitmap(context, bookId, path, target) {
                    handler.post {
                        if (released) return@post
                        onChanged()
                    }
                }
            handler.post { deliver(bookId, decoded) }
        }
    }

    /**
     * 解码结果回投主线程：写入缓存并通知重绘。
     *
     * @param bookId 书目编号
     * @param bitmap 解码结果，未就绪时为空
     */
    private fun deliver(
        bookId: String,
        bitmap: Bitmap?,
    ) {
        requests.remove(bookId)
        if (released) {
            if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
            return
        }
        if (bitmap != null && !bitmap.isRecycled) covers.put(bookId, bitmap)
        onChanged()
    }

    /** 释放执行器与缓存，回收全部位图。 */
    fun release() {
        released = true
        executor.shutdownNow()
        covers.evictAll()
        requests.clear()
    }

    /** 封面缓存可用的字节数，按进程最大内存的固定比例分配。 */
    private fun cacheBytes(): Int =
        (Runtime.getRuntime().maxMemory() / COVER_CACHE_DIVISOR).toInt().coerceAtLeast(MIN_COVER_CACHE)

    /** 封面解码相关的常量。 */
    companion object {
        /** 解码边长相对组件宽度的比例。 */
        const val COVER_TARGET_RATIO: Float = 0.42f

        /** 解码最小边长。 */
        const val MIN_COVER_PX: Int = 96

        /** 解码最大边长。 */
        const val MAX_COVER_PX: Int = 640

        /** 缓存占用进程最大内存的比例分母。 */
        const val COVER_CACHE_DIVISOR: Int = 8

        /** 缓存的最小字节数。 */
        const val MIN_COVER_CACHE: Int = 1024 * 1024

        /** 解码线程名称。 */
        const val LOADER_NAME: String = "weread-board-cover"
    }
}
