package com.shuiyu.octoberrain.feature.widgets.kernel

import com.shuiyu.octoberrain.core.common.continuousReadingDays
import com.shuiyu.octoberrain.core.common.periodReadings
import com.shuiyu.octoberrain.feature.widgets.ImmersiveReadingStats
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reads only existing local statistics during the board's background preparation. */
internal fun readImmersiveReadingStats(root: File): ImmersiveReadingStats =
    parseImmersiveReadingStats(
        runCatching { root.resolve("kernel_prefs.json").readText() }.getOrNull(),
        runCatching { root.resolve("stats.json").readText() }.getOrNull(),
        LocalDate.now(),
        ZoneId.systemDefault(),
    )

/** Keeps absent daily entries unknown, and never substitutes shelf counts for lifetime totals. */
internal fun parseImmersiveReadingStats(
    prefsText: String?,
    statsText: String?,
    today: LocalDate,
    zone: ZoneId,
): ImmersiveReadingStats {
    val prefs = jsonObject(prefsText)
    val strings = prefs?.get("string") as? JsonObject
    val times = prefs?.get("long") as? JsonObject
    val raw = jsonObject(statsText).orEmpty().toMutableMap().apply { putAll(strings.orEmpty()) }
    val daily = cachedDailyReadings(raw, times, today, zone)
    val overall =
        jsonObject((raw["overall"] as? JsonPrimitive)?.contentOrNull)
            ?: jsonObject((raw["stats_overall"] as? JsonPrimitive)?.contentOrNull)
    return ImmersiveReadingStats(
        todayMinutes =
            daily[today]
                ?.second
                ?.div(60)
                ?.coerceAtMost(Int.MAX_VALUE.toLong())
                ?.toInt(),
        finishedBooks = finishedBooks(overall),
        streakDays = continuousReadingDays(daily.mapValues { it.value.second }, today),
        dateKey = today.toString(),
    )
}

/** Merges overlapping periods by fetch time, retaining only explicitly cached daily entries. */
private fun cachedDailyReadings(
    raw: Map<String, JsonElement>,
    times: JsonObject?,
    today: LocalDate,
    zone: ZoneId,
): Map<LocalDate, Pair<Long, Long>> {
    val daily = mutableMapOf<LocalDate, Pair<Long, Long>>()
    raw.forEach { (key, value) ->
        if (!key.startsWith("d_weekly:") && !key.startsWith("d_monthly:")) return@forEach
        val snapshot = jsonObject((value as? JsonPrimitive)?.contentOrNull) ?: return@forEach
        val fetched = (times?.get("t_${key.removePrefix("d_")}") as? JsonPrimitive)?.longOrNull ?: 0L
        val base = key.substringAfter(':').toLongOrNull() ?: return@forEach
        val start = Instant.ofEpochSecond(base).atZone(zone).toLocalDate()
        val end = if (key.startsWith("d_monthly:")) start.plusMonths(1) else start.plusDays(7)
        periodReadings(snapshot, start, end, fetched, today, zone).forEach { (date, seconds) ->
            if (fetched >= (daily[date]?.first ?: -1L)) daily[date] = fetched to seconds
        }
    }
    return daily
}

private fun finishedBooks(overall: JsonObject?): Int? =
    (overall?.get("readStat") as? JsonArray)?.firstNotNullOfOrNull { item ->
        val stat = item as? JsonObject
        val label = (stat?.get("stat") as? JsonPrimitive)?.contentOrNull
        val counts = (stat?.get("counts") as? JsonPrimitive)?.contentOrNull
        if (label == "读完") {
            counts
                ?.trim()
                ?.removeSuffix("本")
                ?.trim()
                ?.toIntOrNull()
                ?.takeIf { it >= 0 }
        } else {
            null
        }
    }

private fun jsonObject(text: String?): JsonObject? =
    text?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
