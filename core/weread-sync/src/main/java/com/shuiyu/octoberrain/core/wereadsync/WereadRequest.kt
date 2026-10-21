package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.JsonObject

/** 可替换的网关请求，失败返回空值；业务解析与 Android 网络实现分离。 */
fun interface WereadRequest {
    fun request(
        apiName: String,
        business: JsonObject,
    ): JsonObject?
}
