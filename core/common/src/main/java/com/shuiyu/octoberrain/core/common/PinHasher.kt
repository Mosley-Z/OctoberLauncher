package com.shuiyu.octoberrain.core.common

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN 摘要与校验的纯逻辑工具。
 *
 * 新格式摘要为 `pbkdf2$轮数$十六进制摘要`，轮数写入摘要串，校验时以串内轮数为准，
 * 使轮数提升后既有摘要仍可校验；旧格式为单次 SHA-256(盐串 + PIN) 的 64 位十六进制串，
 * 不带前缀，由调用方在首次成功校验后升级。本对象不依赖 Android 框架，可直接在本地 JVM 测试。
 *
 * 失败次数与退避时长的计算见 [PinLockPolicy]。
 */
object PinHasher {
    /** 新摘要使用的默认轮数。 */
    const val DEFAULT_ITERATIONS = 20_000

    /**
     * 历史版本持久化的默认轮数。
     *
     * 旧配置会把该值写入 `lock.iterations`，并不代表用户的选择；计算时按未配置处理，
     * 使已升级的安装重新设置密码后改用 [DEFAULT_ITERATIONS]。
     */
    private const val LEGACY_DEFAULT_ITERATIONS = 120_000

    /** 盐的字节数，与旧格式保持一致。 */
    const val SALT_BYTES = 16

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_BITS = 256
    private const val PREFIX = "pbkdf2"
    private const val SEPARATOR = '$'

    /**
     * 生成 16 字节随机盐并转为小写十六进制串。
     *
     * @return 32 位十六进制盐串
     */
    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return HexCodec.encode(bytes)
    }

    /**
     * 计算实际参与摘要的轮数。
     *
     * 配置值大于零且不等于历史默认轮数时按配置值使用，便于按机型实测结果调整；
     * 配置缺失、非正或等于历史默认轮数时使用 [DEFAULT_ITERATIONS]。
     * 该语义使新设置的密码采用新默认轮数，同时把历史默认值视为未配置。
     *
     * @param configured 配置中的轮数
     * @return 用于计算的轮数
     */
    fun effectiveIterations(configured: Int): Int =
        configured.takeIf { it > 0 && it != LEGACY_DEFAULT_ITERATIONS } ?: DEFAULT_ITERATIONS

    /**
     * 计算 PBKDF2 摘要。
     *
     * 轮数按传入值计算，低于 1 时取 1；调用方应先用 [effectiveIterations] 把配置轮数换算为实际轮数。
     *
     * @param pin 四位数字 PIN
     * @param saltHex 十六进制盐串
     * @param iterations 迭代轮数
     * @return `pbkdf2$轮数$十六进制摘要` 形式的摘要串
     */
    fun hash(
        pin: String,
        saltHex: String,
        iterations: Int,
    ): String {
        val actual = iterations.coerceAtLeast(1)
        val derived = derive(pin, saltHex, actual)
        return "$PREFIX$SEPARATOR$actual$SEPARATOR${HexCodec.encode(derived)}"
    }

    /**
     * 校验 PIN 与已存摘要是否匹配。
     *
     * 摘要无 `pbkdf2` 前缀时按旧版单次 SHA-256 校验；摘要无法解析或盐为空时返回 false。
     *
     * @param pin 待校验的 PIN
     * @param saltHex 十六进制盐串
     * @param stored 已保存的摘要串
     * @return 校验通过返回 true
     */
    fun verify(
        pin: String,
        saltHex: String,
        stored: String,
    ): Boolean =
        when {
            saltHex.isBlank() || stored.isBlank() -> false
            isLegacy(stored) ->
                MessageDigest.isEqual(
                    legacyDigest(saltHex, pin).toByteArray(Charsets.UTF_8),
                    stored.lowercase().toByteArray(Charsets.UTF_8),
                )
            else -> verifyPbkdf2(pin, saltHex, stored)
        }

    /**
     * 判断摘要是否为旧版单次哈希格式。
     *
     * @param stored 已保存的摘要串
     * @return 旧格式返回 true
     */
    fun isLegacy(stored: String): Boolean = !stored.startsWith("$PREFIX$SEPARATOR")

    /**
     * 读取摘要串中登记的轮数。
     *
     * @param stored 已保存的摘要串
     * @return PBKDF2 摘要的轮数；旧格式或无法解析时返回 null
     */
    fun iterationsOf(stored: String): Int? {
        if (isLegacy(stored)) return null
        val parts = stored.split(SEPARATOR)
        return if (parts.size == 3) parts[1].toIntOrNull() else null
    }

    /**
     * 判断摘要是否需要按目标轮数重算。
     *
     * 旧格式摘要与登记轮数不等于目标轮数的摘要都需要重算，包括高于目标值的历史默认轮数，
     * 使校验耗时回到当前默认值；轮数一致时保持原摘要，避免无谓的重复派生。
     *
     * @param stored 已保存的摘要串
     * @param targetIterations 目标轮数
     * @return 旧格式或登记轮数与目标值不同时返回 true
     */
    fun needsUpgrade(
        stored: String,
        targetIterations: Int,
    ): Boolean {
        val current = iterationsOf(stored)
        return current == null || current != effectiveIterations(targetIterations)
    }

    /**
     * 校验 PBKDF2 格式的摘要。
     *
     * @param pin 待校验的 PIN
     * @param saltHex 十六进制盐串
     * @param stored 已保存的摘要串
     * @return 校验通过返回 true
     */
    private fun verifyPbkdf2(
        pin: String,
        saltHex: String,
        stored: String,
    ): Boolean {
        val parts = stored.split(SEPARATOR)
        val valid = parts.size == 3 && parts[0] == PREFIX
        val iterations = if (valid) parts[1].toIntOrNull() else null
        return valid &&
            iterations != null &&
            iterations > 0 &&
            MessageDigest.isEqual(
                HexCodec.encode(derive(pin, saltHex, iterations)).toByteArray(Charsets.UTF_8),
                parts[2].lowercase().toByteArray(Charsets.UTF_8),
            )
    }

    /**
     * 以 PBKDF2 派生密钥。
     *
     * @param pin PIN 明文
     * @param saltHex 十六进制盐串
     * @param iterations 迭代轮数
     * @return 派生密钥字节
     */
    private fun derive(
        pin: String,
        saltHex: String,
        iterations: Int,
    ): ByteArray {
        val salt = HexCodec.decode(saltHex) ?: saltHex.toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * 计算旧版单次摘要。
     *
     * 与旧实现保持一致：先拼接十六进制盐串与 PIN 的 UTF-8 字节，再做一次 SHA-256。
     *
     * @param saltHex 十六进制盐串
     * @param pin PIN 明文
     * @return 小写十六进制摘要
     */
    private fun legacyDigest(
        saltHex: String,
        pin: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltHex.toByteArray(Charsets.UTF_8))
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return HexCodec.encode(digest.digest())
    }
}
