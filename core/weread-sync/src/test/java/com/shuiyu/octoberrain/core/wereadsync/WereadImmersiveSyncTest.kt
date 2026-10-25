package com.shuiyu.octoberrain.core.wereadsync

import com.shuiyu.octoberrain.core.common.WereadRefreshRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WereadImmersiveSyncTest {
    @get:Rule val folder = TemporaryFolder()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 2)
    private val now =
        today
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    private val calls = mutableListOf<String>()

    private fun transport(omitToday: Boolean = false) =
        WereadRequest { api, body ->
            val target = body["bookId"] ?: body["baseTime"]
            calls += "$api:${target?.jsonPrimitive?.content.orEmpty()}"
            when (api) {
                "/shelf/sync" -> shelf()
                "/book/getprogress" -> json("""{"book":{"progress":42,"readingTime":120}}""")
                "/user/notebooks" -> json("""{"books":[],"hasMore":0}""")
                "/readdata/detail" -> {
                    if (body["mode"]?.jsonPrimitive?.content == "overall") {
                        json("""{"readStat":[{"stat":"读完","counts":"123本"}]}""")
                    } else {
                        val base =
                            body
                                .getValue("baseTime")
                                .jsonPrimitive.content
                                .toLong()
                        val start = Instant.ofEpochSecond(base).atZone(zone).toLocalDate()
                        period(start, omitToday)
                    }
                }
                else -> null
            }
        }

    private fun shelf() =
        buildJsonObject {
            put(
                "books",
                buildJsonArray {
                    (1..12).forEach { index ->
                        add(
                            buildJsonObject {
                                put("bookId", "b$index")
                                put("title", "Book $index")
                                put("readUpdateTime", index)
                            },
                        )
                    }
                },
            )
        }

    private fun period(
        start: LocalDate,
        omitToday: Boolean,
    ) = buildJsonObject {
        val days = (0 until start.lengthOfMonth()).map { start.plusDays(it.toLong()) }
        put(
            "readTimes",
            buildJsonObject {
                for (date in days.filter { it <= today }) {
                    if (omitToday && date == today) continue
                    val seconds = if (date.monthValue == 9) 0 else 60
                    put(date.atStartOfDay(zone).toEpochSecond().toString(), seconds)
                }
            },
        )
    }

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test fun `manual refresh fetches marks and ideas only for the recent ten candidates`() =
        runBlocking {
            val base = transport()
            val gateway =
                WereadRequest { api, body ->
                    when (api) {
                        "/user/notebooks" -> JsonObject(shelf() + ("hasMore" to JsonPrimitive(0)))
                        "/book/bookmarklist" -> {
                            calls += "marks:${body.getValue("bookId").jsonPrimitive.content}"
                            json("""{"updated":[],"chapters":[]}""")
                        }
                        "/review/list/mine" -> {
                            calls += "ideas:${body.getValue("bookid").jsonPrimitive.content}"
                            json("""{"reviews":[],"hasMore":0}""")
                        }
                        else -> base.request(api, body)
                    }
                }
            val service = WereadCardRefreshService(gateway, folder.root, { now })
            assertTrue(service.refresh(WereadRefreshRequest("immersive")).success)
            assertEquals(10, calls.count { it.startsWith("marks:") })
            assertEquals(10, calls.count { it.startsWith("ideas:") })
            assertFalse(calls.any { it.endsWith(":b1") || it.endsWith(":b2") })
        }

    @Test fun `auto refresh bounds book requests to recent ten and backfills history only once`() =
        runBlocking {
            val service = WereadCardRefreshService(transport(), folder.root, { now })
            assertTrue(service.refresh(WereadRefreshRequest("immersive_auto")).success)
            assertEquals(10, calls.count { it.startsWith("/book/getprogress:") })
            assertFalse(calls.any { it.endsWith(":b1") || it.endsWith(":b2") })
            assertEquals(4, calls.count { it.startsWith("/readdata/detail:") })
            calls.clear()
            assertTrue(service.refresh(WereadRefreshRequest("immersive_auto")).success)
            assertEquals(1, calls.count { it.startsWith("/book/getprogress:") })
            assertEquals(2, calls.count { it.startsWith("/readdata/detail:") })
        }

    @Test fun `missing current day stops bounded lookback and never stamps false success`() =
        runBlocking {
            val service = WereadCardRefreshService(transport(omitToday = true), folder.root, { now })
            assertFalse(service.refresh(WereadRefreshRequest("immersive_auto")).success)
            assertEquals(2, calls.count { it.startsWith("/readdata/detail:") })
            assertFalse(folder.root.resolve(WereadImmersiveSync.STAMP).exists())
        }
}
