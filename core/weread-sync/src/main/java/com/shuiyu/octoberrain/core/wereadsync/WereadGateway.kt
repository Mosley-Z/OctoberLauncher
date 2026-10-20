package com.shuiyu.octoberrain.core.wereadsync

import com.shuiyu.octoberrain.core.common.InkLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 微信读书网关的原始取数实现。
 *
 * 只负责按网关协议取得 JSON 文本，不做业务解析：解析归属各调用方，避免把阅读统计的
 * 字段规则散落在网络层。协议与微读墨记 V1.3.1 相同，为 `POST /api/agent/gateway`、
 * `Authorization: Bearer <key>`、请求体带 `api_name` 与 `skill_version`。
 *
 * @param apiKeyProvider 密钥读取，每次请求前调用，便于运行时更换密钥
 * @param endpoint 网关地址，默认官方地址
 */
class WereadGateway(
    private val apiKeyProvider: () -> String,
    private val endpoint: String = DEFAULT_ENDPOINT,
) {
    /**
     * 调用一次网关接口。
     *
     * @param apiName 接口名，例如 `/user/shelf/sync`
     * @param business 业务参数，会与 `api_name`、`skill_version` 合并为请求体
     * @param timeoutMs 连接与读取超时
     * @return 响应对象，网络失败、密钥无效或业务错误码非零时返回失败结果
     */
    fun request(
        apiName: String,
        business: JsonObject = JsonObject(emptyMap()),
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): WereadGatewayResult {
        val apiKey = apiKeyProvider().trim()
        return if (apiKey.isEmpty()) {
            WereadGatewayResult.Failure(WereadGatewayError.MISSING_KEY, "未配置密钥")
        } else {
            call(apiKey, apiName, business, timeoutMs)
        }
    }

    /** 组装请求体、发送并校验，未配置密钥之外的分支都在这里。 */
    private fun call(
        apiKey: String,
        apiName: String,
        business: JsonObject,
        timeoutMs: Int,
    ): WereadGatewayResult {
        val body =
            buildJsonObject {
                put("api_name", apiName)
                put("skill_version", SKILL_VERSION)
                business.forEach { (name, value) -> put(name, value) }
            }
        val raw =
            runCatching { post(apiKey, body.toString(), timeoutMs) }
                .getOrElse { error ->
                    return WereadGatewayResult.Failure(WereadGatewayError.TRANSPORT, error.message.orEmpty())
                }
        return validate(raw)
    }

    /** 发送请求并读取响应文本，非 2xx 时返回状态码供上层分类。 */
    private fun post(
        apiKey: String,
        body: String,
        timeoutMs: Int,
    ): RawResponse {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.outputStream.use { stream -> stream.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() }.orEmpty()
            RawResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }

    /** 校验 HTTP 状态与业务错误码，把可重试与不可重试分开。 */
    private fun validate(response: RawResponse): WereadGatewayResult =
        when {
            response.status == HTTP_UNAUTHORIZED -> {
                InkLog.w(TAG, "微信读书密钥无效或已过期")
                WereadGatewayResult.Failure(WereadGatewayError.INVALID_KEY, "密钥无效")
            }
            response.status !in 200..299 -> {
                InkLog.w(TAG, "微信读书网关返回 HTTP ${response.status}")
                WereadGatewayResult.Failure(WereadGatewayError.TRANSPORT, "HTTP ${response.status}")
            }
            else -> businessResult(response.body)
        }

    /** 解析响应文本并按业务码返回结果。 */
    private fun businessResult(body: String): WereadGatewayResult {
        val json =
            runCatching { Json.parseToJsonElement(body).jsonObject }
                .getOrElse { error ->
                    InkLog.w(TAG, "微信读书网关响应无法解析：${error.message}")
                    return WereadGatewayResult.Failure(WereadGatewayError.MALFORMED, "响应无法解析")
                }
        val code = businessCode(json)
        return if (code == null || code == 0) {
            WereadGatewayResult.Success(json)
        } else {
            failureForCode(code)
        }
    }

    /** 把非零业务码映射为失败结果。 */
    private fun failureForCode(code: Int): WereadGatewayResult {
        val invalidKey = code == CODE_INVALID_KEY || code == HTTP_UNAUTHORIZED
        val error = if (invalidKey) WereadGatewayError.INVALID_KEY else WereadGatewayError.BUSINESS
        InkLog.w(TAG, "微信读书网关业务码 $code")
        return WereadGatewayResult.Failure(error, "业务码 $code")
    }

    /** 读取业务错误码，兼容 `errcode` 与 `code` 两种键名。 */
    private fun businessCode(json: JsonObject): Int? {
        val value =
            (json[KEY_ERRCODE] ?: json[KEY_CODE])?.let { element ->
                runCatching { element.toString().toInt() }.getOrNull()
            }
        return value
    }

    companion object {
        private const val TAG = "WereadGateway"

        /** 官方网关地址。 */
        const val DEFAULT_ENDPOINT: String = "https://i.weread.qq.com/api/agent/gateway"

        /** 与微读墨记一致的技能版本号。 */
        const val SKILL_VERSION: String = "1.0.5"

        /** 默认超时，与微读墨记的十五秒保持一致。 */
        const val DEFAULT_TIMEOUT_MS: Int = 15000

        private const val HTTP_UNAUTHORIZED = 401
        private const val CODE_INVALID_KEY = -2012
        private const val KEY_ERRCODE = "errcode"
        private const val KEY_CODE = "code"
    }
}

/** 网关单次调用的结果。 */
sealed interface WereadGatewayResult {
    /** 请求成功，携带解析后的响应对象。 */
    data class Success(
        val payload: JsonObject,
    ) : WereadGatewayResult

    /** 请求失败，携带分类与说明。 */
    data class Failure(
        val error: WereadGatewayError,
        val message: String,
    ) : WereadGatewayResult
}

/** 网关失败分类，用于界面提示与重试策略。 */
enum class WereadGatewayError {
    /** 未配置密钥。 */
    MISSING_KEY,

    /** 密钥无效或过期，需重新录入。 */
    INVALID_KEY,

    /** 网络或服务端错误，可重试。 */
    TRANSPORT,

    /** 响应结构不合法。 */
    MALFORMED,

    /** 业务错误码非零。 */
    BUSINESS,
}

/** 一次 HTTP 响应的最小结构。 */
private data class RawResponse(
    val status: Int,
    val body: String,
)

/** 判断异常是否为可重试的网络问题。 */
internal fun Throwable.isRetryableTransport(): Boolean = this is IOException
