package com.shuiyu.octoberrain.feature.wereadapp.ui.qr

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.net.URLDecoder

/**
 * 解析后的录入请求。
 *
 * 只保留路由需要的字段，请求头与表单正文在解析阶段读取完毕。
 *
 * @param method 请求方法
 * @param path 不含查询串的请求路径
 * @param token 查询串中的一次性令牌，缺失时为空字符串
 * @param body 表单正文，仅 POST 请求读取
 */
internal data class KeyEntryRequest(
    val method: String,
    val path: String,
    val token: String,
    val body: String,
)

/**
 * 局域网录入服务的 HTTP 编解码工具。
 *
 * 只处理最小可用的请求解析与响应写出，不依赖第三方 HTTP 库，
 * 以便在离线环境与单元测试中直接使用。
 */
internal object KeyEntryHttp {
    /**
     * 从套接字读取并解析一个请求。
     *
     * 请求行格式非法时返回 null，由调用方回以 400。
     *
     * @param client 客户端连接
     * @param maxBodyBytes 表单正文读取上限，超出部分截断
     */
    fun parse(
        client: Socket,
        maxBodyBytes: Int,
    ): KeyEntryRequest? {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val parts = reader.readLine()?.split(' ')?.takeIf { it.size >= 2 }
        val contentLength = contentLength(reader)
        return parts?.let {
            val target = it[1]
            KeyEntryRequest(
                method = it[0],
                path = target.substringBefore('?'),
                token = parameter(target.substringAfter('?', ""), "t"),
                body = if (it[0] == "POST") readBody(reader, contentLength, maxBodyBytes) else "",
            )
        }
    }

    /** 读取请求头并取出 Content-Length，缺失时按 0 处理。 */
    private fun contentLength(reader: BufferedReader): Int {
        var length = 0
        var line = reader.readLine()
        while (line != null && line.isNotEmpty()) {
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                length = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
            line = reader.readLine()
        }
        return length
    }

    /** 按声明长度读取表单正文，上限之外的部分丢弃。 */
    private fun readBody(
        reader: BufferedReader,
        length: Int,
        max: Int,
    ): String {
        val size = length.coerceIn(0, max)
        val buffer = CharArray(size)
        var offset = 0
        while (offset < size) {
            val read = reader.read(buffer, offset, size - offset)
            if (read <= 0) break
            offset += read
        }
        return String(buffer, 0, offset)
    }

    /**
     * 从查询串或表单正文中取出指定参数。
     *
     * @param text 形如 a=1&b=2 的文本
     * @param name 参数名
     * @return 解码后的参数值，缺失时为空字符串
     */
    fun parameter(
        text: String,
        name: String,
    ): String =
        text
            .split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault("") }
            ?: ""

    /**
     * 写出完整响应并关闭连接。
     *
     * @param output 客户端输出流
     * @param status HTTP 状态码
     * @param body 页面正文
     */
    fun respond(
        output: OutputStream,
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header =
            buildString {
                append("HTTP/1.1 $status ${statusText(status)}\r\n")
                append("Content-Type: text/html; charset=utf-8\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n\r\n")
            }
        output.write(header.toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    /** 状态码对应的英文说明。 */
    private fun statusText(status: Int): String =
        when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            500 -> "Internal Server Error"
            else -> "Unknown"
        }
}
