package com.shuiyu.octoberrain.core.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
    private val memory: ThumbMemory = ThumbMemory(memoryBytes),
) {
    /** 已知的来源键由后台读取更新，绘制阶段不调用 lastModified。 */
    private val knownKeys = ConcurrentHashMap<String, String>()
    private val namespace = "${diskDir.absolutePath}:$targetEdge:$ditherEnabled:"
    private var writesSinceTrim = 0

    /** 本实例的磁盘索引，按来源摘要记录缓存键。 */
    private val index = ThumbIndex(diskDir, targetEdge, ditherEnabled)

    /** 统计计数。 */
    private val memoryHits = AtomicInteger()
    private val diskHits = AtomicInteger()
    private val misses = AtomicInteger()
    private val writes = AtomicInteger()

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
    fun get(path: File): Bitmap? {
        val key = ThumbKey.of(path.absolutePath, path.lastModified(), targetEdge)
        knownKeys[path.absolutePath] = key
        val cached = memory.get(namespace + key)
        if (cached != null) {
            memoryHits.incrementAndGet()
            return cached
        }
        val bitmap =
            synchronized(DISK_LOCK) {
                val file = diskFile(key)

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
            }
        if (bitmap != null) {
            diskHits.incrementAndGet()
            memory.put(namespace + key, bitmap)
        } else {
            misses.incrementAndGet()
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
    fun peekMemory(path: File): Bitmap? {
        val key = knownKeys[path.absolutePath] ?: return null
        return memory.get(namespace + key)?.also { memoryHits.incrementAndGet() }
    }

    /**
     * 写入缩略图并返回可直接绘制的位图。
     *
     * 参数 path 为源图片文件，bitmap 为已解码位图；抖动开启时先按四档量化再转 PNG，
     * 同时更新索引与磁盘容量。目录不可写时静默跳过磁盘写入，内存缓存仍然保留；
     * 返回值即缓存内的位图引用，调用方不得回收。
     */
    fun put(
        path: File,
        bitmap: Bitmap,
    ): Bitmap {
        val key = ThumbKey.of(path.absolutePath, path.lastModified(), targetEdge)
        knownKeys[path.absolutePath] = key
        val quantized = if (ditherEnabled) Ditherer.dither(bitmap, DitherCore.DEFAULT_LEVELS) else bitmap
        memory.put(namespace + key, quantized)
        synchronized(DISK_LOCK) {
            val file = diskFile(key)
            val written =
                runCatching {
                    if (!diskDir.isDirectory) diskDir.mkdirs()
                    file.outputStream().use { stream ->
                        quantized.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                }.getOrDefault(false)
            if (!written) return quantized
            writes.incrementAndGet()
            index.add(ThumbKey.pathDigest(path.absolutePath), key)
            index.save()
            writesSinceTrim++
            if (writesSinceTrim >= TRIM_BATCH || writes.get() == 1) {
                trimDisk()
                writesSinceTrim = 0
            }
        }
        return quantized
    }

    /**
     * 使某个来源文件的全部缓存失效，供文件写入或删除后调用。
     *
     * 参数 path 为源图片文件；内存与磁盘条目一并移除，索引同步更新。
     */
    fun invalidate(path: File) {
        knownKeys.remove(path.absolutePath)?.let { memory.remove(namespace + it) }
        synchronized(DISK_LOCK) {
            val digest = ThumbKey.pathDigest(path.absolutePath)
            val keys = index.remove(digest)
            if (keys.isEmpty()) return
            for (key in keys) {
                memory.remove(namespace + key)
                diskFile(key).delete()
            }
            index.save()
        }
    }

    /**
     * 预热缓存。
     *
     * 参数 paths 为待预热文件，decode 为未命中时的解码函数；返回本次实际解码并写入的条目数。
     */
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
    fun stats(): Stats =
        synchronized(DISK_LOCK) {
            val files = diskFiles()
            Stats(
                memoryHits = memoryHits.get(),
                diskHits = diskHits.get(),
                misses = misses.get(),
                writes = writes.get(),
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
    fun clear(): Long =
        synchronized(DISK_LOCK) {
            val bytes = diskFiles().sumOf { it.length() }
            knownKeys.values.forEach { memory.remove(namespace + it) }
            knownKeys.clear()
            diskFiles().forEach { it.delete() }
            index.reset()
            bytes
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
        private val DISK_LOCK = Any()
        private const val TRIM_BATCH = 16

        /** 默认磁盘上限，32 MB。 */
        const val DEFAULT_MAX_DISK_BYTES = 32L * 1024 * 1024

        /** 默认内存上限，8 MB。 */
        const val DEFAULT_MEMORY_BYTES = 8 * 1024 * 1024

        /** 默认目标长边。 */
        const val DEFAULT_TARGET_EDGE = 256
    }
}
