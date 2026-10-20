package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.json.Json

/**
 * 统一 JSON 编解码器。
 *
 * 宽容未知字段，便于配置结构演进；编码包含默认值，保证往返一致。
 */
object AppJson {
    val format: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            explicitNulls = false
        }
}
