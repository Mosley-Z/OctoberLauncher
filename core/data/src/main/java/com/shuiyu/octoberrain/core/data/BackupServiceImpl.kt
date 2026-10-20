package com.shuiyu.octoberrain.core.data

import android.content.Context
import android.net.Uri
import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.BackupResult
import com.shuiyu.octoberrain.core.common.BackupService
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.withoutLocalCredentials
import com.shuiyu.octoberrain.core.design.ThemeTokensCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 配置备份实现。
 *
 * 备份包为 zip，包含三项：
 * - `config.json` 为完整配置；
 * - `theme.json` 为外观与主题令牌的独立副本，便于只迁移主题；
 * - `manifest.json` 记录包版本与导出时间。
 *
 * 导入时按节恢复：单个节解析失败不会影响其它节，失败项在结果中列出。
 * 配置写入统一经 [AppServices.config] 的编辑事务完成，保证界面立即响应。
 * 主题副本中的令牌经 [ThemeTokensCodec] 合并回外观配置，使配色随备份一起恢复。
 */
class BackupServiceImpl(
    private val context: Context,
) : BackupService {
    /**
     * 导出配置到指定 Uri。
     *
     * 写入前剔除微信读书密钥、锁屏摘要与盐：这三项属于本机凭据，随备份文件外流
     * 会扩大泄露范围。导入时沿用本机已保存的凭据，不读取备份中的这三个字段。
     */
    override suspend fun export(uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val config =
                    AppServices.config.state.value
                        .withoutLocalCredentials()
                val resolver = context.contentResolver
                resolver.openOutputStream(uri)?.use { out ->
                    ZipOutputStream(out).use { zip ->
                        zip.putNextEntry(ZipEntry(ENTRY_CONFIG))
                        zip.write(
                            AppJson.format
                                .encodeToString(LauncherConfig.serializer(), config)
                                .toByteArray(Charsets.UTF_8),
                        )
                        zip.closeEntry()

                        zip.putNextEntry(ZipEntry(ENTRY_THEME))
                        zip.write(themePayload(config).toByteArray(Charsets.UTF_8))
                        zip.closeEntry()

                        zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                        zip.write(manifestPayload(config).toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                } != null
            }.getOrDefault(false)
        }

    /**
     * 从指定 Uri 导入配置，逐节报告结果。
     *
     * 配置节缺失或损坏时回退默认值，主题副本单独解析，避免主题问题影响桌面与托盘
     * 等其它配置。导入完成后由 [ConfigMigration] 升级到当前版本。
     *
     * 旧备份的配置节可能包含微信读书密钥、锁屏摘要与盐；这三个字段一律忽略，
     * 导入后沿用本机已保存的值，避免旧文件覆盖当前凭据或把旧凭据写回本机。
     */
    override suspend fun import(uri: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            val entries =
                readEntriesOrNull(uri)
                    ?: return@withContext BackupResult(false, "无法读取备份文件")
            val rawConfig =
                entries[ENTRY_CONFIG]
                    ?: return@withContext BackupResult(false, "备份文件缺少 config.json")
            val decoded =
                runCatching {
                    AppJson.format.decodeFromString(
                        LauncherConfig.serializer(),
                        rawConfig.toString(Charsets.UTF_8),
                    )
                }.getOrNull()
            if (decoded == null) {
                return@withContext BackupResult(false, "配置解析失败，文件可能已损坏")
            }
            val sections = mutableListOf("配置")
            var merged = ConfigMigration.migrate(decoded)
            val themeEntry = entries[ENTRY_THEME]
            if (themeEntry != null) {
                val theme = parseTheme(themeEntry)
                if (theme != null) {
                    merged = merged.copy(appearance = theme)
                    sections += "外观与主题"
                } else {
                    InkLog.w(TAG, "主题副本解析失败，保留配置内的外观设置")
                    sections += "外观与主题（解析失败，已保留配置内设置）"
                }
            }
            val local = AppServices.config.state.value
            merged =
                merged.copy(
                    integrations = merged.integrations.copy(wereadApiKey = local.integrations.wereadApiKey),
                    lock = merged.lock.copy(pinHash = local.lock.pinHash, salt = local.lock.salt),
                )
            AppServices.config.edit { merged.copy(version = LauncherConfig.CURRENT_VERSION) }
            BackupResult(
                ok = true,
                message = manifestWarning(entries[ENTRY_MANIFEST]) ?: "备份已恢复",
                sections = sections,
            )
        }

    /**
     * 只恢复备份包中的主题数据，不修改桌面、托盘等其它配置。
     *
     * 优先读取 `theme.json`；该条目缺失或损坏时回退到 `config.json` 中的外观节。
     * 备份包不是 zip 或缺少可恢复的外观数据时返回失败结果，由调用方决定是否
     * 改用主题文件导入（`AppearanceRepository.importTheme`）。
     *
     * @param uri 备份包或主题副本文件的 Uri。
     * @return 恢复结果，成功时 sections 只包含“外观与主题”。
     */
    suspend fun importThemeOnly(uri: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            val entries = readEntriesOrNull(uri)
            var appearance: AppearanceConfig? = entries?.get(ENTRY_THEME)?.let { parseTheme(it) }
            if (appearance == null) {
                appearance =
                    entries?.get(ENTRY_CONFIG)?.let { bytes ->
                        runCatching {
                            AppJson.format
                                .decodeFromString(
                                    LauncherConfig.serializer(),
                                    bytes.toString(Charsets.UTF_8),
                                ).appearance
                        }.getOrNull()
                    }
            }
            if (appearance == null) {
                return@withContext BackupResult(false, "备份文件缺少可恢复的主题数据")
            }
            val target = appearance
            AppServices.config.edit { it.copy(appearance = target) }
            BackupResult(true, "主题已恢复", listOf("外观与主题"))
        }

    /** 读取 zip 内全部条目；读取失败或内容不是 zip 时返回 null。 */
    private fun readEntriesOrNull(uri: Uri): Map<String, ByteArray>? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                readEntries(input)
            }
        }.getOrNull()

    /** 读取 zip 内全部条目到内存，条目总量极少，无需流式处理。 */
    private fun readEntries(input: java.io.InputStream): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    result[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    /**
     * 解析主题副本，返回合并令牌后的外观配置。
     *
     * 参数 bytes 为 `theme.json` 的原始字节；外观节缺失或无法解析时返回 null。
     * `themeTokens` 为 JSON 字符串时交由 [ThemeTokensCodec] 解码，并把原文写回
     * `themeTokensJson`，确保 [com.shuiyu.octoberrain.core.design.DesignRuntime]
     * 能按同一份令牌恢复配色。
     */
    private fun parseTheme(bytes: ByteArray): AppearanceConfig? {
        val root =
            runCatching {
                AppJson.format.parseToJsonElement(bytes.toString(Charsets.UTF_8))
            }.getOrNull() as? JsonObject ?: return null
        val appearanceElement = root["appearance"] ?: return null
        val appearance =
            runCatching {
                AppJson.format.decodeFromJsonElement(AppearanceConfig.serializer(), appearanceElement)
            }.getOrNull() ?: return null
        val tokensText = (root["themeTokens"] as? JsonPrimitive)?.content
        if (tokensText.isNullOrBlank()) return appearance
        val tokens = ThemeTokensCodec.decode(tokensText) ?: return appearance
        return ThemeTokensCodec.merge(tokens, appearance).copy(themeTokensJson = tokensText)
    }

    /**
     * 校验清单并给出提示。
     *
     * 参数 bytes 为 `manifest.json` 原始字节。清单缺失或损坏时不阻断恢复，
     * 仅返回提示文本；来源包不同或格式版本更高时同样只提示，便于跨包迁移备份。
     */
    private fun manifestWarning(bytes: ByteArray?): String? {
        if (bytes == null) return "备份已恢复（缺少清单文件）"
        val root =
            runCatching {
                AppJson.format.parseToJsonElement(bytes.toString(Charsets.UTF_8))
            }.getOrNull() as? JsonObject ?: return "备份已恢复（清单文件损坏）"
        val packageName = (root["package"] as? JsonPrimitive)?.content
        if (packageName != null && packageName != PACKAGE_NAME) {
            return "备份已恢复，来源包为 $packageName"
        }
        val format = (root["format"] as? JsonPrimitive)?.content?.toIntOrNull()
        if (format != null && format > FORMAT_VERSION) {
            return "备份已恢复，文件格式版本 $format 高于当前版本"
        }
        return null
    }

    /** 生成主题副本，只包含外观节与主题令牌。 */
    private fun themePayload(config: LauncherConfig): String {
        val appearance =
            AppJson.format
                .encodeToJsonElement(AppearanceConfig.serializer(), config.appearance)
        return buildJsonObject {
            put("appearance", appearance)
            put("themeTokens", JsonPrimitive(config.appearance.themeTokensJson))
        }.toString()
    }

    /** 生成清单，记录包格式版本与导出时间。 */
    private fun manifestPayload(config: LauncherConfig): String {
        val manifest: JsonObject =
            buildJsonObject {
                put("package", PACKAGE_NAME)
                put("format", FORMAT_VERSION)
                put("exportedAt", System.currentTimeMillis())
                put("configVersion", JsonPrimitive(config.version))
            }
        return manifest.toString()
    }

    private companion object {
        /** 日志标签。 */
        const val TAG = "配置备份"

        /** 包内配置条目名。 */
        const val ENTRY_CONFIG = "config.json"

        /** 包内主题条目名。 */
        const val ENTRY_THEME = "theme.json"

        /** 包内清单条目名。 */
        const val ENTRY_MANIFEST = "manifest.json"

        /** 备份格式版本。 */
        const val FORMAT_VERSION = 1

        /** 包标识，导入时校验来源。 */
        const val PACKAGE_NAME = "com.shuiyu.octoberrain"
    }
}
