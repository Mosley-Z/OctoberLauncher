package com.shuiyu.octoberrain.feature.content.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

private const val BUCKET_MILLIS_THRESHOLD = 100_000_000_000L
private const val BUCKET_SECONDS_THRESHOLD = 100_000_000L
private const val TOTAL_MILLIS_THRESHOLD = 100_000_000L
private const val DAY_SECONDS_THRESHOLD = 100_000L
private const val MONTH_SECONDS_THRESHOLD = 2_700_000L
private const val YEAR_SECONDS_THRESHOLD = 31_536_000L

/** 解析分桶数据，优先读取对象形式的时间戳字典。 */
internal fun buckets(
    period: WereadPeriod,
    payload: JsonObject,
    baseSeconds: Long,
): List<WereadBucket> {
    val dictionary = payload.objOrNull("readTimes") ?: payload.objOrNull("dailyReadTimes")
    val fromObject =
        dictionary?.entries?.mapNotNull { (key, value) ->
            val start = bucketStartMillis(period, key, baseSeconds) ?: return@mapNotNull null
            val seconds = normalizeBucketSeconds(period, (value as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L)
            if (seconds <= 0L) null else WereadBucket(start, seconds)
        } ?: emptyList()
    val array = payload.arrayOrNull("readTimes") ?: payload.arrayOrNull("days")
    val fromArray = array?.mapNotNull { parseArrayBucket(period, it, baseSeconds) } ?: emptyList()
    return (fromObject + fromArray).sortedBy { it.startMillis }
}

/** 解析数组形式的分桶，兼容 day 与 date 两种字段。 */
private fun parseArrayBucket(
    period: WereadPeriod,
    element: JsonElement,
    baseSeconds: Long,
): WereadBucket? =
    (element as? JsonObject)?.let { entry ->
        val day = entry.intOrZero("day").takeIf { it > 0 } ?: entry.intOrZero("date")
        val start = if (day > 0) bucketStartMillis(period, day.toString(), baseSeconds) else null
        if (start == null) {
            null
        } else {
            val raw =
                entry.longOrZero("readTime").takeIf { it > 0L }
                    ?: entry.longOrZero("seconds").takeIf { it > 0L }
                    ?: entry.longOrZero("duration")
            val seconds = normalizeBucketSeconds(period, raw)
            if (seconds > 0L) WereadBucket(start, seconds) else null
        }
    }

/** 将字典键换算为分桶起点毫秒，支持秒级、毫秒级时间戳与周期内序号。 */
private fun bucketStartMillis(
    period: WereadPeriod,
    key: String,
    baseSeconds: Long,
): Long? {
    val numeric = key.toLongOrNull()
    if (numeric == null) return dateKeyMillis(key)
    return when {
        numeric > BUCKET_MILLIS_THRESHOLD -> numeric
        numeric > BUCKET_SECONDS_THRESHOLD -> numeric * 1000L
        numeric > 0L -> indexStartMillis(period, numeric, baseSeconds)
        else -> null
    }
}

/** 解析年-月-日形式的键，无法解析时返回 null。 */
private fun dateKeyMillis(key: String): Long? =
    try {
        val date = LocalDate.parse(key)
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

/** 按周期内序号计算分桶起点。 */
private fun indexStartMillis(
    period: WereadPeriod,
    index: Long,
    baseSeconds: Long,
): Long {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochSecond(baseSeconds).atZone(zone)
    val shifted =
        when (period) {
            WereadPeriod.WEEKLY, WereadPeriod.MONTHLY -> moment.plusDays(index - 1)
            WereadPeriod.ANNUALLY -> moment.plusMonths(index - 1)
            WereadPeriod.OVERALL -> moment.plusYears(index - 1)
        }
    return shifted
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}

/** 按周期粒度归一化分桶时长的秒与毫秒单位。 */
private fun normalizeBucketSeconds(
    period: WereadPeriod,
    value: Long,
): Long =
    when (period) {
        WereadPeriod.WEEKLY, WereadPeriod.MONTHLY ->
            if (value > DAY_SECONDS_THRESHOLD) value / 1000L else value
        WereadPeriod.ANNUALLY ->
            if (value > MONTH_SECONDS_THRESHOLD) value / 1000L else value
        WereadPeriod.OVERALL ->
            if (value > YEAR_SECONDS_THRESHOLD) value / 1000L else value
    }

/** 归一化总时长，超过三日年量级视为毫秒。 */
internal fun normalizeTotal(value: Long): Long = if (value > TOTAL_MILLIS_THRESHOLD) value / 1000L else value
