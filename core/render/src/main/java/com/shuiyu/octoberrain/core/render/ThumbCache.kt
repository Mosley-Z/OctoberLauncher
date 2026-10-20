package com.shuiyu.octoberrain.core.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * 缩略图两级缓存：内存 LRU 与磁盘 PNG。
 *
 * 缓存键为来源路径、修改时间与目标长边的摘要（见 [ThumbKey]），文件改写或尺寸调整后
 * 旧键自然失效。磁盘目录由调用方注入，容量上限与目标长边可配置；写入前把位图量化为
 * 四档灰，PNG 对少量灰阶值压缩率高，还原时只需读回位图，不做二次抖动。抖动开关对应
 * 后续设置项「缩略图抖动」，关闭时按原始灰阶保存，默认开启。
 */
class ThumbCache(
    private val diskDir: File,
    private val maxDiskBytes: Long = DEFAULT_MAX_DISK_BYTES,
    memoryBytes: Int = DEFAULT_MEMORY_BYTES,
    private val targetEdge: Int = DEFAULT_TARGET_EDGE,
    private val ditherEnabled: Boolean = true,
) {
    /** 内存缓存，按位图字节数计容量。 */
    private val memory =
        object : LruCache<String, Bitmap>(memoryBytes) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }

    /** 本实例的磁盘索引，按来源摘要记录缓存键。 */
    private val index = ThumbIndex(diskDir, targetEdge, ditherEnabled)

    /** 统计计数。 */
    private var memoryHits = 0
    private var diskHits = 0
    private var misses = 0
    private var writes = 0

    /** 缓存统计快照。 */
    data class Stats(
        val memoryHits: Int,
        val diskHits: Int,
        val misses: Int,
        val writes: Int,
        val diskEntries: Int,
        val diskBytes: Long,
        val memoryBytes: Int,
        val targetEdge: Int,
    )

    /**
     * 读取缩略图。
     *
     * 参数 path 为源图片文件；内存命中直接返回，未命中则按摘要查找磁盘 PNG。
     * 两级均未命中返回 null，由调用方解码后写入 [put]。
     */
    @Synchronized
    fun get(path: File): Bitmap? {
        val key = ThumbKey.of(path.absolutePath, path.lastModified(), targetEdge)
        val cached = memory.get(key)
        if (cached != null) {
            memoryHits += 1
            return cached
        }
        val file = diskFile(key)
        val bitmap =
            if (file.isFile) {
                runCatching {
                    BitmapFactory.decodeFile(
                        file.absolutePath,
                        BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        },
                    )
                }.getOrNull()
            } else {
                null
            }
        if (bitmap != null) {
            diskHits += 1
            memory.put(key, bitmap)
        } else {
            misses += 1
        }
        return bitmap
    }

    /**
     * 仅查询内存缓存，命中返回位图，未命中返回 null 且不触碰磁盘。
     *
     * 供绘制线程使用：绘制阶段只取已就绪的位图，未命中时先画占位，解码与磁盘读取
     * 交给后台线程经 [loadOrDecode] 完成，避免首帧在绘制线程上读取文件。
     *
     * 参数 path 为源图片文件。
     */
    @Synchronized
    fun peekMemory(path: File): Bitmap? {
        val key = ThumbKey.of(path.absolutePath, path.lastModified(), targetEdge)
        val cached = memory.get(key) ?: return null
        memoryHits += 1
        return cached
    }

    /**
     * 写入缩略图并返回可直接绘制的位图。
     *
     * 参数 path 为源图片文件，bitmap 为已解码位图；抖动开启时先按四档量化再转 PNG，
     * 同时更新索引与磁盘容量。目录不可写时静默跳过磁盘写入，内存缓存仍然保留；
     * 返回值即缓存内的位图引用，调用方不得回收。
     */
    @Synchronized
    fun put(
        path: File,
        bitmap: Bitmap,
    ): Bitmap {
        val key = ThumbKey.of(path.absolutePath, path.lastModified(), targetEdge)
        val quantized = if (ditherEnabled) Ditherer.dither(bitmap, DitherCore.DEFAULT_LEVELS) else bitmap
        memory.put(key, quantized)
        val file = diskFile(key)
        val written =
            runCatching {
                if (!diskDir.isDirectory) diskDir.mkdirs()
                file.outputStream().use { stream ->
                    quantized.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }.getOrDefault(false)
        if (!written) return quantized
        writes += 1
        index.add(ThumbKey.pathDigest(path.absolutePath), key)
        index.save()
        trimDisk()
        return quantized
    }

    /**
     * 使某个来源文件的全部缓存失效，供文件写入或删除后调用。
     *
     * 参数 path 为源图片文件；内存与磁盘条目一并移除，索引同步更新。
     */
    @Synchronized
    fun invalidate(path: File) {
        val digest = ThumbKey.pathDigest(path.absolutePath)
        val keys = index.remove(digest)
        if (keys.isEmpty()) return
        for (key in keys) {
            memory.remove(key)
            diskFile(key).delete()
        }
        index.save()
    }

    /**
     * 预热缓存。
     *
     * 参数 paths 为待预热文件，decode 为未命中时的解码函数；返回本次实际解码并写入的条目数。
     */
    @Synchronized
    fun warmUp(
        paths: List<File>,
        decode: (File) -> Bitmap?,
    ): Int {
        var decoded = 0
        for (path in paths) {
            if (get(path) == null) {
                val bitmap = decode(path)
                if (bitmap != null) {
                    put(path, bitmap)
                    decoded += 1
                }
            }
        }
        return decoded
    }

    /** 返回当前统计快照，磁盘字节数按目录内 PNG 文件累计。 */
    @Synchronized
    fun stats(): Stats {
        val files = diskFiles()
        return Stats(
            memoryHits = memoryHits,
            diskHits = diskHits,
            misses = misses,
            writes = writes,
            diskEntries = files.size,
            diskBytes = files.sumOf { it.length() },
            memoryBytes = memory.size(),
            targetEdge = targetEdge,
        )
    }

    /**
     * 清空本实例的全部缓存。
     *
     * 内存条目逐出，磁盘 PNG 与索引文件删除；返回清理前的磁盘占用字节数。
     */
    @Synchronized
    fun clear(): Long {
        val bytes = diskFiles().sumOf { it.length() }
        memory.evictAll()
        diskFiles().forEach { it.delete() }
        index.reset()
        return bytes
    }

    /** 缓存文件路径，键本身即文件名。 */
    private fun diskFile(key: String): File = File(diskDir, "$key.png")

    /** 按最近修改时间清理，直到磁盘占用不超过上限。 */
    private fun trimDisk() {
        val files = diskFiles().sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        var index = 0
        while (index < files.size && total > maxDiskBytes) {
            val file = files[index]
            total -= file.length()
            file.delete()
            index += 1
        }
    }

    /** 列出磁盘目录内的 PNG 缓存文件，目录不存在时返回空列表。 */
    private fun diskFiles(): List<File> =
        diskDir.listFiles { file -> file.isFile && file.name.endsWith(".png") }?.toList()
            ?: emptyList()

    companion object {
        /** 默认磁盘上限，32 MB。 */
        const val DEFAULT_MAX_DISK_BYTES = 32L * 1024 * 1024

        /** 默认内存上限，8 MB。 */
        const val DEFAULT_MEMORY_BYTES = 8 * 1024 * 1024

        /** 默认目标长边。 */
        const val DEFAULT_TARGET_EDGE = 256
    }
}
