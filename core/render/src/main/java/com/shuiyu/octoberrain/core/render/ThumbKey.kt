package com.shuiyu.octoberrain.core.render

import java.security.MessageDigest

/**
 * 缩略图缓存键的摘要计算。
 *
 * 键由来源路径、文件修改时间与目标长边共同决定，结果只保留 SHA-256 十六进制串，
 * 不把明文路径写入文件名或索引。纯计算对象，不引用 Android 类型。
 */
object ThumbKey {
    /** 摘要版本标记，缓存格式变化时递增即可让旧键整体失效。 */
    private const val VERSION = "thumb-v1"

    /** 字段分隔符，选择不会出现在常规路径中的控制字符。 */
    private const val SEPARATOR = '\u0001'

    /**
     * 计算缓存键。
     *
     * 参数 path 为源文件绝对路径，modifiedMillis 为文件修改时间，targetEdge 为期望长边；
     * 任一参数变化都会得到不同键，因此文件改写或尺寸调整后旧条目自然失效。
     */
    fun of(
        path: String,
        modifiedMillis: Long,
        targetEdge: Int,
    ): String {
        val material =
            buildString {
                append(VERSION)
                append(SEPARATOR)
                append(path)
                append(SEPARATOR)
                append(modifiedMillis)
                append(SEPARATOR)
                append(targetEdge)
            }
        return sha256Hex(material)
    }

    /**
     * 计算仅与路径相关的摘要，供缓存索引按来源分组。
     *
     * 参数 path 为源文件绝对路径；结果同样为十六进制串，不含明文路径。
     */
    fun pathDigest(path: String): String = sha256Hex("$VERSION$SEPARATOR$path")

    /** 计算 UTF-8 文本的 SHA-256 十六进制摘要，长度为 64 个字符。 */
    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            builder.append(HEX[(byte.toInt() shr 4) and 0x0F])
            builder.append(HEX[byte.toInt() and 0x0F])
        }
        return builder.toString()
    }

    /** 十六进制字符表，索引与半字节取值对应。 */
    private val HEX = "0123456789abcdef".toCharArray()
}
