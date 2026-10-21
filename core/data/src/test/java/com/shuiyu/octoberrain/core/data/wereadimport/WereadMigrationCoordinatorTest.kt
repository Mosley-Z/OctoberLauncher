package com.shuiyu.octoberrain.core.data.wereadimport

import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.WereadDeskConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 迁移编排单元测试。
 *
 * 覆盖首次迁移写配置、摘要一致跳过与密钥错误不写配置三条路径。
 */
class WereadMigrationCoordinatorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val json = Json { encodeDefaults = true }

    /** 可写配置存储替身，直接以 StateFlow 承载当前配置。 */
    private class FakeConfigStore(
        initial: LauncherConfig = LauncherConfig(),
    ) : ConfigStore {
        private val flow = MutableStateFlow(initial)
        override val state: StateFlow<LauncherConfig> = flow

        override fun edit(transform: (LauncherConfig) -> LauncherConfig) {
            flow.value = transform(flow.value)
        }
    }

    /**
     * 构造带 kernel_prefs.json 与 achievement.json 的迁移包。
     *
     * @param key 迁移密钥
     * @param extra 追加条目，覆盖同名默认条目
     */
    private fun buildPack(
        key: ByteArray,
        extra: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        val entries =
            linkedMapOf(
                "nt_index.json" to """{"books":[1]}""".toByteArray(),
                "kernel_prefs.json" to
                    """{"string":{"api_key":"wrk-test"},"long":{},"int":{},"bool":{}}""".toByteArray(),
                "achievement.json" to """{"level":3}""".toByteArray(),
            )
        entries.putAll(extra)
        val manifest =
            WereadImportManifest(
                sourceVersionCode = 47,
                entries =
                    entries.map { (name, data) ->
                        WereadImportEntry(name, data.size, WereadImportReader.sha256Hex(data))
                    },
                counts = WereadImportCounts(books = 1, notes = 0, bills = 0),
            )
        val manifestText = json.encodeToString(manifest)
        val container = encodeContainer(entries)
        val iv = ByteArray(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(container)
        val manifestBytes = manifestText.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(manifestBytes.size + 1 + iv.size + encrypted.size)
        buffer.put(manifestBytes)
        buffer.put('\n'.code.toByte())
        buffer.put(iv)
        buffer.put(encrypted)
        return buffer.array()
    }

    /** 按导出端约定编码容器：4 字节魔数、容器版本、条目数、逐条名称与数据。 */
    private fun encodeContainer(entries: LinkedHashMap<String, ByteArray>): ByteArray {
        val size =
            4 + 4 + 4 +
                entries.entries.sumOf { (name, data) ->
                    val bytes = name.toByteArray(Charsets.UTF_8)
                    2 + bytes.size + 4 + data.size
                }
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buffer.put("WRMK".toByteArray(Charsets.US_ASCII))
        buffer.putInt(1)
        buffer.putInt(entries.size)
        entries.forEach { (name, data) ->
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            buffer.putShort(nameBytes.size.toShort())
            buffer.put(nameBytes)
            buffer.putInt(data.size)
            buffer.put(data)
        }
        return buffer.array()
    }

    @Test
    fun `legacy fingerprint books and font migrate with original grouped keys`() {
        val key = ByteArray(32) { it.toByte() }
        val config = FakeConfigStore()
        val store = WereadImportStore(File(folder.root, "weread"), File(folder.root, "weread-backup"))
        val prefs = """{"string":{"fp_books":"book-b,book-a,book-b"},"int":{"fp_font_px":17}}"""
        val result =
            WereadMigrationCoordinator(config, store).import(
                buildPack(key, mapOf("kernel_prefs.json" to prefs.toByteArray())),
                key,
                3000L,
            )
        assertTrue(result is WereadMigrationOutcome.Success)
        val desk = config.state.value.integrations.wereadDesk
        assertEquals(listOf("book-b", "book-a"), desk.effectiveFpBookIds)
        assertTrue(desk.fpBooksConfigured)
        assertEquals(17, desk.effectiveFpFontPx)
    }

    @Test
    fun `explicitly cleared fingerprint selection survives later legacy import`() {
        val key = ByteArray(32) { it.toByte() }
        val initial =
            LauncherConfig(
                integrations =
                    IntegrationsConfig(
                        wereadDesk = WereadDeskConfig(fpBooksConfigured = true),
                    ),
            )
        val config = FakeConfigStore(initial)
        val store = WereadImportStore(File(folder.root, "weread"), File(folder.root, "weread-backup"))
        val prefs = """{"string":{"fp_books":"old-book"},"int":{"fp_font_px":99}}"""
        val result =
            WereadMigrationCoordinator(config, store).import(
                buildPack(key, mapOf("kernel_prefs.json" to prefs.toByteArray())),
                key,
                3000L,
            )
        assertTrue(result is WereadMigrationOutcome.Success)
        val desk = config.state.value.integrations.wereadDesk
        assertTrue(desk.effectiveFpBookIds.isEmpty())
        assertTrue(desk.fpBooksConfigured)
        assertEquals(18, desk.effectiveFpFontPx)
    }

    /** 首次迁移写入密钥、成就原文与迁移状态。 */
    @Test
    fun `首次迁移写入配置与迁移状态`() {
        val key = ByteArray(32) { it.toByte() }
        val config = FakeConfigStore()
        val store = WereadImportStore(File(folder.root, "weread"), File(folder.root, "weread-backup"))
        val coordinator = WereadMigrationCoordinator(config, store)

        val outcome = coordinator.import(buildPack(key), key, nowMillis = 3000L)

        assertTrue(outcome is WereadMigrationOutcome.Success)
        outcome as WereadMigrationOutcome.Success
        assertFalse(outcome.skipped)
        val saved = config.state.value
        assertEquals("wrk-test", saved.integrations.wereadApiKey)
        assertEquals("""{"level":3}""", saved.wereadAchievement.rawJson)
        assertTrue(saved.wereadMigration.complete)
        assertEquals(47, saved.wereadMigration.sourceVersionCode)
        assertEquals(3000L, saved.wereadMigration.importedAtMillis)
        assertNotEquals("", saved.wereadMigration.manifestDigest)
        assertTrue(File(folder.root, "weread/nt_index.json").exists())
    }

    /** 导出侧的墨台配置时间较新时，采用导出侧的顺序与显示位。 */
    @Test
    fun `导出侧墨台配置较新时被采用`() {
        val key = ByteArray(32) { it.toByte() }
        val config = FakeConfigStore()
        val store = WereadImportStore(File(folder.root, "weread"), File(folder.root, "weread-backup"))
        val coordinator = WereadMigrationCoordinator(config, store)
        val desk = """{"order":["fp","bill"],"onMask":5,"updatedAt":5000}""".toByteArray()

        val outcome = coordinator.import(buildPack(key, mapOf("desk_prefs.json" to desk)), key, 6000L)

        assertTrue(outcome is WereadMigrationOutcome.Success)
        val saved = config.state.value.integrations.wereadDesk
        assertEquals(listOf("fp", "bill"), saved.order)
        assertEquals(5, saved.onMask)
        assertEquals(5000L, saved.syncedAtMillis)
    }

    /** 十雨侧的墨台配置时间较新时，保留本地顺序与显示位。 */
    @Test
    fun `本地墨台配置较新时保持原值`() {
        val key = ByteArray(32) { it.toByte() }
        val initial =
            LauncherConfig(
                integrations =
                    IntegrationsConfig(
                        wereadDesk =
                            WereadDeskConfig(
                                order = listOf("rank", "bill"),
                                onMask = 2,
                                syncedAtMillis = 9000L,
                            ),
                    ),
            )
        val config = FakeConfigStore(initial)
        val store = WereadImportStore(File(folder.root, "weread"), File(folder.root, "weread-backup"))
        val coordinator = WereadMigrationCoordinator(config, store)
        val desk = """{"order":["fp","bill"],"onMask":5,"updatedAt":5000}""".toByteArray()

        val outcome = coordinator.import(buildPack(key, mapOf("desk_prefs.json" to desk)), key, 10000L)

        assertTrue(outcome is WereadMigrationOutcome.Success)
        val saved = config.state.value.integrations.wereadDesk
        assertEquals(listOf("rank", "bill"), saved.order)
        assertEquals(2, saved.onMask)
        assertEquals(9000L, saved.syncedAtMillis)
    }

    /** 同一份迁移包再次导入时按摘要跳过，不重复落位。 */
    @Test
    fun `相同摘要重复导入被跳过`() {
        val key = ByteArray(32) { it.toByte() }
        val config = FakeConfigStore()
        val store = WereadImportStore(File(folder.root, "weread"), File(folder.root, "weread-backup"))
        val coordinator = WereadMigrationCoordinator(config, store)
        val pack = buildPack(key)

        coordinator.import(pack, key, nowMillis = 3000L)
        val second = coordinator.import(pack, key, nowMillis = 4000L)

        assertTrue(second is WereadMigrationOutcome.Success)
        assertTrue((second as WereadMigrationOutcome.Success).skipped)
        assertEquals(3000L, config.state.value.wereadMigration.importedAtMillis)
    }

    /** 密钥错误时返回解密失败，配置与数据目录都不改变。 */
    @Test
    fun `密钥错误不写配置`() {
        val key = ByteArray(32) { it.toByte() }
        val config = FakeConfigStore()
        val store = WereadImportStore(File(folder.root, "weread"), File(folder.root, "weread-backup"))
        val coordinator = WereadMigrationCoordinator(config, store)

        val outcome = coordinator.import(buildPack(key), ByteArray(32) { (it + 2).toByte() })

        assertEquals(
            WereadImportError.DECRYPT_FAILED,
            (outcome as WereadMigrationOutcome.Failure).error,
        )
        assertEquals("", config.state.value.integrations.wereadApiKey)
        assertFalse(File(folder.root, "weread").exists())
    }

    /** 先启动或同步产生目录不阻止迁移，旧数据留在备份内。 */
    @Test
    fun `已有同步数据允许迁移并切换导入来源`() {
        val key = ByteArray(32) { it.toByte() }
        val root = File(folder.root, "weread").apply { mkdirs() }
        File(root, "shelf.json").writeText("old shelf")
        val config = FakeConfigStore(LauncherConfig(integrations = IntegrationsConfig(wereadSource = "synced")))
        val coordinator = WereadMigrationCoordinator(config, WereadImportStore(root, File(folder.root, "backups")))

        val result = coordinator.import(buildPack(key), key, 3000L) as WereadMigrationOutcome.Success

        assertEquals("old shelf", File(result.backup!!, "shelf.json").readText())
        assertEquals("imported", config.state.value.integrations.wereadSource)
        assertTrue(File(root, "nt_index.json").isFile)
    }

    /** 仅打开页面产生的空目录不会导致拒绝，也无需生成空备份。 */
    @Test
    fun `空目录允许导入`() {
        val key = ByteArray(32) { it.toByte() }
        val root = File(folder.root, "weread").apply { mkdirs() }
        val config = FakeConfigStore()
        val coordinator = WereadMigrationCoordinator(config, WereadImportStore(root, File(folder.root, "backups")))

        val result = coordinator.import(buildPack(key), key) as WereadMigrationOutcome.Success

        assertEquals(null, result.backup)
        assertTrue(File(root, "nt_index.json").isFile)
    }

    /** 配置存储可能吞掉持久化失败；未发布迁移状态时必须回滚目录。 */
    @Test
    fun `配置未持久化时报告失败并恢复旧数据`() {
        val key = ByteArray(32) { it.toByte() }
        val root = File(folder.root, "weread").apply { mkdirs() }
        File(root, "shelf.json").writeText("old shelf")
        val config =
            object : ConfigStore {
                override val state = MutableStateFlow(LauncherConfig())

                override fun edit(transform: (LauncherConfig) -> LauncherConfig) = Unit
            }
        val coordinator = WereadMigrationCoordinator(config, WereadImportStore(root, File(folder.root, "backups")))

        val result = coordinator.import(buildPack(key), key)

        assertEquals(WereadImportError.WRITE_FAILED, (result as WereadMigrationOutcome.Failure).error)
        assertEquals("old shelf", File(root, "shelf.json").readText())
        assertFalse(File(root, "nt_index.json").exists())
        assertFalse(config.state.value.wereadMigration.complete)
    }

    /** 重复导入只判幂等，不覆盖之后手动选择的数据来源和本地数据。 */
    @Test
    fun `重复摘要保留后来选择的来源及数据`() {
        val key = ByteArray(32) { it.toByte() }
        val config = FakeConfigStore()
        val root = File(folder.root, "weread")
        val coordinator = WereadMigrationCoordinator(config, WereadImportStore(root, File(folder.root, "backups")))
        val pack = buildPack(key)
        coordinator.import(pack, key, 1000L)
        config.edit { it.copy(integrations = it.integrations.copy(wereadSource = "synced")) }
        File(root, "nt_index.json").writeText("newer")

        val result = coordinator.import(pack, key, 2000L) as WereadMigrationOutcome.Success

        assertTrue(result.skipped)
        assertEquals("synced", config.state.value.integrations.wereadSource)
        assertEquals("newer", File(root, "nt_index.json").readText())
    }
}
