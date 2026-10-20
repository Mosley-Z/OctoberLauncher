package com.shuiyu.octoberrain.feature.widgets.kernel

import com.shuiyu.octoberrain.core.common.WereadAppearance
import java.io.File

/**
 * 缓存微读外观设置，避免每次绘制重复解析偏好文件。
 *
 * 偏好文件包含书架与统计等大体积字符串，单个文件可达一百千字节以上，逐帧解析会占用主线程。
 * 本对象按文件修改时间与长度判断内容是否变化，未变化时直接复用上次解析结果；
 * 设置页写入新值后文件时间或长度改变，下一次读取即重新解析。
 */
internal object WereadAppearanceCache {
    /** 偏好文件名，与内核存储约定一致。 */
    private const val PREFS_FILE_NAME = "kernel_prefs.json"

    /** 上次读取的目录。 */
    private var cachedRoot: File? = null

    /** 上次读取时偏好文件的修改时间。 */
    private var cachedModified: Long = Long.MIN_VALUE

    /** 上次读取时偏好文件的长度。 */
    private var cachedSize: Long = -1L

    /** 上次解析得到的外观设置。 */
    private var cachedValue: WereadAppearance = WereadAppearance()

    /**
     * 读取指定目录下的外观设置，文件未变化时返回缓存值。
     *
     * @param root 内核数据目录，偏好文件位于该目录内。
     * @return 解析得到的外观设置，解析失败时返回默认值。
     */
    @Synchronized
    fun read(root: File): WereadAppearance {
        val file = File(root, PREFS_FILE_NAME)
        val modified = runCatching { file.lastModified() }.getOrDefault(0L)
        val size = runCatching { file.length() }.getOrDefault(0L)
        if (root == cachedRoot && modified == cachedModified && size == cachedSize) {
            return cachedValue
        }
        val value = runCatching { WereadAppearance.read(root) }.getOrDefault(WereadAppearance())
        cachedRoot = root
        cachedModified = modified
        cachedSize = size
        cachedValue = value
        return value
    }
}
