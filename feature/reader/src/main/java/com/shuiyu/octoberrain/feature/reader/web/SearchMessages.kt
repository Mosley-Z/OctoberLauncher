package com.shuiyu.octoberrain.feature.reader.web

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class WebSearchHit(
    val position: WebPosition,
    val snippet: String,
)

internal data class WebSearchPage(
    val offset: Int,
    val total: Int,
    val hits: List<WebSearchHit>,
)

/** 扫描结果只接受当前引擎坐标，不从脚本反序列化任意来源/Locator。 */
internal object SearchMessages {
    fun parse(data: JsonObject): WebSearchPage? =
        runCatching {
            val offset = data.integer("offset")
            val total = data.integer("total")
            require(total in 0..4_000_000 && offset in 0..4_000_000)
            val values = data.getValue("hits") as JsonArray
            require(values.size == minOf(64, (total - offset).coerceAtLeast(0)))
            WebSearchPage(offset, total, values.map { hit(it as JsonObject) })
        }.getOrNull()

    private fun hit(data: JsonObject): WebSearchHit {
        require("anchor" in data || listOf("sheet", "row", "column").all { it in data })
        val anchor = data.optionalInteger("anchor")
        val sheet = data.optionalInteger("sheet")
        val row = data.optionalInteger("row")
        val column = data.optionalInteger("column")
        require(anchor in 0..4_000_000 && sheet in 0..99)
        require(row in 0..99999 && column in 0..1023)
        val snippet = data.getValue("snippet").jsonPrimitive
        require(snippet.isString && snippet.content.length <= 160)
        return WebSearchHit(WebPosition(anchor = anchor, sheet = sheet, row = row, column = column), snippet.content)
    }

    private fun JsonObject.integer(name: String): Int = requireNotNull(getValue(name).jsonPrimitive.intOrNull)

    private fun JsonObject.optionalInteger(name: String): Int = if (name in this) integer(name) else 0
}
