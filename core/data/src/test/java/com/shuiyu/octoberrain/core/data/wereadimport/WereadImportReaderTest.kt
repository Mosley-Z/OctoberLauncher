package com.shuiyu.octoberrain.core.data.wereadimport

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 迁移包读取器单元测试。
 *
 * 测试内构造与导出端一致的字节结构，覆盖正常读取、密钥错误、摘要篡改、
 * 版本不符、空数据与截断六种路径。
 */
class WereadImportReaderTest {
    private val json = Json { encodeDefaults = true }

    /** 构造与导出端一致的完整迁移包，供各用例复用。 */
    private fun buildPack(
        entries: LinkedHashMap<String, ByteArray>,
        key: ByteArray,
        iv: ByteArray = ByteArray(12).also { SecureRandom().nextBytes(it) },
        manifestOverride: WereadImportManifest? = null,
    ): ByteArray {
        val container = encodeContainer(entries)
        val manifest =
            manifestOverride
                ?: WereadImportManifest(
                    sourceVersionName = "1.3.2-beta",
                    sourceVersionCode = 47,
                    exportedAt = 1_700_000_000_000L,
                    entries =
                        entries.map { (name, data) ->
                            WereadImportEntry(name, data.size, WereadImportReader.sha256Hex(data))
                        },
                    counts = WereadImportCounts(books = 1, notes = 2, bills = 1),
                )
        val manifestText = json.encodeToString(manifest)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(container)

        val out = ByteBuffer.allocate(manifestText.toByteArray(Charsets.UTF_8).size + 1 + iv.size + cipherText.size)
        out.put(manifestText.toByteArray(Charsets.UTF_8))
        out.put('\n'.code.toByte())
        out.put(iv)
        out.put(cipherText)
        return out.array()
    }

    /** 按导出端约定编码容器：4 字节魔数、容器版本、条目数、逐条名称与数据。 */
    private fun encodeContainer(entries: LinkedHashMap<String, ByteArray>): ByteArray {
        val size =
            4 + 4 + 4 +
                entries.entries.sumOf { (name, data) ->
                    val nameBytes = name.toByteArray(Charsets.UTF_8)
                    2 + nameBytes.size + 4 + data.size
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

    /** 正常包应解析出全部条目与清单统计。 */
    @Test
    fun `正常包读取成功`() {
        val key = ByteArray(32) { it.toByte() }
        val entries =
            linkedMapOf(
                "nt_index.json" to """{"books":[1,2,3]}""".toByteArray(),
                "shelf.json" to """{"shelf":[1]}""".toByteArray(),
            )
        val pack = buildPack(entries, key)

        val result = WereadImportReader.read(pack, key)

        assertTrue(result is WereadImportResult.Success)
        val success = result as WereadImportResult.Success
        assertEquals(2, success.entries.size)
        assertArrayEquals(entries["nt_index.json"], success.entries["nt_index.json"])
        assertEquals("1.3.2-beta", success.manifest.sourceVersionName)
        assertEquals(47, success.manifest.sourceVersionCode)
        assertEquals(WereadImportCounts(books = 1, notes = 2, bills = 1), success.manifest.counts)
    }

    /** 密钥不匹配时 GCM 校验失败，返回解密错误。 */
    @Test
    fun `错误密钥解密失败`() {
        val key = ByteArray(32) { it.toByte() }
        val pack = buildPack(linkedMapOf("a.json" to byteArrayOf(1, 2, 3)), key)

        val result = WereadImportReader.read(pack, ByteArray(32) { (it + 1).toByte() })

        assertEquals(
            WereadImportError.DECRYPT_FAILED,
            (result as WereadImportResult.Failure).error,
        )
    }

    /** 清单摘要被改写时，落位前即被拒绝。 */
    @Test
    fun `摘要不一致被拒绝`() {
        val key = ByteArray(32) { it.toByte() }
        val data = byteArrayOf(9, 8, 7, 6)
        val manifest =
            WereadImportManifest(
                entries = listOf(WereadImportEntry("a.json", data.size, "0".repeat(64))),
            )
        val pack = buildPack(linkedMapOf("a.json" to data), key, manifestOverride = manifest)

        val result = WereadImportReader.read(pack, key)

        assertEquals(
            WereadImportError.DIGEST_MISMATCH,
            (result as WereadImportResult.Failure).error,
        )
    }

    /** 清单版本高于当前支持版本时拒绝导入，避免结构错位。 */
    @Test
    fun `未知清单版本被拒绝`() {
        val key = ByteArray(32) { it.toByte() }
        val data = byteArrayOf(1)
        val manifest =
            WereadImportManifest(
                schemaVersion = 2,
                entries = listOf(WereadImportEntry("a.json", 1, WereadImportReader.sha256Hex(data))),
            )
        val pack = buildPack(linkedMapOf("a.json" to data), key, manifestOverride = manifest)

        val result = WereadImportReader.read(pack, key)

        assertEquals(WereadImportError.MALFORMED, (result as WereadImportResult.Failure).error)
    }

    /** 清单条目为空时直接返回空数据，不进入解密。 */
    @Test
    fun `空清单返回空数据错误`() {
        val key = ByteArray(32) { it.toByte() }
        val manifest = WereadImportManifest(entries = emptyList())
        val pack = buildPack(linkedMapOf("a.json" to byteArrayOf(1)), key, manifestOverride = manifest)

        val result = WereadImportReader.read(pack, key)

        assertEquals(WereadImportError.EMPTY, (result as WereadImportResult.Failure).error)
    }

    /** 文件被截断到只有清单时返回长度错误。 */
    @Test
    fun `截断包返回参数错误`() {
        val key = ByteArray(32) { it.toByte() }
        val pack = buildPack(linkedMapOf("a.json" to byteArrayOf(1)), key)
        val truncated = pack.copyOf(16)

        val result = WereadImportReader.read(truncated, key)

        assertTrue(result is WereadImportResult.Failure)
    }

    /** 密钥长度不为 32 字节时立即拒绝。 */
    @Test
    fun `非法密钥长度被拒绝`() {
        val result = WereadImportReader.read(byteArrayOf(1, 2, 3), byteArrayOf(1, 2))

        assertEquals(WereadImportError.INVALID_PARAM, (result as WereadImportResult.Failure).error)
    }
}
