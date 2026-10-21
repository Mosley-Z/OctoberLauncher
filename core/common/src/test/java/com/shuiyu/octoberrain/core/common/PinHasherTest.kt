package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/** [PinHasher] 与 [PinLockPolicy] 的单元测试：摘要格式、校验、旧格式兼容与失败退避。 */
class PinHasherTest {
    private val salt = "00112233445566778899aabbccddeeff"

    @Test
    fun `PBKDF2 摘要带轮数前缀且校验通过`() {
        val hash = PinHasher.hash("1234", salt, PinHasher.DEFAULT_ITERATIONS)
        assertTrue(hash.startsWith("pbkdf2$"))
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.iterationsOf(hash))
        assertTrue(PinHasher.verify("1234", salt, hash))
        assertFalse(PinHasher.isLegacy(hash))
    }

    @Test
    fun `错误 PIN 的校验返回假`() {
        val hash = PinHasher.hash("1234", salt, 1_000)
        assertFalse(PinHasher.verify("1235", salt, hash))
        assertFalse(PinHasher.verify("1234", "ffeeddccbbaa99887766554433221100", hash))
    }

    @Test
    fun `轮数低于目标的摘要仍可校验并判定需要重算`() {
        val low = PinHasher.hash("2468", salt, 1_000)
        assertEquals(1_000, PinHasher.iterationsOf(low))
        assertTrue(PinHasher.verify("2468", salt, low))
        assertTrue(PinHasher.needsUpgrade(low, PinHasher.DEFAULT_ITERATIONS))
        assertFalse(PinHasher.needsUpgrade(low, 1_000))
    }

    @Test
    fun `旧版单次摘要仍可校验并判定需要升级`() {
        val legacy = legacyDigest(salt, "0000")
        assertTrue(PinHasher.isLegacy(legacy))
        assertNull(PinHasher.iterationsOf(legacy))
        assertTrue(PinHasher.needsUpgrade(legacy, PinHasher.DEFAULT_ITERATIONS))
        assertTrue(PinHasher.verify("0000", salt, legacy))
        assertFalse(PinHasher.verify("0001", salt, legacy))
    }

    @Test
    fun `历史十二万轮摘要可校验并判定需要迁移`() {
        val hash = PinHasher.hash("1124", salt, LEGACY_ROUNDS)
        assertEquals(LEGACY_ROUNDS, PinHasher.iterationsOf(hash))
        assertTrue(PinHasher.verify("1124", salt, hash))
        assertFalse(PinHasher.verify("1125", salt, hash))
        assertTrue(PinHasher.needsUpgrade(hash, PinHasher.DEFAULT_ITERATIONS))
    }

    @Test
    fun `新默认轮数的摘要可通过校验`() {
        val hash = PinHasher.hash("8642", salt, PinHasher.DEFAULT_ITERATIONS)
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.iterationsOf(hash))
        assertTrue(PinHasher.verify("8642", salt, hash))
        assertFalse(PinHasher.needsUpgrade(hash, PinHasher.DEFAULT_ITERATIONS))
    }

    @Test
    fun `配置轮数为零负数或历史默认值时回落到新默认值`() {
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.effectiveIterations(0))
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.effectiveIterations(-7))
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.effectiveIterations(LEGACY_ROUNDS))
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.effectiveIterations(PinHasher.DEFAULT_ITERATIONS))
    }

    @Test
    fun `显式配置的其它轮数被保留`() {
        assertEquals(5_000, PinHasher.effectiveIterations(5_000))
        assertEquals(200_000, PinHasher.effectiveIterations(200_000))
    }

    @Test
    fun `盐长度为十六字节且为小写十六进制`() {
        val generated = PinHasher.newSalt()
        assertEquals(PinHasher.SALT_BYTES * 2, generated.length)
        assertTrue(generated.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(generated == PinHasher.newSalt())
    }

    @Test
    fun `连续失败次数决定剩余可尝试次数`() {
        assertEquals(PinLockPolicy.MAX_ATTEMPTS, PinLockPolicy.remainingAttempts(0))
        assertEquals(1, PinLockPolicy.remainingAttempts(PinLockPolicy.MAX_ATTEMPTS - 1))
        assertEquals(0, PinLockPolicy.remainingAttempts(PinLockPolicy.MAX_ATTEMPTS))
        assertEquals(0, PinLockPolicy.remainingAttempts(PinLockPolicy.MAX_ATTEMPTS + 3))
    }

    @Test
    fun `退避时长从第五次失败开始递增并封顶`() {
        assertEquals(0L, PinLockPolicy.backoffMillis(1))
        assertEquals(0L, PinLockPolicy.backoffMillis(4))
        assertEquals(30_000L, PinLockPolicy.backoffMillis(5))
        assertEquals(60_000L, PinLockPolicy.backoffMillis(6))
        assertEquals(120_000L, PinLockPolicy.backoffMillis(7))
        assertEquals(900_000L, PinLockPolicy.backoffMillis(20))
    }

    @Test
    fun `锁定剩余秒数向上取整`() {
        val now = 1_000_000L
        assertEquals(0L, PinLockPolicy.remainingLockoutSeconds(now, now))
        assertEquals(1L, PinLockPolicy.remainingLockoutSeconds(now + 1L, now))
        assertEquals(30L, PinLockPolicy.remainingLockoutSeconds(now + 30_000L, now))
        assertEquals(1L, PinLockPolicy.remainingLockoutSeconds(now + 500L, now))
        assertEquals(0L, PinLockPolicy.remainingLockoutSeconds(now - 5_000L, now))
    }

    @Test
    fun `空盐与空摘要的校验返回假`() {
        val hash = PinHasher.hash("1234", salt, 1_000)
        assertFalse(PinHasher.verify("1234", "", hash))
        assertFalse(PinHasher.verify("1234", salt, ""))
    }

    /**
     * 复算旧实现摘要，用于验证兼容分支。
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
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        /** 历史版本写入配置的默认轮数，用于构造旧摘要。 */
        const val LEGACY_ROUNDS: Int = 120_000
    }
}
