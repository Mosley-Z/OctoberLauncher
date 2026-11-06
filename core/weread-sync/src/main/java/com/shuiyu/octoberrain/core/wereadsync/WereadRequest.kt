package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.JsonObject

/** 可替换的网关请求，失败返回空值；业务解析与 Android 网络实现分离。 */
fun interface WereadRequest {
    /** 全局断网、凭证失败或自动预算耗尽时结束批次；单书解析失败可继续。 */
    fun stopAfterFailure(): Boolean = false

    fun request(
        apiName: String,
        business: JsonObject,
    ): JsonObject?
}
