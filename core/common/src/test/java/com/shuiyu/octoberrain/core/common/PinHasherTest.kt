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
    fun `轮数提升后旧 PBKDF2 摘要仍可校验`() {
        val low = PinHasher.hash("2468", salt, 1_000)
        assertEquals(1_000, PinHasher.iterationsOf(low))
        assertTrue(PinHasher.verify("2468", salt, low))
        assertTrue(PinHasher.needsUpgrade(low, PinHasher.DEFAULT_ITERATIONS))
        val high = PinHasher.hash("2468", salt, PinHasher.effectiveIterations(1))
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.iterationsOf(high))
        assertFalse(PinHasher.needsUpgrade(high, PinHasher.DEFAULT_ITERATIONS))
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
    fun `配置轮数低于默认值时提升到默认值`() {
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.effectiveIterations(1))
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.effectiveIterations(0))
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
}
