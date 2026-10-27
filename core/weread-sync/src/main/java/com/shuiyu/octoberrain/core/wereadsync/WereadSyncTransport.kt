package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.JsonObject

/** 同步请求统一保留错误分类，自动批次遇到错误后停止继续联网。 */
class WereadSyncTransport(
    private val send: (String, JsonObject, Int) -> WereadGatewayResult,
    private val budget: AutomaticSyncBudget? = null,
) : WereadRequest {
    private var failure: WereadGatewayError? = null

    val failureMessage: String?
        get() =
            when (failure) {
                WereadGatewayError.MISSING_KEY -> "尚未配置微信读书 API Key"
                WereadGatewayError.INVALID_KEY -> "微信读书密钥无效，请在连接设置中更新"
                WereadGatewayError.TRANSPORT -> "网络或服务暂不可用，已保留密钥与已有数据，请稍后重试"
                WereadGatewayError.MALFORMED, WereadGatewayError.BUSINESS -> "微信读书返回异常，已保留已有数据，请稍后重试"
                null -> if (budget?.stopped == true) "自动刷新已暂停或达到本轮限额，保留已有数据" else null
            }

    override fun request(
        apiName: String,
        business: JsonObject,
    ): JsonObject? {
        val timeout = if (budget == null) WereadGateway.DEFAULT_TIMEOUT_MS else budget.nextTimeoutMillis()
        val failed = failure == WereadGatewayError.INVALID_KEY || (budget != null && failure != null)
        return if (timeout == null || failed) {
            null
        } else {
            when (val response = send(apiName, business, timeout)) {
                is WereadGatewayResult.Success -> response.payload
                is WereadGatewayResult.Failure -> {
                    failure = response.error
                    null
                }
            }
        }
    }
}
