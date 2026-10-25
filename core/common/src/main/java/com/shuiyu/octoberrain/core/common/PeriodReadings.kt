package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A validated full-period response omits zero days; an absent or incomplete period remains unknown. */
fun periodReadings(
    payload: JsonObject,
    start: LocalDate,
    endExclusive: LocalDate,
    fetchedMillis: Long,
    today: LocalDate,
    zone: ZoneId,
): Map<LocalDate, Long> {
    val entries = payload["readTimes"] as? JsonObject
    if (entries == null || fetchedMillis <= 0) return emptyMap()
    val fetched = Instant.ofEpochMilli(fetchedMillis).atZone(zone).toLocalDate()
    val base = (payload["baseTime"] as? JsonPrimitive)?.longOrNull
    val total = (payload["totalReadTime"] as? JsonPrimitive)?.longOrNull
    val complete = base == start.atStartOfDay(zone).toEpochSecond() && total != null && total >= 0
    val cutoff = if (complete) minOf(today, fetched, endExclusive.minusDays(1)) else minOf(today, fetched)
    val days = mutableMapOf<LocalDate, Long>()
    if (complete) {
        var date = start
        while (date <= cutoff) {
            days[date] = 0L
            date = date.plusDays(1)
        }
    }
    entries.forEach { (stamp, raw) ->
        val epoch = stamp.toLongOrNull() ?: return@forEach
        val seconds = (raw as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 } ?: return@forEach
        val date = Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate()
        if ((!complete || date >= start) && date <= cutoff) days[date] = seconds
    }
    return days
}
