package com.shuiyu.octoberrain.feature.reader.web

import com.shuiyu.octoberrain.core.common.AppJson
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
        if (!acceptsContext(origin, mainFrame, closed) || payload.length > 8192) return null
        return runCatching {
            val data = AppJson.format.parseToJsonElement(payload) as? JsonObject ?: return@runCatching null
            if (data.text("session") != session) return@runCatching null
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
            "locate" ->
                data
                    .number("anchor")
                    ?.takeIf { it in 0..4_000_000 }
                    ?.let { WebEvent(kind, 0, position = WebPosition(it)) }
            "turn" -> data.number("delta")?.takeIf { it == -1 || it == 1 }?.let { WebEvent(kind, 0, delta = it) }
            "link" -> data.text("url")?.takeIf { it.length <= 2048 }?.let { WebEvent(kind, 0, url = it) }
            "error" -> WebEvent(kind, request)
            else -> null
        }
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
