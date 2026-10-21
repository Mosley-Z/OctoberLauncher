package com.shuiyu.octoberrain.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/** [PinMigration] 的单元测试：轮数迁移、无需迁移与写盘失败。 */
class PinMigrationTest {
    private val salt = "00112233445566778899aabbccddeeff"

    @Test
    fun `十二万轮摘要校验通过后迁移为新默认轮数`() {
        val legacy = PinHasher.hash("1234", salt, LEGACY_ROUNDS)
        val store = FakeConfigStore(lockConfig(legacy, LEGACY_ROUNDS))
        val migration = PinMigration(store)

        assertTrue(PinHasher.verify("1234", salt, legacy))
        assertTrue(migration.onVerified("1234", salt, legacy, PinHasher.DEFAULT_ITERATIONS))

        val stored =
            store.state.value.lock.pinHash
                .orEmpty()
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.iterationsOf(stored))
        assertEquals(PinHasher.DEFAULT_ITERATIONS, store.state.value.lock.iterations)
        assertTrue(PinHasher.verify("1234", salt, stored))
    }

    @Test
    fun `轮数一致时保持原摘要`() {
        val hash = PinHasher.hash("2468", salt, PinHasher.DEFAULT_ITERATIONS)
        val store = FakeConfigStore(lockConfig(hash, PinHasher.DEFAULT_ITERATIONS))
        val migration = PinMigration(store)

        assertTrue(migration.onVerified("2468", salt, hash, PinHasher.DEFAULT_ITERATIONS))
        assertEquals(hash, store.state.value.lock.pinHash)
    }

    @Test
    fun `旧格式摘要迁移为 PBKDF2 格式`() {
        val legacy = legacyDigest(salt, "0000")
        val store = FakeConfigStore(lockConfig(legacy, iterations = 0))
        val migration = PinMigration(store)

        assertTrue(migration.onVerified("0000", salt, legacy, PinHasher.DEFAULT_ITERATIONS))
        val stored =
            store.state.value.lock.pinHash
                .orEmpty()
        assertEquals(PinHasher.DEFAULT_ITERATIONS, PinHasher.iterationsOf(stored))
        assertTrue(PinHasher.verify("0000", salt, stored))
    }

    @Test
    fun `迁移写入失败时保留原摘要且不抛出异常`() {
        val legacy = PinHasher.hash("1124", salt, LEGACY_ROUNDS)
        val store = FakeConfigStore(lockConfig(legacy, LEGACY_ROUNDS), failWrites = true)
        val migration = PinMigration(store)

        assertFalse(migration.onVerified("1124", salt, legacy, PinHasher.DEFAULT_ITERATIONS))
        assertEquals(legacy, store.state.value.lock.pinHash)
    }

    /**
     * 构造只含锁屏字段的配置。
     *
     * @param pinHash 已保存的摘要
     * @param iterations 配置中的轮数
     * @return 测试用配置
     */
    private fun lockConfig(
        pinHash: String,
        iterations: Int,
    ): LauncherConfig =
        LauncherConfig(
            lock = LockConfig(pinHash = pinHash, salt = salt, iterations = iterations, enabled = true),
        )

    /**
     * 复算旧格式摘要，用于构造兼容分支的输入。
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

    /** 内存配置存储，可选择让写入抛出异常以覆盖写盘失败路径。 */
    private class FakeConfigStore(
        initial: LauncherConfig,
        private val failWrites: Boolean = false,
    ) : ConfigStore {
        private val flow = MutableStateFlow(initial)

        override val state: StateFlow<LauncherConfig> = flow

        override fun edit(transform: (LauncherConfig) -> LauncherConfig) {
            if (failWrites) error("写盘失败")
            flow.value = transform(flow.value)
        }
    }

    private companion object {
        /** 历史版本写入配置的默认轮数。 */
        const val LEGACY_ROUNDS: Int = 120_000
    }
}
