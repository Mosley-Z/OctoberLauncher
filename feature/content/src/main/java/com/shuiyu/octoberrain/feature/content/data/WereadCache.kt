package com.shuiyu.octoberrain.feature.content.data

import java.io.File

/**
 * 微信读书统计结果的两级缓存。
 *
 * 键由统计周期与周期起点组成；内存保存最近读取的响应文本，磁盘目录保存
 * 相同内容以便进程重启后复用。磁盘最多保留 [maxPeriods] 个周期，超出时按
 * 周期起点从旧到新淘汰。
 */
internal class WereadCache(
    private val directory: File,
    private val maxPeriods: Int = 6,
) {
    private val memory = LinkedHashMap<String, String>()

    /** 读取缓存，内存未命中时回落到磁盘；均未命中返回 null。 */
    fun get(
        mode: String,
        periodStartSeconds: Long,
    ): String? = memory[keyOf(mode, periodStartSeconds)] ?: readFromDisk(mode, periodStartSeconds)

    /** 写入内存与磁盘，并触发周期淘汰。 */
    fun put(
        mode: String,
        periodStartSeconds: Long,
        payload: String,
    ) {
        memory[keyOf(mode, periodStartSeconds)] = payload
        runCatching {
            directory.mkdirs()
            fileOf(mode, periodStartSeconds).writeText(payload)
        }
        evict()
    }

    /** 清除指定周期的全部缓存，含内存与磁盘。 */
    fun invalidate(mode: String) {
        val memoryPrefix = "$mode:"
        memory.keys.filter { it.startsWith(memoryPrefix) }.forEach(memory::remove)
        runCatching {
            listFiles()
                .filter { it.name.startsWith("${mode}_") }
                .forEach { it.delete() }
        }
    }

    /** 清除全部缓存，含内存与磁盘。 */
    fun invalidate() {
        memory.clear()
        runCatching { listFiles().forEach { it.delete() } }
    }

    /** 返回磁盘上现存周期起点的升序列表，供测试与诊断使用。 */
    fun periods(): List<Long> = listFiles().mapNotNull { startOf(it.name) }.sorted()

    /** 二级读取：命中磁盘时同步写入内存。 */
    private fun readFromDisk(
        mode: String,
        periodStartSeconds: Long,
    ): String? {
        val text =
            fileOf(
                mode,
                periodStartSeconds,
            ).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
        if (text != null) memory[keyOf(mode, periodStartSeconds)] = text
        return text
    }

    /** 按周期起点从旧到新淘汰超出上限的磁盘与内存条目。 */
    private fun evict() {
        val files = listFiles()
        if (files.isEmpty()) return
        val kept = files.sortedBy { startOf(it.name) ?: Long.MAX_VALUE }.takeLast(maxPeriods).toSet()
        files.filterNot { it in kept }.forEach { it.delete() }
        val keptStarts = kept.mapNotNull { startOf(it.name) }.toSet()
        memory.keys
            .filter { key -> startOfKey(key) !in keptStarts }
            .forEach(memory::remove)
    }

    /** 列出缓存目录中的 JSON 文件，目录不可读时返回空表。 */
    private fun listFiles(): List<File> =
        runCatching { directory.listFiles()?.filter { it.isFile && it.name.endsWith(".json") } }
            .getOrNull()
            .orEmpty()

    /** 返回缓存文件路径。 */
    private fun fileOf(
        mode: String,
        periodStartSeconds: Long,
    ): File = File(directory, "${mode}_$periodStartSeconds.json")
}

/** 由周期与起点拼接缓存键。 */
private fun keyOf(
    mode: String,
    periodStartSeconds: Long,
): String = "$mode:$periodStartSeconds"

/** 由缓存键还原周期起点，无法解析时返回 -1。 */
private fun startOfKey(key: String): Long = key.substringAfter(':').toLongOrNull() ?: -1L

/** 从文件名解析周期起点，无法解析时返回 null。 */
private fun startOf(name: String): Long? = name.removeSuffix(".json").substringAfter('_').toLongOrNull()
