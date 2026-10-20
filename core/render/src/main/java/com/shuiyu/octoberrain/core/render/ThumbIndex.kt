package com.shuiyu.octoberrain.core.render

import java.io.File

/**
 * 单个缩略图缓存实例的磁盘索引。
 *
 * 同一磁盘目录可能服务多个缓存实例（目标长边或抖动开关不同），索引文件按实例区分，
 * 文件名形如 `index-<目标长边>-dither<0|1>.txt`。每个实例只读写自身文件，避免实例之间
 * 互相覆盖条目；PNG 文件名本身带内容摘要，不受索引隔离影响。
 */
internal class ThumbIndex(
    private val directory: File,
    targetEdge: Int,
    ditherEnabled: Boolean,
) {
    /** 索引文件路径。 */
    private val file: File = File(directory, fileName(targetEdge, ditherEnabled))

    /** 来源摘要到缓存键的映射。 */
    private val byPath = mutableMapOf<String, MutableSet<String>>()

    /** 是否已从磁盘载入。 */
    private var loaded = false

    /** 返回索引文件路径，供测试与诊断使用。 */
    fun file(): File = file

    /**
     * 记录一个缓存键。
     *
     * 参数 digest 为来源路径摘要，key 为缓存键；重复记录同一键不会产生重复条目。
     */
    fun add(
        digest: String,
        key: String,
    ) {
        load()
        byPath.getOrPut(digest) { mutableSetOf() }.add(key)
    }

    /**
     * 移除一个来源摘要及其全部键。
     *
     * 返回被移除的键集合；未记录时返回空集合。
     */
    fun remove(digest: String): Set<String> {
        load()
        return byPath.remove(digest) ?: emptySet()
    }

    /** 返回来源摘要已记录的键集合，未记录时为空集合。 */
    fun keys(digest: String): Set<String> {
        load()
        return byPath[digest]?.toSet() ?: emptySet()
    }

    /** 从磁盘载入索引，每个来源摘要一行，格式为「摘要 键」。 */
    fun load() {
        if (loaded) return
        loaded = true
        if (!file.isFile) return
        runCatching {
            file.readLines().forEach { line ->
                val parts = line.split(' ')
                if (parts.size == 2) {
                    byPath.getOrPut(parts[0]) { mutableSetOf() }.add(parts[1])
                }
            }
        }
    }

    /** 清空内存索引并删除索引文件。 */
    fun reset() {
        byPath.clear()
        loaded = true
        runCatching { file.delete() }
    }

    /** 把索引写回本实例的索引文件。 */
    fun save() {
        runCatching {
            if (!directory.isDirectory) directory.mkdirs()
            file.writeText(
                byPath.entries.joinToString("\n") { (digest, keys) ->
                    keys.joinToString("\n") { "$digest $it" }
                },
            )
        }
    }

    companion object {
        /** 索引文件名，目标长边与抖动开关决定实例归属。 */
        fun fileName(
            targetEdge: Int,
            ditherEnabled: Boolean,
        ): String = "index-$targetEdge-dither${if (ditherEnabled) 1 else 0}.txt"
    }
}
