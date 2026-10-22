package com.shuiyu.octoberrain.feature.reader.web

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableMessagesTest {
    @Test fun largeWindowHasSeparateBoundedProtocol() {
        val value = message()
        assertTrue(value.toString().length > 8192)
        assertEquals(20, parse(value)?.table?.cells?.size)
        assertNull(parse(JsonObject(value + ("kind" to JsonPrimitive("ready")))))
        assertNull(WebMessagePolicy.parse(value.toString(), "old", WebMessagePolicy.ORIGIN, true, false))
    }

    @Test fun rejectsOversizedCoordinatesCellsAndMerges() {
        val value = message()
        val tooWide = JsonArray(listOf(JsonArray(List(11) { JsonPrimitive("") })))
        val longCell = JsonArray(listOf(JsonArray(listOf(JsonPrimitive("x".repeat(513))))))
        val numericCell = JsonArray(listOf(JsonArray(listOf(JsonPrimitive(12)))))
        val invalid =
            listOf(
                "sheet" to JsonPrimitive(1),
                "row" to JsonPrimitive(-1),
                "totalRows" to JsonPrimitive(100001),
                "totalColumns" to JsonPrimitive(1025),
                "cells" to JsonArray(List(21) { JsonArray(emptyList()) }),
                "cells" to tooWide,
                "cells" to longCell,
                "cells" to numericCell,
                "merges" to JsonArray(listOf(merge(Int.MAX_VALUE))),
            )
        for ((key, replacement) in invalid) {
            assertNull("$key=$replacement", parse(JsonObject(value + (key to replacement))))
        }
        assertNull(WebMessagePolicy.parse(" ".repeat(256 * 1024 + 1), "live", WebMessagePolicy.ORIGIN, true, false))
    }

    @Test fun acceptsOnlyMergesIntersectingWindowAndInsideSheet() {
        assertNotNull(parse(JsonObject(message() + ("merges" to JsonArray(listOf(merge(2)))))))
        assertNull(parse(JsonObject(message() + ("merges" to JsonArray(listOf(merge(200)))))))
    }

    private fun merge(row: Int) =
        buildJsonObject {
            put("row", row)
            put("column", 0)
            put("rows", 2)
            put("columns", 2)
        }

    private fun parse(value: JsonObject) =
        WebMessagePolicy.parse(value.toString(), "live", WebMessagePolicy.ORIGIN, true, false)

    private fun message() =
        buildJsonObject {
            val row = JsonArray(List(10) { JsonPrimitive("x".repeat(100)) })
            put("session", "live")
            put("kind", "table")
            put("request", 1)
            put("sheet", 0)
            put("sheets", JsonArray(listOf(JsonPrimitive("Sheet1"))))
            put("row", 0)
            put("column", 0)
            put("totalRows", 100)
            put("totalColumns", 20)
            put("cells", JsonArray(List(20) { row }))
        }
}
