package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JsonConfigStore 的写入原子性与迁移回写单元测试。
 *
 * 通过内存持久化实现注入读写成败，断言状态发布顺序、迁移结果回写行为，
 * 以及高版本配置不被降级改写。
 */
class JsonConfigStoreTest {
    /**
     * 内存持久化实现。
     *
     * 记录读写次数，可指定接下来若干次写入失败，用于覆盖写盘失败分支。
     *
     * @param initial 初始磁盘内容，null 表示尚未写入。
     */
    private class MemoryPersistence(
        initial: String? = null,
    ) : ConfigPersistence {
        private var stored: String? = initial

        /** 读取次数。 */
        var readCount = 0

        /** 写入次数，包含失败的尝试。 */
        var writeCount = 0

        /** 接下来连续失败的写入次数。 */
        var failNextWrites = 0

        override fun read(): String? {
            readCount += 1
            return stored
        }

        override fun write(value: String): Boolean {
            writeCount += 1
            if (failNextWrites > 0) {
                failNextWrites -= 1
                return false
            }
            stored = value
            return true
        }

        /** 当前磁盘内容。 */
        fun snapshot(): String? = stored
    }

    /** 把配置编码为与生产一致格式的 JSON 字符串。 */
    private fun encode(config: LauncherConfig): String =
        AppJson.format.encodeToString(LauncherConfig.serializer(), config)

    /** 解码持久化层中的 JSON 字符串。 */
    private fun decode(raw: String): LauncherConfig = AppJson.format.decodeFromString(LauncherConfig.serializer(), raw)

    /** 写盘成功后发布新状态，并把同一份配置写入持久化层。 */
    @Test
    fun `写盘成功后发布新值`() {
        val memory = MemoryPersistence()
        val store = JsonConfigStore(memory)

        store.edit { it.copy(appearance = it.appearance.copy(themeId = "night")) }

        assertEquals("night", store.state.value.appearance.themeId)
        assertEquals(1, memory.writeCount)
        assertEquals("night", decode(memory.snapshot()!!).appearance.themeId)
        assertNull(store.writeError.value)
    }

    /** 写盘失败时保留旧状态，磁盘内容不变，并记录失败原因。 */
    @Test
    fun `写盘失败保留旧值`() {
        val memory = MemoryPersistence()
        val store = JsonConfigStore(memory)
        val before = store.state.value

        memory.failNextWrites = 1
        store.edit { it.copy(appearance = it.appearance.copy(themeId = "night")) }

        assertEquals(before, store.state.value)
        assertNull(memory.snapshot())
        assertNotNull(store.writeError.value)
        assertEquals(1, memory.writeCount)
    }

    /** 写盘失败时回调收到同一异常。 */
    @Test
    fun `写盘失败触发回调`() {
        var reported: Throwable? = null
        val memory = MemoryPersistence()
        val store = JsonConfigStore(memory) { reported = it }

        memory.failNextWrites = 1
        store.edit { it.copy(appearance = it.appearance.copy(themeId = "night")) }

        assertNotNull(reported)
        assertEquals(store.writeError.value, reported)
    }

    /** 写盘失败后再次成功写入，错误字段清空且新值发布。 */
    @Test
    fun `写盘恢复后清除错误`() {
        val memory = MemoryPersistence()
        val store = JsonConfigStore(memory)

        memory.failNextWrites = 1
        store.edit { it.copy(appearance = it.appearance.copy(themeId = "night")) }
        assertNotNull(store.writeError.value)

        store.edit { it.copy(appearance = it.appearance.copy(themeId = "dusk")) }

        assertNull(store.writeError.value)
        assertEquals("dusk", store.state.value.appearance.themeId)
        assertEquals(2, memory.writeCount)
    }

    /** 变换结果与当前配置相等时不触发写入。 */
    @Test
    fun `相同配置不写盘`() {
        val memory = MemoryPersistence()
        val store = JsonConfigStore(memory)

        store.edit { it }

        assertEquals(0, memory.writeCount)
        assertNull(store.writeError.value)
    }

    /** 读取到低版本配置时，迁移结果立即回写持久化层。 */
    @Test
    fun `迁移结果立即回写`() {
        val memory = MemoryPersistence(encode(LauncherConfig(version = 6)))
        val store = JsonConfigStore(memory)

        assertEquals(LauncherConfig.CURRENT_VERSION, store.state.value.version)
        assertEquals(1, memory.writeCount)
        assertEquals(LauncherConfig.CURRENT_VERSION, decode(memory.snapshot()!!).version)
    }

    /** 迁移结果回写失败时，本次运行仍使用迁移后的配置，磁盘保持原版本。 */
    @Test
    fun `迁移回写失败仍按迁移结果运行`() {
        val memory = MemoryPersistence(encode(LauncherConfig(version = 6)))
        memory.failNextWrites = 1
        val store = JsonConfigStore(memory)

        assertEquals(LauncherConfig.CURRENT_VERSION, store.state.value.version)
        assertNotNull(store.writeError.value)
        assertEquals(6, decode(memory.snapshot()!!).version)
    }

    /**
     * 微信读书迁移状态与成就配置经写盘与重建读取后保持原值。
     *
     * 覆盖「导入完成 → 进程重启 → 修改其它设置」流程，避免两节在重新加载时
     * 回退默认值，随后被无关的配置编辑写回磁盘而丢失。
     */
    @Test
    fun `微信读书两节经重建读取后不回退默认值`() {
        val memory = MemoryPersistence()
        val first = JsonConfigStore(memory)
        first.edit {
            it.copy(
                wereadMigration =
                    it.wereadMigration.copy(
                        sourceVersionCode = 48,
                        importedAtMillis = 1_700_000_000_000L,
                        manifestDigest = "abc123",
                        complete = true,
                    ),
                wereadAchievement =
                    it.wereadAchievement.copy(rawJson = "{\"week\":1}", updatedAtMillis = 1_700_000_100_000L),
            )
        }

        val second = JsonConfigStore(memory)
        assertEquals(48, second.state.value.wereadMigration.sourceVersionCode)
        assertEquals(1_700_000_000_000L, second.state.value.wereadMigration.importedAtMillis)
        assertEquals("abc123", second.state.value.wereadMigration.manifestDigest)
        assertTrue(second.state.value.wereadMigration.complete)
        assertEquals("{\"week\":1}", second.state.value.wereadAchievement.rawJson)

        second.edit { it.copy(appearance = it.appearance.copy(themeId = "night")) }
        val persisted = decode(memory.snapshot()!!)
        assertEquals(48, persisted.wereadMigration.sourceVersionCode)
        assertEquals(1_700_000_100_000L, persisted.wereadAchievement.updatedAtMillis)
    }

    /** 高版本配置按可读字段加载，版本号与磁盘内容保持原样，不执行回写。 */
    @Test
    fun `高版本配置不清洗版本号`() {
        val future =
            LauncherConfig(
                version = LauncherConfig.CURRENT_VERSION + 1,
                desktop = DesktopConfig(columns = 5),
            )
        val raw = encode(future).removeSuffix("}") + ",\"futureSection\":{\"value\":1}}"
        val memory = MemoryPersistence(raw)
        val store = JsonConfigStore(memory)

        assertEquals(LauncherConfig.CURRENT_VERSION + 1, store.state.value.version)
        assertEquals(5, store.state.value.desktop.columns)
        assertEquals(0, memory.writeCount)
        assertTrue(store.newerVersionDetected.value)
        assertEquals(raw, memory.snapshot())
    }
}
