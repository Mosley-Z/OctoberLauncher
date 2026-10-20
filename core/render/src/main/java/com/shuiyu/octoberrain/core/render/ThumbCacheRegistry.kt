package com.shuiyu.octoberrain.core.render

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 缩略图缓存的进程内入口。
 *
 * 同一进程内按目标长边与抖动开关共享缓存实例，磁盘目录固定为 cacheDir/thumbs。
 * 接入方通过 [get] 取得实例，文件写入或删除后经 [invalidate] 清理对应条目；
 * 缓存实例不再由调用方自行创建，避免同一进程内出现多份磁盘索引。
 *
 * 质量档位与抖动开关由 [configure] 注入，取值来自配置中的缩略图设置：
 * 质量档位决定内存与磁盘预算以及默认目标长边，抖动开关决定写入前的量化方式。
 */
object ThumbCacheRegistry {
    /** 缓存目录名，位于应用私有 cacheDir 下。 */
    private const val DIRECTORY_NAME = "thumbs"

    /** 已创建的缓存实例，键为目标长边与抖动开关的组合。 */
    private val caches = ConcurrentHashMap<CacheKey, ThumbCache>()

    /** 当前质量档位。 */
    @Volatile
    private var quality: Int = ThumbProfile.DEFAULT_QUALITY

    /** 当前抖动开关。 */
    @Volatile
    private var ditherEnabled: Boolean = true

    /**
     * 缓存实例键。
     *
     * 目标长边决定采样倍率，抖动开关决定写入前的量化方式，二者都参与键的构成，
     * 避免同一路径在不同参数下读到不符合调用方预期的位图。
     */
    private data class CacheKey(
        val targetEdge: Int,
        val dither: Boolean,
    )

    /**
     * 应用缩略图设置。
     *
     * 参数 quality 为质量档位，越界值按默认档处理；dither 为抖动开关。
     * 质量变化会重建后续实例的预算参数，已创建的实例保留原预算直到被清空。
     */
    fun configure(
        quality: Int,
        dither: Boolean,
    ) {
        this.quality = ThumbProfile.normalize(quality)
        this.ditherEnabled = dither
    }

    /** 返回当前质量档位。 */
    fun currentQuality(): Int = quality

    /** 返回当前抖动开关。 */
    fun currentDither(): Boolean = ditherEnabled

    /**
     * 取得缓存实例。
     *
     * 参数 context 用于定位 cacheDir；targetEdge 为目标长边像素，非正数时按当前质量档位
     * 计算默认长边，正数时沿用调用方传入的值；dither 为缩略图是否先量化为四档灰，
     * 为空时取当前配置，关闭时保留解码后的原始灰阶。内存与磁盘预算按质量档位换算，
     * 首次调用创建实例，此后相同参数复用同一实例。
     */
    fun get(
        context: Context,
        targetEdge: Int = 0,
        dither: Boolean? = null,
    ): ThumbCache {
        val edge = if (targetEdge > 0) targetEdge else ThumbProfile.targetEdge(quality)
        val useDither = dither ?: ditherEnabled
        val key = CacheKey(edge, useDither)
        return caches.getOrPut(key) {
            ThumbCache(
                diskDir = File(context.applicationContext.cacheDir, DIRECTORY_NAME),
                maxDiskBytes = ThumbProfile.diskBytes(quality),
                memoryBytes = ThumbProfile.memoryBytes(quality),
                targetEdge = edge,
                ditherEnabled = useDither,
            )
        }
    }

    /**
     * 仅查询已创建实例的内存缓存，实例尚未创建或未命中时返回 null。
     *
     * 绘制线程使用本方法避免磁盘读取：未命中时先画占位，再在后台线程经
     * `ThumbCache.loadOrDecode` 补齐。参数与 [get] 保持一致，不含上下文，
     * 因为本方法不创建缓存实例、不访问文件系统。
     */
    fun peekMemory(
        path: File,
        targetEdge: Int = 0,
        dither: Boolean? = null,
    ): Bitmap? {
        val edge = if (targetEdge > 0) targetEdge else ThumbProfile.targetEdge(quality)
        val useDither = dither ?: ditherEnabled
        return caches[CacheKey(edge, useDither)]?.peekMemory(path)
    }

    /**
     * 使指定来源文件的全部缓存失效。
     *
     * 参数 path 为源图片文件；已创建的全部实例都会清理该路径对应的条目。
     * 退出应用或切换上下文时无需调用，缓存目录随应用数据一同保留。
     */
    @Suppress("UNUSED_PARAMETER")
    fun invalidate(
        context: Context,
        path: File,
    ) {
        if (caches.isEmpty()) return
        for (cache in caches.values) {
            cache.invalidate(path)
        }
    }

    /** 返回缩略图磁盘缓存的当前占用字节数，目录不存在时为 0。 */
    fun diskBytes(context: Context): Long {
        val dir = File(context.applicationContext.cacheDir, DIRECTORY_NAME)
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".png") } ?: return 0L
        return files.sumOf { it.length() }
    }

    /**
     * 清空全部缩略图缓存。
     *
     * 参数 context 用于定位缓存目录；内存条目与磁盘文件一并清理，返回清理前的磁盘字节数。
     */
    fun clearAll(context: Context): Long {
        var bytes = 0L
        for (cache in caches.values) {
            bytes += cache.clear()
        }
        val dir = File(context.applicationContext.cacheDir, DIRECTORY_NAME)
        val files = dir.listFiles { file -> file.isFile } ?: return bytes
        for (file in files) {
            if (file.name.endsWith(".png")) {
                bytes += file.length()
            }
            file.delete()
        }
        return bytes
    }
}
