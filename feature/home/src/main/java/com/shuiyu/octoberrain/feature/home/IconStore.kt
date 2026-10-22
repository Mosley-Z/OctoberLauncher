package com.shuiyu.octoberrain.feature.home

import android.graphics.Bitmap
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.IconTargets
import com.shuiyu.octoberrain.core.common.LauncherEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 桌面图标缓存。
 *
 * 按“入口键 + 目标尺寸”缓存已加载的位图，未命中时向应用目录异步请求；
 * 加载完成后通过回调通知视图重绘。缓存容量固定，避免长列表占用过多内存。
 */
internal class IconStore(
    private val scope: CoroutineScope,
) {
    private val cache =
        object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
                size > MAX_ENTRIES
        }

    /** 正在加载中的键集合，防止同一图标重复请求。 */
    private val loading = HashSet<String>()

    /**
     * 缓存代际。
     *
     * 每次清空或按包名失效时递增，用于丢弃失效前发起的在途加载结果，
     * 避免旧图标在清空之后重新写回缓存。
     */
    private var generation = 0

    /** 读取已缓存图标，未命中返回 null。 */
    fun get(
        key: String,
        sizePx: Int,
    ): Bitmap? =
        synchronized(cache) {
            cache[cacheKey(key, sizePx)]
        }

    /**
     * 请求加载图标。
     *
     * 参数 entry 为桌面入口；sizePx 为目标边长；
     * onLoaded 在图标写入缓存后回调，调用方据此触发局部重绘。
     */
    fun request(
        entry: LauncherEntry,
        sizePx: Int,
        onLoaded: () -> Unit,
    ) {
        if (sizePx <= 0) return
        val key = cacheKey(entry.key, sizePx)
        if (get(entry.key, sizePx) != null) return
        // 读取代际与登记加载中记录在同一缓存锁内完成，避免清空恰好发生在两者之间。
        // 锁顺序固定为缓存锁在外、加载集合锁在内，其它方法均只取其中一把。
        val requestGeneration: Int
        synchronized(cache) {
            requestGeneration = generation
            synchronized(loading) {
                if (!loading.add(key)) return
            }
        }
        scope.launch {
            val bitmap =
                runCatching {
                    if (entry.isApp) {
                        entry.packageName?.let { AppServices.catalog.loadIcon(it, sizePx) }
                    } else {
                        entry.special?.let { AppServices.catalog.loadSpecialIcon(it, sizePx) }
                    }
                }.getOrNull()
            synchronized(loading) { loading.remove(key) }
            if (bitmap != null) {
                // 请求期间缓存被清空或按包名失效时丢弃结果，避免旧图标重新写回。
                val stored =
                    synchronized(cache) {
                        if (generation != requestGeneration) {
                            false
                        } else {
                            cache[key] = bitmap
                            true
                        }
                    }
                if (stored) onLoaded()
            }
        }
    }

    /**
     * 使指定入口的图标缓存失效。
     *
     * 参数 targetId 为应用包名或 special:<名称>；应用补 app: 前缀后按入口键清除
     * 该入口在所有目标尺寸下的缓存与加载记录，下次绘制时重新请求图标。
     */
    fun invalidate(targetId: String) {
        val prefix = "${IconTargets.entryKeyOf(targetId)}@"
        synchronized(cache) {
            // 递增代际，使失效前发起的在途请求完成后不写回缓存。
            generation++
            cache.keys.removeAll { it.startsWith(prefix) }
        }
        synchronized(loading) { loading.removeAll { it.startsWith(prefix) } }
    }

    /** 清空缓存并放弃加载中的记录。 */
    fun clear() {
        synchronized(cache) {
            generation++
            cache.clear()
        }
        synchronized(loading) { loading.clear() }
    }

    private fun cacheKey(
        key: String,
        sizePx: Int,
    ): String = "$key@$sizePx"

    companion object {
        /** 缓存条目上限。 */
        private const val MAX_ENTRIES = 48
    }
}
