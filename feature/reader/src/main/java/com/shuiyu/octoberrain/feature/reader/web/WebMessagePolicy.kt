package com.shuiyu.octoberrain.feature.reader.web

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.feature.reader.ReaderTable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class WebEvent(
    val kind: String,
    val request: Int,
    val position: WebPosition? = null,
    val delta: Int = 0,
    val url: String? = null,
    val found: Boolean = true,
    val table: ReaderTable? = null,
    val failure: ReaderFailure? = null,
    val search: WebSearchPage? = null,
)

/** 固定来源、主帧、会话和长度；不暴露读文件/执行代码/任意原生调用。 */
internal object WebMessagePolicy {
    const val HOST = "reader.october.invalid"
    const val ORIGIN = "https://$HOST"

    fun parse(
        payload: String,
        session: String,
        origin: String,
        mainFrame: Boolean,
        closed: Boolean,
    ): WebEvent? {
        if (!acceptsContext(origin, mainFrame, closed) || payload.length > 256 * 1024) return null
        return runCatching {
            val data = AppJson.format.parseToJsonElement(payload) as? JsonObject ?: return@runCatching null
            if (data.text("session") != session) return@runCatching null
            if (payload.length > 8192 && data.text("kind") !in setOf("table", "searchResults")) return@runCatching null
            event(data)
        }.getOrNull()
    }

    private fun acceptsContext(
        origin: String,
        main: Boolean,
        closed: Boolean,
    ): Boolean = !closed && main && origin == ORIGIN

    private fun event(data: JsonObject): WebEvent? {
        val kind = data.text("kind") ?: return null
        val request = data.number("request") ?: 0
        return when (kind) {
            "ready" -> ready(data, request)
            "locate" -> locate(data)
            "turn" -> data.number("delta")?.takeIf { it == -1 || it == 1 }?.let { WebEvent(kind, 0, delta = it) }
            "link" -> data.text("url")?.takeIf { it.length <= 2048 }?.let { WebEvent(kind, 0, url = it) }
            "table" -> table(data, request)
            "searchResults" -> search(data, request)
            "error" -> WebEvent(kind, request, failure = error(data.text("code")))
            else -> null
        }
    }

    private fun search(
        data: JsonObject,
        request: Int,
    ): WebEvent? =
        if (request > 0) SearchMessages.parse(data)?.let { WebEvent("searchResults", request, search = it) } else null

    private fun table(
        data: JsonObject,
        request: Int,
    ): WebEvent? =
        if (request > 0) {
            TableMessages.parse(data)?.let {
                WebEvent(
                    "table",
                    request,
                    table = it,
                    found =
                        data.text("found") != "false",
                )
            }
        } else {
            null
        }

    private fun error(code: String?): ReaderFailure =
        when (code) {
            "limit" -> ReaderFailure.LIMIT
            "corrupt" -> ReaderFailure.CORRUPT
            else -> ReaderFailure.RENDERER
        }

    private fun locate(data: JsonObject): WebEvent? =
        run {
            val anchor = data.number("anchor") ?: return@run null
            val fraction = data["fraction"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            if (anchor !in 0..4_000_000 || !fraction.isFinite() || fraction !in 0.0..1.0) return@run null
            WebEvent("locate", 0, position = WebPosition(anchor, fraction))
        }

    private fun ready(
        data: JsonObject,
        request: Int,
    ): WebEvent? =
        run {
            val anchor = data.number("anchor") ?: return@run null
            val fraction = data["fraction"]?.jsonPrimitive?.doubleOrNull ?: return@run null
            val page = data.number("page") ?: return@run null
            val pages = data.number("pages") ?: return@run null
            if (request < 1 || anchor !in 0..4_000_000) return@run null
            if (!fraction.isFinite() || fraction !in 0.0..1.0) return@run null
            if (pages !in 1..100000 || page !in 0 until pages) return@run null
            WebEvent(
                "ready",
                request,
                WebPosition(anchor, fraction, page, pages),
                found = data.text("found") != "false",
            )
        }

    private fun JsonObject.text(name: String): String? = get(name)?.jsonPrimitive?.content

    private fun JsonObject.number(name: String): Int? = get(name)?.jsonPrimitive?.intOrNull
}
