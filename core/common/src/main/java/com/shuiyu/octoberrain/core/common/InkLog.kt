package com.shuiyu.octoberrain.core.common

import android.util.Log

/**
 * 统一日志入口。
 *
 * 开发期输出到 logcat；发布构建由 R8 移除调试级调用，保留警告以上级别。
 * 参数 message 为日志正文，tag 默认取调用方类名，便于按模块过滤。
 */
object InkLog {
    /** 调试日志，仅开发构建保留。 */
    fun d(
        tag: String,
        message: String,
    ) {
        Log.d(tag, message)
    }

    /** 提示日志，记录可恢复的异常路径。 */
    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        Log.w(tag, message, throwable)
    }

    /** 错误日志，记录导致功能不可用的异常。 */
    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        Log.e(tag, message, throwable)
    }
}

/**
 * 结果包装类型，区分成功值与失败原因，避免在各层抛异常穿透。
 *
 * 参数 T 为成功时携带的数据类型。
 */
sealed interface Outcome<out T> {
    data class Ok<T>(
        val value: T,
    ) : Outcome<T>

    data class Err(
        val reason: String,
        val cause: Throwable? = null,
    ) : Outcome<Nothing>

    /** 取得成功值，失败时返回提供的默认值。 */
    fun getOrElse(fallback: @UnsafeVariance T): T =
        when (this) {
            is Ok -> value
            is Err -> fallback
        }
}
