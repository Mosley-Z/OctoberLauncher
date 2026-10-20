package com.shuiyu.octoberrain.feature.settings.qr

import com.shuiyu.octoberrain.feature.settings.WereadKeyValidator
import java.io.IOException
import java.io.OutputStream
import java.net.Socket

/**
 * 单个连接的请求处理器。
 *
 * 负责解析请求、校验令牌、按路径与方法路由，并在提交合法密钥时把密钥回传调用方。
 * 处理器本身无状态，令牌由构造参数提供，便于在服务重启后复用到新的令牌。
 */
internal class KeyEntryHandler(
    private val token: () -> String,
) {
    /** 路由结果，包含状态码、页面正文与需要写入的密钥。 */
    private data class Outcome(
        val status: Int,
        val body: String,
        val key: String? = null,
    )

    /**
     * 处理一个客户端连接。
     *
     * @param client 客户端连接，处理结束后由本方法关闭
     * @return 提交合法的密钥，未收到合法密钥时返回 null
     */
    fun handle(client: Socket): String? {
        var output: OutputStream? = null
        try {
            client.soTimeout = SOCKET_TIMEOUT_MS
            output = client.getOutputStream()
            val outcome = route(KeyEntryHttp.parse(client, MAX_BODY_BYTES))
            KeyEntryHttp.respond(output, outcome.status, outcome.body)
            return outcome.key
        } catch (_: IOException) {
            val stream = output
            if (stream != null) {
                runCatching {
                    KeyEntryHttp.respond(stream, 500, KeyEntryPage.message("服务异常", "写入失败，请重试。"))
                }
            }
            return null
        } finally {
            runCatching { client.close() }
        }
    }

    /** 按令牌、路径与方法路由请求。 */
    private fun route(request: KeyEntryRequest?): Outcome {
        val currentToken = token()
        return when {
            request == null -> Outcome(400, KeyEntryPage.message("请求无效", "无法解析请求行。"))
            request.token != currentToken ->
                Outcome(403, KeyEntryPage.message("链接已失效", "页面令牌不匹配，请在电脑上重新生成。"))
            request.path != PATH -> Outcome(404, KeyEntryPage.message("页面不存在", "请重新扫描二维码。"))
            request.method == "GET" -> Outcome(200, KeyEntryPage.form(PATH, currentToken, null, ""))
            request.method != "POST" ->
                Outcome(405, KeyEntryPage.message("请求方式不支持", "该地址只接受表单提交。"))
            else -> submit(request.body, currentToken)
        }
    }

    /** 校验提交的密钥并给出结果。 */
    private fun submit(
        body: String,
        currentToken: String,
    ): Outcome {
        val rawKey = KeyEntryHttp.parameter(body, "key").trim()
        val error = WereadKeyValidator.validateOrNull(rawKey)
        return if (error == null) {
            Outcome(200, KeyEntryPage.message("密钥已接收", "电脑端已收到密钥，可以关闭本页面。"), rawKey)
        } else {
            Outcome(200, KeyEntryPage.form(PATH, currentToken, error, rawKey))
        }
    }

    /** 处理器内部常量。 */
    private companion object {
        /** 页面路径。 */
        const val PATH = "/k"

        /** 单次连接读取超时，单位毫秒。 */
        const val SOCKET_TIMEOUT_MS = 8000

        /** 请求体长度上限，单位字节。 */
        const val MAX_BODY_BYTES = 4096
    }
}
