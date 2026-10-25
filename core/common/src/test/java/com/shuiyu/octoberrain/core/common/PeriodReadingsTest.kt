package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PeriodReadingsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 25)
    private val start = today.withDayOfMonth(1)

    private fun stamp(date: LocalDate) = date.atStartOfDay(zone).toEpochSecond()

    private fun snapshot(complete: Boolean): JsonObject {
        val days = JsonObject(mapOf(stamp(today).toString() to JsonPrimitive(60)))
        val fields = mutableMapOf("readTimes" to days)
        return if (complete) {
            JsonObject(
                fields +
                    mapOf(
                        "baseTime" to JsonPrimitive(stamp(start)),
                        "totalReadTime" to JsonPrimitive(60),
                    ),
            )
        } else {
            JsonObject(fields)
        }
    }

    @Test fun `complete sparse response establishes omitted zero days but incomplete cache does not`() {
        val fetched =
            today
                .atTime(12, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val known = periodReadings(snapshot(true), start, start.plusMonths(1), fetched, today, zone)
        assertEquals(0L, known[today.minusDays(1)])
        assertEquals(1, continuousReadingDays(known, today))
        val partial = periodReadings(snapshot(false), start, start.plusMonths(1), fetched, today, zone)
        assertNull(partial[today.minusDays(1)])
        assertNull(continuousReadingDays(partial, today))
    }

    @Test fun `stale complete period never establishes today or future dates`() {
        val fetched =
            today
                .minusDays(1)
                .atTime(12, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val days = periodReadings(snapshot(true), start, start.plusMonths(1), fetched, today, zone)
        assertFalse(today in days)
        assertFalse(today.plusDays(1) in days)
        assertEquals(0L, days[today.minusDays(1)])
    }
}
