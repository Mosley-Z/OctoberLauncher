package com.shuiyu.octoberrain.feature.content.data

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 记录请求并按 api_name 返回预设响应的传输实现。 */
internal class FakeTransport(
    private val handler: (String) -> RawResponse?,
) : WereadTransport {
    val requests = ArrayList<String>()

    override fun post(
        url: String,
        authorization: String,
        body: String,
        timeoutMs: Int,
    ): RawResponse? {
        requests.add(body)
        return handler(body)
    }
}

/** 从请求体中取出 api_name，便于按接口分派响应。 */
internal fun requestName(body: String): String =
    (AppJson.format.parseToJsonElement(body) as JsonObject)["api_name"]?.jsonPrimitive?.content.orEmpty()

/** 构造可直接调用的客户端，测试环境不触碰 Android 框架。 */
internal fun testClient(
    transport: WereadTransport,
    cache: WereadCache? = null,
    key: String = "test-key",
    enabled: Boolean = true,
): WereadClient =
    WereadClient(
        null,
        transport,
        { IntegrationsConfig(wereadApiKey = key, wereadEnabled = enabled) },
        cache,
    )

/** 构造统计回包文本。 */
internal fun statsBody(fields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
    buildJsonObject(fields).toString()

/** 构造只含单个统计接口的传输。 */
internal fun statsTransport(body: String): FakeTransport =
    FakeTransport { request -> if (requestName(request) == "/readdata/detail") RawResponse(200, body) else null }

/** 构造含笔记概览与单本书内容的传输。 */
internal fun notesTransport(
    notebooks: String,
    marks: String? = null,
    reviews: String? = null,
): FakeTransport =
    FakeTransport { request ->
        when (requestName(request)) {
            "/user/notebooks" -> RawResponse(200, notebooks)
            "/book/bookmarklist" -> marks?.let { RawResponse(200, it) }
            "/review/list/mine" -> reviews?.let { RawResponse(200, it) }
            else -> null
        }
    }

/** 构造一本笔记书的概览回包。 */
internal fun notebooksBody(
    bookId: String = "b1",
    title: String = "测试书籍",
    sort: Long = 100L,
    hasMore: Int = 0,
    total: Int = 1,
): String =
    buildJsonObject {
        put("hasMore", hasMore)
        put("totalNoteCount", total)
        put(
            "books",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("bookId", bookId)
                        put("sort", sort)
                        put("book", buildJsonObject { put("title", title) })
                    },
                )
            },
        )
    }.toString()
