package com.shuiyu.octoberrain.feature.wereadapp.ui.qr

/**
 * 微信读书接口密钥格式校验。
 *
 * 密钥由 `wrk-` 前缀与后缀组成，后缀允许大小写字母、数字、下划线与连字符。
 * 校验只判断输入格式，不判断密钥在服务端是否有效。
 */
internal object WereadKeyValidator {
    /** 密钥前缀。 */
    private const val PREFIX = "wrk-"

    /** 后缀最短长度。 */
    private const val PAYLOAD_MIN_LENGTH = 8

    /** 后缀最长长度。 */
    private const val PAYLOAD_MAX_LENGTH = 128

    /** 后缀允许的字符集合。 */
    private val PAYLOAD_PATTERN = Regex("^[A-Za-z0-9_-]+$")

    /**
     * 校验密钥格式。
     *
     * @param key 用户输入，调用方需先去除首尾空白。
     * @return 格式合法时返回 null，否则返回可直接展示的错误说明。
     */
    fun validateOrNull(key: String): String? {
        val payload = key.removePrefix(PREFIX)
        return when {
            key.isEmpty() -> "密钥不能为空"
            !key.startsWith(PREFIX) -> "密钥应以 $PREFIX 开头"
            payload.length < PAYLOAD_MIN_LENGTH -> "密钥长度不足"
            payload.length > PAYLOAD_MAX_LENGTH -> "密钥长度超出限制"
            !PAYLOAD_PATTERN.matches(payload) -> "密钥只能包含字母、数字、下划线与连字符"
            else -> null
        }
    }
}
