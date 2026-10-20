package com.shuiyu.octoberrain.core.common

/**
 * 十六进制编码与解码工具。
 *
 * 只服务于 [PinHasher] 的盐与摘要文本转换，保持在包内可见；不依赖 Android 框架。
 */
internal object HexCodec {
    private const val HEX_DIGITS = "0123456789abcdef"
    private const val RADIX = 16

    /**
     * 将字节数组转为小写十六进制串。
     *
     * @param bytes 原始字节
     * @return 十六进制表示
     */
    fun encode(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[value ushr 4])
            builder.append(HEX_DIGITS[value and 0x0F])
        }
        return builder.toString()
    }

    /**
     * 解析十六进制串。
     *
     * @param value 十六进制文本
     * @return 原始字节；长度为奇数或含非法字符时返回 null
     */
    fun decode(value: String): ByteArray? {
        if (value.isEmpty() || value.length % 2 != 0) return null
        val digits = IntArray(value.length) { index -> Character.digit(value[index], RADIX) }
        return if (digits.any { it < 0 }) {
            null
        } else {
            ByteArray(digits.size / 2) { index ->
                ((digits[index * 2] shl 4) or digits[index * 2 + 1]).toByte()
            }
        }
    }
}
