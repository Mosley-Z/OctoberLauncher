package com.shuiyu.octoberrain.feature.content.data

import com.shuiyu.octoberrain.core.common.InkLog
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 网关原始响应：HTTP 状态与响应体文本。 */
internal class RawResponse(
    val status: Int,
    val body: String,
)

/**
 * 微信读书网关的传输接口。
 *
 * 抽出该接口后，统计与笔记解析可以在本地 JVM 中以固定响应验证，不依赖真实网络。
 */
internal interface WereadTransport {
    /**
     * 发起一次 POST 请求。
     *
     * @param url 完整请求地址
     * @param authorization 完整的 Authorization 头内容
     * @param body JSON 请求体
     * @param timeoutMs 连接与读取超时毫秒数
     * @return 响应状态与文本；网络异常返回 null
     */
    fun post(
        url: String,
        authorization: String,
        body: String,
        timeoutMs: Int,
    ): RawResponse?
}

/** 基于 HttpURLConnection 的默认传输实现。 */
internal class HttpWereadTransport : WereadTransport {
    override fun post(
        url: String,
        authorization: String,
        body: String,
        timeoutMs: Int,
    ): RawResponse? {
        var connection: HttpURLConnection? = null
        return try {
            connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", authorization)
                }
            connection.outputStream.use { output -> output.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            RawResponse(status, text)
        } catch (error: IOException) {
            InkLog.w("OctoberRain", "微信读书请求 $url 失败：${error.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
