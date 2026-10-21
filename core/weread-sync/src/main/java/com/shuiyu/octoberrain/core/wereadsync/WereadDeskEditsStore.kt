package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

/**
 * 墨台手填价格和移出书目的持久状态，独立于联网生成的账单与阅读资产。
 *
 * 使用原盘偏好兼容键；内核加载器和墨台宿主共同读取同一份数据。负价格是删除手填价的
 * 标记，可阻止旧迁移文件的价格再次回流。隐藏列表一旦写入便作为完整快照，空值表示恢复全部。
 *
 * @param root 微读数据目录
 */
class WereadDeskEditsStore(
    private val root: File,
) {
    private val store = WereadStore(root)
    private val documents = mutableMapOf<String, CachedDocument>()

    /** 返回手填价（分），未知或已删除时返回 -1；零表示明确免费。 */
    fun priceFen(bookId: String): Int =
        store.withPrefsLock {
            val saved = prefsString("$PRICE_PREFIX$bookId")
            if (saved != null) return@withPrefsLock saved.toIntOrNull()?.coerceAtLeast(-1) ?: -1
            val legacy = readObject(PRICE_FILE)
            (legacy?.get(bookId) as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.coerceAtLeast(-1) ?: -1
        }

    /** 录价成功落盘才返回 true；负值删除手填价。 */
    fun setPrice(
        bookId: String,
        fen: Int,
    ): Boolean = bookId.isNotBlank() && putString("$PRICE_PREFIX$bookId", fen.coerceAtLeast(-1).toString())

    /** 返回已移出书目；未曾写入时兼容十雨旧配置和原版迁移文件。 */
    fun hiddenIds(legacyIds: Collection<String> = emptyList()): Set<String> =
        store.withPrefsLock {
            val saved = prefsString(HIDDEN_KEY)
            if (saved != null) return@withPrefsLock parseIds(saved)
            val legacy = (readObject(HIDDEN_FILE)?.get("ids") as? JsonPrimitive)?.contentOrNull.orEmpty()
            parseIds(legacy) + legacyIds.filter { it.isNotBlank() }
        }

    /** 加入一个移出书目，读改写共用偏好锁，避免不同分区互相覆盖。 */
    fun hide(
        bookId: String,
        legacyIds: Collection<String> = emptyList(),
    ): Boolean =
        store.withPrefsLock {
            bookId.isNotBlank() && putString(HIDDEN_KEY, (hiddenIds(legacyIds) + bookId).joinToString(","))
        }

    /** 恢复全部已移出书目，保留空快照，避免旧配置再次回流。 */
    fun restoreHidden(): Boolean = putString(HIDDEN_KEY, "")

    /** 保存内核长按操作后的完整隐藏快照。 */
    fun saveHidden(ids: Collection<String>): Boolean =
        putString(HIDDEN_KEY, ids.filter(String::isNotBlank).toSet().joinToString(","))

    private fun parseIds(raw: String): Set<String> =
        raw
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private fun prefsString(key: String): String? =
        ((readObject("kernel_prefs.json")?.get("string") as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

    private fun putString(
        key: String,
        value: String,
    ): Boolean =
        store.withPrefsLock {
            store.putString(key, value).also { saved -> if (saved) documents.clear() }
        }

    /** 大偏好只解析一次；原子替换的文件标识变化也会失效，覆盖同长度同时间的写入。 */
    private fun readObject(name: String): JsonObject? {
        val file = File(root, name)
        val stamp =
            runCatching {
                val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                FileStamp(
                    attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS),
                    attributes.size(),
                    attributes.fileKey(),
                )
            }.getOrNull()
        documents[name]?.takeIf { it.stamp == stamp }?.let { return it.value }
        val value = runCatching { Json.parseToJsonElement(file.readText()) as? JsonObject }.getOrNull()
        documents[name] = CachedDocument(stamp, value)
        return value
    }

    private data class FileStamp(
        val modified: Long,
        val length: Long,
        val key: Any?,
    )

    private data class CachedDocument(
        val stamp: FileStamp?,
        val value: JsonObject?,
    )

    private companion object {
        const val PRICE_PREFIX = "menu_prices."
        const val PRICE_FILE = "menu_prices.json"
        const val HIDDEN_KEY = "menu_hidden.ids"
        const val HIDDEN_FILE = "menu_hidden.json"
    }
}
