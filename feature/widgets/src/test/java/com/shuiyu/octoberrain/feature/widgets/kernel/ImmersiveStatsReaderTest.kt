package com.shuiyu.octoberrain.feature.widgets.kernel

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Daily cache boundaries and lifetime count parsing. */
class ImmersiveStatsReaderTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun missingCacheRemainsUnknown() {
        val stats = parseImmersiveReadingStats(null, null, today, zone)
        assertNull(stats.todayMinutes)
        assertNull(stats.finishedBooks)
        assertNull(stats.streakDays)
    }

    @Test
    fun explicitZeroAndCompletedStreakArePreserved() {
        val stats = parse(days(0, 120, 60, 0))
        assertEquals(0, stats.todayMinutes)
        assertEquals(2, stats.streakDays)
        assertEquals(today.toString(), stats.dateKey)
    }

    @Test
    fun positiveTodayUsesSecondsAndIncludesTodayInStreak() {
        val stats = parse(days(3599, 120, 0))
        assertEquals(59, stats.todayMinutes)
        assertEquals(2, stats.streakDays)
    }

    @Test
    fun unknownPreviousDayDoesNotTruncateStreak() {
        val stats = parse(days(120, 120))
        assertEquals(2, stats.todayMinutes)
        assertNull(stats.streakDays)
    }

    @Test
    fun staleFutureZeroDoesNotEstablishToday() {
        val stats = parse(days(0, 120, 0), today.minusDays(1))
        assertNull(stats.todayMinutes)
        assertNull(stats.streakDays)
    }

    @Test
    fun lifetimeFinishedCountUsesExactReadStatLabel() {
        val overall = """{"readStat":[{"stat":"读过","counts":"527本"},{"stat":"读完","counts":"123本"}]}"""
        val file = JsonObject(mapOf("stats_overall" to JsonPrimitive(overall))).toString()
        assertEquals(123, parseImmersiveReadingStats(null, file, today, zone).finishedBooks)
    }

    /** 单日达到 60 秒才计入连续阅读。 */
    @Test
    fun streakRequiresSixtySecondsPerDay() {
        val stats = parse(days(0, 120, 59, 120))
        assertEquals(1, stats.streakDays)
    }

    /** 今天不足 60 秒时从昨天起算。 */
    @Test
    fun insufficientTodayStartsFromYesterday() {
        val stats = parse(days(30, 120, 60, 0))
        assertEquals(2, stats.streakDays)
    }

    /** 今天不足且昨天也不足时连续天数为 0。 */
    @Test
    fun insufficientYesterdayStopsAtZero() {
        val stats = parse(days(30, 30, 120))
        assertEquals(0, stats.streakDays)
    }

    /** 重叠快照取抓取时间最新的桶，不相加。 */
    @Test
    fun overlappingSnapshotsTakeLatestBucket() {
        val stamp = today.atStartOfDay(zone).toEpochSecond().toString()
        val weekly = JsonObject(mapOf("readTimes" to JsonObject(mapOf(stamp to JsonPrimitive(120L)))))
        val monthly = JsonObject(mapOf("readTimes" to JsonObject(mapOf(stamp to JsonPrimitive(300L)))))
        val prefs =
            JsonObject(
                mapOf(
                    "string" to
                        JsonObject(
                            mapOf(
                                "d_weekly:1" to JsonPrimitive(weekly.toString()),
                                "d_monthly:2" to JsonPrimitive(monthly.toString()),
                            ),
                        ),
                    "long" to
                        JsonObject(
                            mapOf(
                                "t_weekly:1" to JsonPrimitive(today.atStartOfDay(zone).toInstant().toEpochMilli()),
                                "t_monthly:2" to
                                    JsonPrimitive(
                                        today
                                            .plusDays(1)
                                            .atStartOfDay(zone)
                                            .toInstant()
                                            .toEpochMilli(),
                                    ),
                            ),
                        ),
                ),
            ).toString()
        val stats = parseImmersiveReadingStats(prefs, null, today, zone)
        assertEquals(5, stats.todayMinutes)
    }

    /** 未经证实的连续标签不能替代按日桶推导。 */
    @Test
    fun unverifiedStreakLabelDoesNotOverrideDailyBuckets() {
        val overall = """{"readStat":[{"stat":"连续阅读","counts":"12天"}]}"""
        val file = JsonObject(mapOf("stats_overall" to JsonPrimitive(overall))).toString()
        val prefs =
            JsonObject(
                mapOf(
                    "string" to
                        JsonObject(
                            mapOf(
                                "d_weekly:1" to
                                    JsonPrimitive(
                                        JsonObject(mapOf("readTimes" to days(0, 120))).toString(),
                                    ),
                            ),
                        ),
                ),
            ).toString()
        assertNull(parseImmersiveReadingStats(prefs, file, today, zone).streakDays)
    }

    private fun days(vararg seconds: Long): JsonObject =
        JsonObject(
            seconds
                .mapIndexed { index, value ->
                    today
                        .minusDays(index.toLong())
                        .atStartOfDay(zone)
                        .toEpochSecond()
                        .toString() to JsonPrimitive(value)
                }.toMap(),
        )

    private fun parse(
        days: JsonObject,
        fetchedDate: LocalDate = today,
    ) = parseImmersiveReadingStats(
        JsonObject(
            mapOf(
                "string" to
                    JsonObject(
                        mapOf("d_weekly:1" to JsonPrimitive(JsonObject(mapOf("readTimes" to days)).toString())),
                    ),
                "long" to
                    JsonObject(
                        mapOf("t_weekly:1" to JsonPrimitive(fetchedDate.atStartOfDay(zone).toInstant().toEpochMilli())),
                    ),
            ),
        ).toString(),
        null,
        today,
        zone,
    )
}
