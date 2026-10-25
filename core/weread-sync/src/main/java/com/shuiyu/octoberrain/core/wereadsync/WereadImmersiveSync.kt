package com.shuiyu.octoberrain.core.wereadsync

import com.shuiyu.octoberrain.core.common.WereadRefreshRequest
import com.shuiyu.octoberrain.core.common.continuousReadingDays
import com.shuiyu.octoberrain.core.common.periodReadings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One bounded carousel batch, plus as many historical periods as the current streak requires. */
internal class WereadImmersiveSync(
    private val request: WereadRequest,
    private val root: File,
    private val clock: () -> Long,
) {
    private val store = WereadStore(root)
    private val zone = ZoneId.systemDefault()
    private val today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

    fun refresh(intent: WereadRefreshRequest): WereadSyncReport {
        val warnings = mutableListOf<String>()
        val books = WereadBookSync(request, root, clock)
        val candidates = books.recentBooks(intent.bookId, intent.mode == "immersive")
        warnings += books.warnings
        if (candidates == null) warnings += "轮播列表未更新"
        if (!fetchPeriod("monthly", today.withDayOfMonth(1))) warnings += "今日阅读未更新"
        if (!fetchPeriod("overall", null)) warnings += "读完书籍未更新"
        if (!completeStreak()) warnings += "连续阅读历史未补齐"
        if (intent.mode == "immersive" && candidates != null) {
            val notes = WereadNoteSync(request, root, clock)
            val batch = notes.sync(force = true, bookIds = candidates.map { it.bookId }.toSet())
            if (batch == null || !store.writeNotes(batch.indexJson, batch.notes) || !batch.complete) {
                warnings += "部分划线未更新"
            }
        }
        val success = warnings.isEmpty()
        if (success) {
            root.resolve(STAMP).apply {
                writeText(today.toString())
                setLastModified(clock())
            }
        }
        return WereadSyncReport(success, if (success) "阅读数据已更新" else "${warnings.distinct().joinToString("；")}，保留已有数据")
    }

    private fun fetchPeriod(
        mode: String,
        month: LocalDate?,
    ): Boolean {
        val base = month?.atStartOfDay(zone)?.toEpochSecond() ?: 0L
        val payload =
            request.request(
                "/readdata/detail",
                buildJsonObject {
                    put("mode", mode)
                    put("baseTime", base)
                },
            ) ?: return false
        return (mode != "monthly" || payload["readTimes"] is JsonObject) &&
            store.writeStats(mode, base, payload.toString(), clock())
    }

    /** Never infer a zero from an omitted day, and never repeatedly fetch the same missing month. */
    @Suppress("ReturnCount") // Each stop distinguishes a break from unavailable history.
    private fun completeStreak(): Boolean {
        val days = mutableMapOf<LocalDate, Long>()
        val visited = mutableSetOf<LocalDate>()
        var cursor = today
        while (true) {
            val month = cursor.withDayOfMonth(1)
            if (!visited.add(month)) return false
            val entries = monthlyEntries(month, cursor) ?: return false
            days.putAll(entries)
            if (continuousReadingDays(days, today) != null) return true
            val current = days[today] ?: return false
            cursor = if (current >= 60L) today else today.minusDays(1)
            while ((days[cursor] ?: -1L) >= 60L) cursor = cursor.minusDays(1)
            if (cursor.withDayOfMonth(1) in visited) return false
        }
    }

    private fun monthlyEntries(
        month: LocalDate,
        cursor: LocalDate,
    ): Map<LocalDate, Long>? {
        val key = "monthly:${month.atStartOfDay(zone).toEpochSecond()}"
        val fetched = store.getLong("t_$key") ?: 0L
        val fetchedDate = Instant.ofEpochMilli(fetched).atZone(zone).toLocalDate()
        val cached = parse(store.getString("d_$key"))
        val available = (cached != null && fetchedDate >= cursor) || fetchPeriod("monthly", month)
        val payload = if (available) parse(store.getString("d_$key")) else null
        val savedAt = store.getLong("t_$key") ?: 0L
        return payload?.let { periodReadings(it, month, month.plusMonths(1), savedAt, today, zone) }
    }

    private fun parse(text: String?): JsonObject? =
        text?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    companion object {
        const val STAMP = "immersive-refresh.stamp"
    }
}
