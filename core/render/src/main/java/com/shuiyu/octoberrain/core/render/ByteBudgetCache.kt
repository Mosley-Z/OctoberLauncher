package com.shuiyu.octoberrain.core.render

/** 仅锁住内存读写的 LRU，总预算可即时缩小；逐出不销毁调用方仍持有的值。 */
internal class ByteBudgetCache<V>(
    bytes: Int,
    private val sizeOf: (V) -> Int,
) {
    private val values = LinkedHashMap<String, V>(16, 0.75f, true)
    private var limit = bytes.coerceAtLeast(1)
    private var used = 0

    @Synchronized fun get(key: String): V? = values[key]

    @Synchronized fun put(
        key: String,
        value: V,
    ) {
        values.remove(key)?.let { used -= sizeOf(it) }
        values[key] = value
        used += sizeOf(value)
        trim()
    }

    @Synchronized fun remove(key: String) {
        values.remove(key)?.let { used -= sizeOf(it) }
    }

    @Synchronized fun resize(bytes: Int) {
        limit = bytes.coerceAtLeast(1)
        trim()
    }

    @Synchronized fun clear() {
        values.clear()
        used = 0
    }

    @Synchronized fun size(): Int = used

    private fun trim() {
        val iterator = values.entries.iterator()
        while (used > limit && iterator.hasNext()) {
            used -= sizeOf(iterator.next().value)
            iterator.remove()
        }
    }
}
