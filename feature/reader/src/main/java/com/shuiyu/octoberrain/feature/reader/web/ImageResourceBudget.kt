package com.shuiyu.octoberrain.feature.reader.web

import java.util.concurrent.atomic.AtomicLong

/** 单文档图片总预算，重复加载也计入；不以压缩大小替代解码像素预算。 */
internal class ImageResourceBudget {
    private val bytes = AtomicLong()
    private val pixels = AtomicLong()

    fun acceptBytes(size: Long): Boolean = size in 0..8 * 1024 * 1024L && bytes.addAndGet(size) <= 32 * 1024 * 1024L

    fun acceptPixels(
        width: Int,
        height: Int,
    ): Boolean {
        if (width <= 0 || height <= 0) return false
        val size = width.toLong() * height
        return size <= 8_000_000 && pixels.addAndGet(size) <= 16_000_000
    }

    companion object {
        fun mime(name: String): String? =
            when (name.substringAfterLast('.').lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> null
            }
    }
}
