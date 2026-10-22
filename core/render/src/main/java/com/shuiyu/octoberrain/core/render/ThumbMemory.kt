package com.shuiyu.octoberrain.core.render

import android.graphics.Bitmap

/** 多尺寸缩略图共享的字节预算；所有操作只访问内存，不回收仍可能由视图持有的位图。 */
class ThumbMemory(
    bytes: Int,
) {
    private val cache = ByteBudgetCache<Bitmap>(bytes) { it.byteCount }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(
        key: String,
        bitmap: Bitmap,
    ) {
        cache.put(key, bitmap)
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun resize(bytes: Int) {
        cache.resize(bytes.coerceAtLeast(1))
    }

    fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size()
}
