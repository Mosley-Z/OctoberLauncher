package com.shuiyu.octoberrain.feature.reader

import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.core.common.ReaderFormat
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException

/**
 * 阅读页错误文案映射。
 *
 * 把异常类型与当前格式转换为面向读者的中文说明，避免界面层重复拼装；
 * 只依赖异常与格式，不读取会话状态，便于独立测试与复用。
 */
internal object ReaderErrors {
    /**
     * 返回当前格式不支持时的说明。
     *
     * @param format 当前文档格式
     */
    fun unsupported(format: ReaderFormat): String =
        when (format) {
            ReaderFormat.EPUB -> "当前仅支持无加密资源的 UTF-8 重排版 EPUB，可使用其他应用打开"
            ReaderFormat.UNKNOWN -> "无法识别文件类型，可选择编码按纯文本阅读或使用其他应用打开"
            else -> "暂不支持此文档类型，可使用其他应用打开"
        }

    /**
     * 把异常转换为状态栏文案。
     *
     * @param error 打开、翻页或会话操作抛出的异常
     * @param format 当前文档格式，用于不支持说明
     */
    fun message(
        error: Exception,
        format: ReaderFormat,
    ): String =
        when {
            error is java.util.zip.ZipException ->
                "ZIP损坏、超出预算或使用不支持的加密/压缩格式，可使用其他应用打开"
            error is java.io.FileNotFoundException -> "源文件不存在或读取授权已失效"
            error is SecurityException -> "文档授权已失效，请从文件选择器重新打开"
            error is java.nio.charset.CharacterCodingException -> "编码无法识别，请在阅读设置中选择正确编码"
            error is ReaderSourceException -> sourceMessage(error, format)
            else -> "文档读取失败，可返回后重新打开"
        }

    /** 来源层异常按失败类型细分。 */
    private fun sourceMessage(
        error: ReaderSourceException,
        format: ReaderFormat,
    ): String =
        when (error.failure) {
            ReaderFailure.MISSING -> "源文件不存在"
            ReaderFailure.PERMISSION -> "无法读取文档，请重新授权"
            ReaderFailure.UNSUPPORTED -> unsupported(format)
            ReaderFailure.LIMIT -> "文档超出当前阅读预算，请在阅读设置中选择其他打开方式"
            ReaderFailure.PASSWORD -> "PDF受密码保护，当前系统引擎不支持解密，可用其他应用打开"
            ReaderFailure.RENDERER -> "文档显示已中断或超时，请返回后重新打开"
            else -> "文档无法解析"
        }
}
