package com.shuiyu.octoberrain.core.data.wereadimport

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 迁移包清单中的单个条目描述，字节数与摘要用于接收端校验。 */
@Serializable
data class WereadImportEntry(
    /** 条目名称，与微读侧原盘文件名或偏好名一致。 */
    val name: String,
    /** 明文字节数。 */
    val bytes: Int = 0,
    /** 明文的 SHA-256 十六进制摘要。 */
    val sha256: String = "",
)

/** 迁移包条目数量统计，用于迁移完成后展示与核对。 */
@Serializable
data class WereadImportCounts(
    val books: Int = 0,
    val notes: Int = 0,
    val bills: Int = 0,
)

/**
 * 迁移包加密参数，由导出端写入清单，接收端据此确认解密方式。
 *
 * @property cipher 加密算法名称，固定为 AES/GCM/NoPadding
 * @property keyBits 密钥位数，固定为 256
 * @property tagBits 认证标签位数，固定为 128
 * @property container 容器标识，固定为 WRMK
 */
@Serializable
data class WereadImportEncryption(
    val cipher: String = "AES/GCM/NoPadding",
    val keyBits: Int = 256,
    val tagBits: Int = 128,
    val container: String = "WRMK",
) {
    /**
     * 判断加密参数是否属于接收端支持的组合。
     *
     * @return 算法、密钥位数、标签位数与容器标识均匹配时为真
     */
    fun isSupported(): Boolean =
        cipher == "AES/GCM/NoPadding" && keyBits == 256 && tagBits == 128 && container == "WRMK"
}

/**
 * 迁移包清单。
 *
 * 清单本身明文存放，位于导出文件第一行；其后依次是 12 字节初始化向量与 GCM 密文。
 *
 * @property schemaVersion 清单结构版本，当前固定为 1
 * @property sourceVersionName 导出方版本名
 * @property sourceVersionCode 导出方版本号
 * @property exportedAt 导出时间，单位毫秒
 * @property encrypted 加密参数对象，解密方式与接收端实现逐项对应
 * @property entries 条目清单
 * @property counts 条目数量统计
 */
@Serializable
data class WereadImportManifest(
    val schemaVersion: Int = 1,
    val sourceVersionName: String = "",
    val sourceVersionCode: Int = 0,
    val exportedAt: Long = 0L,
    val encrypted: WereadImportEncryption = WereadImportEncryption(),
    val entries: List<WereadImportEntry> = emptyList(),
    val counts: WereadImportCounts = WereadImportCounts(),
)

/** 导入失败的分类，与导出端的错误码保持一一对应。 */
enum class WereadImportError {
    /** 参数无效：清单缺失、密钥长度错误、文件过短。 */
    INVALID_PARAM,

    /** 解密失败：密钥不匹配或密文被截断。 */
    DECRYPT_FAILED,

    /** 摘要不一致：条目内容在传输中被改写。 */
    DIGEST_MISMATCH,

    /** 容器结构不合法：魔数、条目长度或条目数量异常。 */
    MALFORMED,

    /** 数据为空：清单没有条目。 */
    EMPTY,

    /** 落位失败：目录不可写、空间不足或替换时出现异常。 */
    WRITE_FAILED,
}

/** 导入结果。 */
sealed interface WereadImportResult {
    /**
     * 导入成功。
     *
     * @property manifest 解析后的清单
     * @property entries 名称到明文字节的映射，顺序与清单一致
     */
    data class Success(
        val manifest: WereadImportManifest,
        val entries: Map<String, ByteArray>,
    ) : WereadImportResult

    /**
     * 导入失败。
     *
     * @property error 失败分类
     * @property message 可直接展示的失败说明
     */
    data class Failure(
        val error: WereadImportError,
        val message: String,
    ) : WereadImportResult
}

/**
 * 微读墨记迁移包读取器。
 *
 * 读取顺序：第一行清单 → 12 字节初始化向量 → AES/GCM 密文 → 解密得到 WRMK1 容器 →
 * 逐条校验摘要。实现只做内存解析，不访问文件系统与网络，便于在单元测试中复现。
 *
 * 协议每一步校验独立成函数，便于单测定位失败分类；函数数量随协议步骤展开而增加，
 * 因此对函数数量度量局部豁免。
 */
@Suppress("TooManyFunctions")
object WereadImportReader {
    /** 容器魔数，用于识别解密后的内容是否属于本协议。 */
    private const val MAGIC = "WRMK"

    /** 容器版本号，当前固定为 1。 */
    private const val CONTAINER_VERSION = 1

    /** GCM 认证标签位数。 */
    private const val TAG_BITS = 128

    /** 初始化向量字节数。 */
    private const val IV_BYTES = 12

    /** 迁移密钥字节数，与导出端保持一致。 */
    private const val KEY_BYTES = 32

    /** 清单结构版本，当前固定为 1。 */
    private const val SCHEMA_VERSION = 1

    /** 允许的最大清单行长度，防止异常输入占用过多内存。 */
    private const val MAX_MANIFEST_BYTES = 1 shl 20

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

    /**
     * 解析迁移包。
     *
     * @param pack 导出端产生的完整字节
     * @param key 32 字节迁移密钥，只在本方法内使用
     * @return 成功时返回清单与条目明文，失败时返回分类与说明
     */
    fun read(
        pack: ByteArray,
        key: ByteArray,
    ): WereadImportResult =
        when (val opened = open(pack, key)) {
            is Opened.Bad -> opened.failure
            is Opened.Ok -> decode(opened, pack, key)
        }

    /**
     * 打开包并解析清单，校验参数与清单结构。
     *
     * @param pack 导出端产生的完整字节
     * @param key 32 字节迁移密钥
     */
    private fun open(
        pack: ByteArray,
        key: ByteArray,
    ): Opened =
        when {
            key.size != KEY_BYTES -> bad(WereadImportError.INVALID_PARAM, "迁移密钥必须为 32 字节")
            pack.size <= IV_BYTES -> bad(WereadImportError.INVALID_PARAM, "迁移包长度不足")
            else -> header(pack)
        }

    /**
     * 解析清单行并确认密文存在。
     *
     * @param pack 导出端产生的完整字节
     */
    private fun header(pack: ByteArray): Opened {
        // 清单以换行结束，其后紧跟初始化向量与密文
        val newline = pack.indexOf('\n'.code.toByte())
        if (newline <= 0 || newline > MAX_MANIFEST_BYTES) {
            return bad(WereadImportError.MALFORMED, "清单行缺失或超长")
        }
        return layout(pack, newline)
    }

    /**
     * 解析清单内容并校验版本、条目与加密参数。
     *
     * @param pack 导出端产生的完整字节
     * @param newline 清单行结束位置
     */
    private fun layout(
        pack: ByteArray,
        newline: Int,
    ): Opened {
        val manifestText = String(pack, 0, newline, Charsets.UTF_8)
        val manifest =
            runCatching { json.decodeFromString<WereadImportManifest>(manifestText) }.getOrNull()
                ?: return bad(WereadImportError.MALFORMED, "清单解析失败")
        val ivOffset = newline + 1
        val cipherOffset = ivOffset + IV_BYTES
        val problem = manifestProblem(manifest, cipherOffset, pack.size)
        return problem ?: Opened.Ok(manifest, ivOffset, cipherOffset)
    }

    /**
     * 检查清单字段是否满足接收端要求。
     *
     * @param manifest 已解析的清单
     * @param cipherOffset 密文起始位置
     * @param packSize 迁移包总长度
     * @return 存在问题返回失败结果，否则返回 null
     */
    private fun manifestProblem(
        manifest: WereadImportManifest,
        cipherOffset: Int,
        packSize: Int,
    ): Opened.Bad? =
        when {
            manifest.schemaVersion != SCHEMA_VERSION ->
                bad(WereadImportError.MALFORMED, "清单版本 ${manifest.schemaVersion} 不受支持")
            manifest.entries.isEmpty() -> bad(WereadImportError.EMPTY, "迁移包没有数据")
            cipherOffset >= packSize -> bad(WereadImportError.MALFORMED, "缺少密文")
            !manifest.encrypted.isSupported() ->
                bad(WereadImportError.MALFORMED, "清单加密参数 ${manifest.encrypted.cipher} 不受支持")
            else -> null
        }

    /**
     * 解密容器并校验条目。
     *
     * @param opened 已解析的包头部
     * @param pack 导出端产生的完整字节
     * @param key 32 字节迁移密钥
     */
    private fun decode(
        opened: Opened.Ok,
        pack: ByteArray,
        key: ByteArray,
    ): WereadImportResult {
        // 解密容器：GCM 自带完整性校验，密钥或密文不匹配时直接失败
        val plain = decrypt(pack, key, opened.ivOffset, opened.cipherOffset)
        if (plain == null) return fail(WereadImportError.DECRYPT_FAILED, "解密失败，密钥不匹配")
        return unpack(opened.manifest, plain)
    }

    /**
     * 解析容器明文并逐条校验。
     *
     * @param manifest 已解析的清单
     * @param plain 解密后的容器明文
     */
    private fun unpack(
        manifest: WereadImportManifest,
        plain: ByteArray,
    ): WereadImportResult {
        val entries = runCatching { parseContainer(plain) }.getOrNull()
        return entries?.let { verify(manifest, it) }
            ?: fail(WereadImportError.MALFORMED, "容器结构不合法")
    }

    /**
     * 按清单逐条比对摘要，全部一致时返回成功结果。
     *
     * @param manifest 已解析的清单
     * @param entries 容器内的条目明文
     */
    private fun verify(
        manifest: WereadImportManifest,
        entries: Map<String, ByteArray>,
    ): WereadImportResult = firstMismatch(manifest, entries) ?: WereadImportResult.Success(manifest, entries)

    /**
     * 找出首个不一致条目。
     *
     * @param manifest 已解析的清单
     * @param entries 容器内的条目明文
     * @return 首次不一致对应的失败结果，全部一致时返回 null
     */
    private fun firstMismatch(
        manifest: WereadImportManifest,
        entries: Map<String, ByteArray>,
    ): WereadImportResult.Failure? =
        when {
            entries.isEmpty() -> fail(WereadImportError.EMPTY, "容器没有条目")
            entries.size != manifest.entries.size ->
                fail(
                    WereadImportError.MALFORMED,
                    "条目数量不一致：清单 ${manifest.entries.size}，容器 ${entries.size}",
                )
            else -> manifest.entries.firstNotNullOfOrNull { checkEntry(it, entries[it.name]) }
        }

    /**
     * 校验单个条目的字节数与摘要。
     *
     * @param entry 清单条目
     * @param data 容器内的对应明文，缺失时为 null
     * @return 不一致时返回失败结果，一致时返回 null
     */
    private fun checkEntry(
        entry: WereadImportEntry,
        data: ByteArray?,
    ): WereadImportResult.Failure? =
        when {
            data == null -> fail(WereadImportError.MALFORMED, "清单条目 ${entry.name} 在容器中缺失")
            entry.bytes > 0 && entry.bytes != data.size ->
                fail(WereadImportError.DIGEST_MISMATCH, "条目 ${entry.name} 长度不一致")
            entry.sha256.isNotBlank() && sha256Hex(data) != entry.sha256.lowercase() ->
                fail(WereadImportError.DIGEST_MISMATCH, "条目 ${entry.name} 摘要不一致")
            else -> null
        }

    /**
     * 解密容器明文。
     *
     * @param pack 导出端产生的完整字节
     * @param key 32 字节迁移密钥
     * @param ivOffset 初始化向量起始位置
     * @param cipherOffset 密文起始位置
     * @return 解密成功返回明文，失败返回 null
     */
    private fun decrypt(
        pack: ByteArray,
        key: ByteArray,
        ivOffset: Int,
        cipherOffset: Int,
    ): ByteArray? =
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, pack, ivOffset, IV_BYTES),
            )
            cipher.doFinal(pack, cipherOffset, pack.size - cipherOffset)
        }.getOrNull()

    /**
     * 构造失败结果。
     *
     * @param error 失败分类
     * @param message 便于展示的说明
     */
    private fun fail(
        error: WereadImportError,
        message: String,
    ): WereadImportResult.Failure = WereadImportResult.Failure(error, message)

    /**
     * 构造打开阶段的失败结果。
     *
     * @param error 失败分类
     * @param message 便于展示的说明
     */
    private fun bad(
        error: WereadImportError,
        message: String,
    ): Opened.Bad = Opened.Bad(fail(error, message))

    /** 打开阶段的中间结果。 */
    private sealed interface Opened {
        /**
         * 解析成功。
         *
         * @property manifest 已解析的清单
         * @property ivOffset 初始化向量起始位置
         * @property cipherOffset 密文起始位置
         */
        data class Ok(
            val manifest: WereadImportManifest,
            val ivOffset: Int,
            val cipherOffset: Int,
        ) : Opened

        /** 解析失败。 */
        data class Bad(
            val failure: WereadImportResult.Failure,
        ) : Opened
    }

    /**
     * 解析解密后的容器。
     *
     * 结构为 4 字节魔数 WRMK、32 位容器版本、32 位条目数，随后逐条
     * 16 位名称长度、名称、32 位数据长度与数据，长度均为大端有符号整数。
     *
     * @param plain 解密后的容器字节
     * @return 名称到明文字节的映射，保持容器内的顺序
     */
    private fun parseContainer(plain: ByteArray): Map<String, ByteArray> {
        val buffer = ByteBuffer.wrap(plain).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.toByteArray(Charsets.US_ASCII).size)
        buffer.get(magic)
        require(String(magic, Charsets.US_ASCII) == MAGIC) { "魔数错误" }

        val version = buffer.int
        require(version == CONTAINER_VERSION) { "容器版本 $version 不受支持" }

        val count = buffer.int
        require(count > 0 && count <= 4096) { "条目数量异常" }

        val result = LinkedHashMap<String, ByteArray>(count)
        repeat(count) {
            val nameLength = buffer.short.toInt() and 0xFFFF
            require(nameLength in 1..512) { "名称长度异常" }
            val nameBytes = ByteArray(nameLength)
            buffer.get(nameBytes)
            val name = String(nameBytes, Charsets.UTF_8)
            require(name.isNotBlank() && name !in result) { "名称重复或为空" }

            val dataLength = buffer.int
            require(dataLength in 0..MAX_MANIFEST_BYTES) { "数据长度异常" }
            val data = ByteArray(dataLength)
            buffer.get(data)
            result[name] = data
        }
        require(!buffer.hasRemaining()) { "容器尾部存在多余字节" }
        return result
    }

    /**
     * 计算字节数组的 SHA-256 十六进制摘要。
     *
     * @param data 需要计算的字节
     * @return 小写十六进制摘要字符串
     */
    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") {
            it
                .toInt()
                .and(0xFF)
                .toString(16)
                .padStart(2, '0')
        }
}
