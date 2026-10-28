package com.shuiyu.octoberrain.core.wereadsync

import com.shuiyu.octoberrain.core.common.WereadRefreshRequest
import com.shuiyu.octoberrain.core.data.WereadDataGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/** 卡片手动刷新只请求当前形态及绑定项，不重复预热整库或历史统计。 */
class WereadCardRefreshService(
    private val request: WereadRequest,
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store = WereadStore(root)

    /** 与全量同步、迁移使用同一互斥门，异常也会释放。 */
    suspend fun refresh(intent: WereadRefreshRequest): WereadSyncReport =
        withContext(Dispatchers.IO) {
            if (!WereadDataGate.tryBeginSync()) {
                return@withContext WereadSyncReport(false, "同步或迁移正在进行，请稍后重试")
            }
            try {
                runRefresh(intent)
            } finally {
                WereadDataGate.endSync()
            }
        }

    private fun runRefresh(intent: WereadRefreshRequest): WereadSyncReport =
        when (intent.mode) {
            "assets", "assets_full" -> WereadBookSync(request, root, clock).refreshAssets(intent.mode == "assets_full")
            "immersive", "immersive_auto" -> WereadImmersiveSync(request, root, clock).refresh(intent)
            else -> runCardRefresh(intent)
        }

    private fun runCardRefresh(intent: WereadRefreshRequest): WereadSyncReport {
        val prefs = readPrefs()
        val targets = refreshTargets(intent.mode, bindingMask(prefs))
        val warnings = mutableListOf<String>()
        var updated = 0
        for (mode in targets) {
            val saved =
                when (mode) {
                    "book" -> refreshBook(intent.bookId.ifBlank { store.getString("book_pick").orEmpty() }, warnings)
                    "note" -> refreshNotes(warnings)
                    else -> refreshPeriod(mode, intent)
                }
            if (saved) updated++ else warnings += "$mode 刷新未完成，保留已有数据"
        }
        val success = warnings.isEmpty()
        val message = if (success) "刷新完成：$updated 项" else "部分刷新：${warnings.joinToString("；") }"
        return WereadSyncReport(success, message)
    }

    private fun refreshPeriod(
        mode: String,
        intent: WereadRefreshRequest,
    ): Boolean {
        val base =
            if (mode == intent.mode && intent.baseTimeSec > 0) {
                intent.baseTimeSec
            } else {
                WereadSyncService.defaultPeriods(clock()).first { it.mode == mode }.baseTimeSec
            }
        val payload =
            request.request(
                "/readdata/detail",
                buildJsonObject {
                    put("mode", mode)
                    put("baseTime", base)
                },
            )
        return payload != null && store.writeStats(mode, base, payload.toString(), clock())
    }

    private fun refreshBook(
        id: String,
        warnings: MutableList<String>,
    ): Boolean {
        val books = WereadBookSync(request, root, clock)
        val result = books.currentBook(id)
        warnings += books.warnings
        return result != null &&
            result.second != null &&
            store.writeShelf(result.first, clock()) &&
            store.writeShelfIds(result.first.map { it.bookId }) &&
            store.writeCurrentBook(result.second) &&
            store.writeAssets(books.cachedAssets())
    }

    private fun refreshNotes(warnings: MutableList<String>): Boolean {
        val notes = WereadNoteSync(request, root, clock)
        val result = notes.sync(force = true)
        warnings += notes.warnings
        return result != null && store.writeNotes(result.indexJson, result.notes) && result.complete
    }

    private fun readPrefs(): JsonObject =
        runCatching {
            Json.parseToJsonElement(File(root, "kernel_prefs.json").readText()) as? JsonObject
        }.getOrNull() ?: JsonObject(emptyMap())

    companion object {
        /** 迁移的原版位掩码为默认，新设置的独立布尔值按位覆盖。 */
        internal fun bindingMask(prefs: JsonObject): Int {
            val ints = prefs["int"] as? JsonObject
            val bools = prefs["bool"] as? JsonObject
            var mask = ints?.get("bind_targets")?.jsonPrimitive?.intOrNull ?: 5
            listOf("bind_week", "bind_month", "bind_book").forEachIndexed { index, key ->
                val enabled = bools?.get(key)?.jsonPrimitive?.booleanOrNull
                if (enabled != null) {
                    val bit = 1 shl index
                    mask = if (enabled) mask or bit else mask and bit.inv()
                }
            }
            return mask
        }

        /** 待办只读本地，其余形态刷新自身并补发开启的周/月/本书。 */
        internal fun refreshTargets(
            mode: String,
            mask: Int,
        ): List<String> =
            when (mode) {
                "todo" -> emptyList()
                else ->
                    buildList {
                        if (mode == "overall") add("annually")
                        add(mode)
                        listOf("weekly", "monthly", "book").forEachIndexed { index, target ->
                            if (mask and (1 shl index) != 0 && target != mode) add(target)
                        }
                    }
            }
    }
}
