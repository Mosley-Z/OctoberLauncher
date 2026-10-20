package com.shuiyu.octoberrain.core.render

/**
 * 缩略图质量档位与缓存参数的换算。
 *
 * 档位取值与配置中的 `AppearanceConfig.thumbnailQuality` 对应：0 省内存、1 平衡、2 清晰。
 * 每个档位给出默认目标长边、内存预算与磁盘预算；调用方按目标长边显式取图时，
 * 仅预算参数按档位生效，目标长边沿用调用方传入的值。
 */
object ThumbProfile {
    /** 省内存档。 */
    const val QUALITY_LOW: Int = 0

    /** 平衡档，默认。 */
    const val QUALITY_BALANCED: Int = 1

    /** 清晰档。 */
    const val QUALITY_SHARP: Int = 2

    /** 默认档位。 */
    const val DEFAULT_QUALITY: Int = QUALITY_BALANCED

    /** 省内存档的目标长边。 */
    const val LOW_TARGET_EDGE: Int = 192

    /** 平衡档的目标长边，与既有默认值一致。 */
    const val BALANCED_TARGET_EDGE: Int = ThumbCache.DEFAULT_TARGET_EDGE

    /** 清晰档的目标长边。 */
    const val SHARP_TARGET_EDGE: Int = 384

    /** 省内存档的内存预算，4 MB。 */
    const val LOW_MEMORY_BYTES: Int = 4 * 1024 * 1024

    /** 平衡档的内存预算，与既有默认值一致。 */
    const val BALANCED_MEMORY_BYTES: Int = ThumbCache.DEFAULT_MEMORY_BYTES

    /** 清晰档的内存预算，12 MB。 */
    const val SHARP_MEMORY_BYTES: Int = 12 * 1024 * 1024

    /** 省内存档的磁盘预算，16 MB。 */
    const val LOW_DISK_BYTES: Long = 16L * 1024 * 1024

    /** 平衡档的磁盘预算，与既有默认值一致。 */
    const val BALANCED_DISK_BYTES: Long = ThumbCache.DEFAULT_MAX_DISK_BYTES

    /** 清晰档的磁盘预算，64 MB。 */
    const val SHARP_DISK_BYTES: Long = 64L * 1024 * 1024

    /** 有效档位，越界值回退默认档。 */
    fun normalize(quality: Int): Int = if (quality in QUALITY_LOW..QUALITY_SHARP) quality else DEFAULT_QUALITY

    /** 档位对应的默认目标长边。 */
    fun targetEdge(quality: Int): Int =
        when (normalize(quality)) {
            QUALITY_LOW -> LOW_TARGET_EDGE
            QUALITY_SHARP -> SHARP_TARGET_EDGE
            else -> BALANCED_TARGET_EDGE
        }

    /** 档位对应的内存预算，单位字节。 */
    fun memoryBytes(quality: Int): Int =
        when (normalize(quality)) {
            QUALITY_LOW -> LOW_MEMORY_BYTES
            QUALITY_SHARP -> SHARP_MEMORY_BYTES
            else -> BALANCED_MEMORY_BYTES
        }

    /** 档位对应的磁盘预算，单位字节。 */
    fun diskBytes(quality: Int): Long =
        when (normalize(quality)) {
            QUALITY_LOW -> LOW_DISK_BYTES
            QUALITY_SHARP -> SHARP_DISK_BYTES
            else -> BALANCED_DISK_BYTES
        }

    /** 档位对应的显示文本。 */
    fun label(quality: Int): String =
        when (normalize(quality)) {
            QUALITY_LOW -> "省内存"
            QUALITY_SHARP -> "清晰"
            else -> "平衡"
        }
}
