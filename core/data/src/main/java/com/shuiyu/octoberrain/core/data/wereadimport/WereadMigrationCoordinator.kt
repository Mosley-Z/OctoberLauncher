package com.shuiyu.octoberrain.core.data.wereadimport

import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.WereadDeskConfig
import com.shuiyu.octoberrain.core.common.WereadMigrationState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException

/** 迁移结果。 */
sealed interface WereadMigrationOutcome {
    /**
     * 迁移成功。
     *
     * @property counts 条目数量统计
     * @property backup 旧数据备份目录，首次迁移时为 null
     * @property skipped 是否因摘要一致而跳过
     */
    data class Success(
        val counts: WereadImportCounts,
        val backup: String?,
        val skipped: Boolean,
    ) : WereadMigrationOutcome

    /**
     * 迁移失败。
     *
     * @property error 失败分类
     * @property message 可展示的失败说明
     */
    data class Failure(
        val error: WereadImportError,
        val message: String,
    ) : WereadMigrationOutcome
}

/**
 * 微读墨记数据迁移编排。
 *
 * 流程固定为：解析迁移包 → 比对清单摘要判断幂等 → 落位数据目录 → 写配置迁移状态。
 * 任何一步失败都不修改配置，落位器已保证旧数据不被破坏；成功后写入迁移状态，
 * 后续启动按摘要跳过重复导入。
 *
 * 迁移不依赖网络，数据全部来自导出文件；导出失败或版本过低由调用方在进入本类之前拦截。
 *
 * @param config 配置存储，用于写入密钥、成就与迁移状态
 * @param store 数据落位器，指向内核数据根目录
 */
class WereadMigrationCoordinator(
    private val config: ConfigStore,
    private val store: WereadImportStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 执行迁移。
     *
     * @param pack 导出端产生的完整迁移包字节
     * @param key 32 字节迁移密钥，仅本次调用有效
     * @param nowMillis 迁移完成时间，供测试注入固定值
     * @return 成功、跳过或失败结果
     */
    fun import(
        pack: ByteArray,
        key: ByteArray,
        nowMillis: Long = System.currentTimeMillis(),
    ): WereadMigrationOutcome {
        val manifestDigest = digestOfManifest(pack)
        val current = config.state.value

        // 摘要一致且上次已完成时直接跳过，保证重复启动不重复导入
        if (manifestDigest.isNotEmpty() &&
            current.wereadMigration.complete &&
            current.wereadMigration.manifestDigest == manifestDigest
        ) {
            return WereadMigrationOutcome.Success(
                counts = WereadImportCounts(),
                backup = null,
                skipped = true,
            )
        }

        return when (val parsed = WereadImportReader.read(pack, key)) {
            is WereadImportResult.Failure ->
                WereadMigrationOutcome.Failure(parsed.error, parsed.message)

            is WereadImportResult.Success -> {
                val installed =
                    store.install(parsed.entries, nowMillis) { root ->
                        normalizeAssetBooks(root)
                        applyConfig(parsed, manifestDigest, nowMillis)
                    }
                when (installed) {
                    is WereadImportInstallResult.Failure ->
                        WereadMigrationOutcome.Failure(WereadImportError.WRITE_FAILED, installed.message)

                    is WereadImportInstallResult.Success -> {
                        WereadMigrationOutcome.Success(
                            counts = parsed.manifest.counts,
                            backup = installed.backup?.absolutePath,
                            skipped = false,
                        )
                    }
                }
            }
        }
    }

    /**
     * 把导出端平铺的资产书目补成内核读取的键名。
     *
     * 微读侧导出时按偏好文件逐个合并，资产书目的键名 `books` 与书架数据同名；
     * 内核读取的键为 `asset_books`，值为含 `books` 数组的对象。此处按内核约定
     * 补写一份包装值，原键保持不动，迁移数据因此可被负一屏阅读资产分区直接使用。
     *
     * @param root 迁移数据根目录
     */
    private fun normalizeAssetBooks(root: File) {
        val file = File(root, "kernel_prefs.json")
        val source = readPrefsStringGroup(file)
        val books = source?.let { (it[ASSET_BOOKS_SOURCE_KEY] as? JsonPrimitive)?.contentOrNull }
        val needsWrap = source != null && books != null && books.isNotBlank()
        if (needsWrap && !source.containsKey(ASSET_BOOKS_KEY)) {
            val wrapped = JsonPrimitive("""{"books":$books}""")
            val updatedStrings = JsonObject(source + (ASSET_BOOKS_KEY to wrapped))
            val updatedRoot = JsonObject(readPrefsRoot(file) + ("string" to updatedStrings))
            file.writeText(updatedRoot.toString())
        }
    }

    /**
     * 读取内核偏好文件的根对象。
     *
     * @param file 内核偏好文件
     * @return 解析后的根对象，文件缺失或结构不合法时返回空对象
     */
    private fun readPrefsRoot(file: File): JsonObject {
        val text = if (file.isFile) runCatching { file.readText() }.getOrNull() else null
        return text?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?: JsonObject(emptyMap())
    }

    /**
     * 读取内核偏好文件中的字符串分组。
     *
     * @param file 内核偏好文件
     * @return 字符串分组对象，缺失时返回 null
     */
    private fun readPrefsStringGroup(file: File): JsonObject? = readPrefsRoot(file)["string"] as? JsonObject

    /**
     * 写入迁移带出的配置项。
     *
     * 密钥从 kernel_prefs.json 的 string.api_key 读取，成就原文从 achievement.json 读取；
     * 两者缺失时保持原有配置不变。
     *
     * @param parsed 解析成功的迁移包
     * @param manifestDigest 清单摘要
     * @param nowMillis 迁移完成时间
     */
    private fun applyConfig(
        parsed: WereadImportResult.Success,
        manifestDigest: String,
        nowMillis: Long,
    ) {
        val prefs = parsed.entries["kernel_prefs.json"]?.let(::parsePrefs)
        val apiKey =
            prefs
                ?.get("string")
                ?.get("api_key")
                ?.jsonPrimitive
                ?.content
                ?.trim()
                .orEmpty()
        val achievement = parsed.entries["achievement.json"]?.toString(Charsets.UTF_8).orEmpty()

        val migration =
            WereadMigrationState(
                sourceVersionCode = parsed.manifest.sourceVersionCode,
                importedAtMillis = nowMillis,
                manifestDigest = manifestDigest,
                complete = true,
            )
        config.edit { config ->
            val desk = parsed.entries["desk_prefs.json"]?.let(::parseDeskPrefs)
            val currentDesk = config.integrations.wereadDesk
            val deskConfig =
                if (desk != null && desk.updatedAt > currentDesk.syncedAtMillis) {
                    currentDesk.copy(
                        order = desk.order.ifEmpty { currentDesk.order },
                        onMask = desk.onMask,
                        syncedAtMillis = desk.updatedAt,
                    )
                } else {
                    currentDesk
                }
            val base =
                if (apiKey.isNotEmpty()) {
                    config.integrations.copy(wereadApiKey = apiKey)
                } else {
                    config.integrations
                }
            val integrations = base.copy(wereadDesk = fingerprintConfig(deskConfig, prefs), wereadSource = "imported")
            val achievementConfig =
                if (achievement.isNotEmpty()) {
                    config.wereadAchievement.copy(rawJson = achievement, updatedAtMillis = nowMillis)
                } else {
                    config.wereadAchievement
                }
            config.copy(
                integrations = integrations,
                wereadAchievement = achievementConfig,
                wereadMigration = migration,
            )
        }
        val saved = config.state.value
        if (saved.wereadMigration != migration ||
            saved.integrations.wereadSource != "imported"
        ) {
            throw IOException("迁移配置保存失败，已恢复原数据")
        }
    }

    /** 原盘 PagePrefs 把 fp_books 存在 string、fp_font_px 存在 int 分组。 */
    private fun fingerprintConfig(
        current: WereadDeskConfig,
        prefs: Map<String, JsonObject>?,
    ): WereadDeskConfig {
        val books =
            prefs
                ?.get("string")
                ?.get("fp_books")
                ?.jsonPrimitive
                ?.contentOrNull
        val font =
            prefs
                ?.get("int")
                ?.get("fp_font_px")
                ?.jsonPrimitive
                ?.intOrNull
        val selection =
            if (books != null && !current.fpBooksConfigured) {
                WereadDeskConfig(fpBookIds = books.split(',')).effectiveFpBookIds
            } else {
                current.fpBookIds
            }
        return current.copy(
            fpBookIds = selection,
            fpBooksConfigured = current.fpBooksConfigured || books != null,
            fpFontPx = font?.takeIf { it in WereadDeskConfig.FP_FONT_OPTIONS } ?: current.fpFontPx,
        )
    }

    /**
     * 解析 kernel_prefs.json 的顶层分组结构。
     *
     * @param bytes 条目明文
     * @return 分组对象，解析失败返回 null
     */
    private fun parsePrefs(bytes: ByteArray): Map<String, JsonObject>? =
        runCatching {
            val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: return null
            buildMap {
                for ((group, value) in root) {
                    if (value is JsonObject) put(group, value)
                }
            }
        }.getOrNull()

    /**
     * 解析 desk_prefs.json 的墨台分区顺序、显示位与更新时间。
     *
     * 文件由微读墨记导出桥生成，结构为 order 数组、onMask 整数、updatedAt 毫秒。
     * 任一字段缺失或类型不符时取默认值，整体解析失败返回 null 交由调用方跳过应用。
     *
     * @param bytes 条目明文
     * @return 解析结果，失败返回 null
     */
    private fun parseDeskPrefs(bytes: ByteArray): DeskPrefs? =
        runCatching {
            val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: return null
            val order =
                (root["order"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .orEmpty()
            val onMask = root["onMask"]?.jsonPrimitive?.intOrNull ?: WereadDeskConfig.ON_MASK_DEFAULT
            val updatedAt = root["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0
            DeskPrefs(order, onMask, updatedAt)
        }.getOrNull()

    /**
     * 计算清单行的摘要，用于幂等判定。
     *
     * @param pack 迁移包完整字节
     * @return 摘要十六进制字符串；清单行缺失时返回空字符串
     */
    private fun digestOfManifest(pack: ByteArray): String {
        val newline = pack.indexOf('\n'.code.toByte())
        if (newline <= 0) return ""
        return WereadImportReader.sha256Hex(pack.copyOf(newline))
    }

    /**
     * 微读墨记墨台配置的解析结果。
     *
     * @property order 分区标识顺序
     * @property onMask 显示位掩码
     * @property updatedAt 导出侧的最后写入时间，用于判断两侧哪一份更新
     */
    private data class DeskPrefs(
        val order: List<String>,
        val onMask: Int,
        val updatedAt: Long,
    )

    private companion object {
        /** 内核读取资产书目使用的键名。 */
        const val ASSET_BOOKS_KEY: String = "asset_books"

        /** 导出端平铺资产书目时使用的键名。 */
        const val ASSET_BOOKS_SOURCE_KEY: String = "books"
    }
}
