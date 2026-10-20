package com.shuiyu.octoberrain.core.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.BeautifyTheme
import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.GridAlign
import com.shuiyu.octoberrain.core.common.SortMode
import com.shuiyu.octoberrain.core.common.ThemeLayout
import com.shuiyu.octoberrain.core.common.ThemeRepository
import com.shuiyu.octoberrain.core.common.ThemeScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 美化主题仓库实现。
 *
 * 每个主题存放在 filesDir/beautify-themes/<id>，图标副本位于 icons 子目录，
 * 主题描述写入 theme.json；导出文件把图标转为 Base64 内嵌，便于单文件备份。
 * 主题同时保存桌面布局参数，应用后恢复列数、行数、间距、图标尺寸与对齐方式。
 */
class ThemeRepositoryImpl(
    private val context: Context,
    private val config: ConfigStore,
) : ThemeRepository {
    /** 主题根目录，懒创建。 */
    private val root: File
        get() = File(context.filesDir, "beautify-themes").apply { mkdirs() }

    /** 图标覆盖目录。 */
    private val overrideDir: File
        get() = File(context.filesDir, "icon-overrides").apply { mkdirs() }

    /** 已保存主题，按创建时间倒序。 */
    override fun themes(): List<BeautifyTheme> {
        val dirs = root.listFiles { file -> file.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { readTheme(it) }.sortedByDescending { it.createdAt }
    }

    /**
     * 保存当前桌面顺序、布局与图标覆盖为主题。
     *
     * 同名主题先删除再创建；scope 为 HOME 时只保留首页应用与对应图标覆盖，
     * 为 ALL 时保存全部应用顺序。主题内的图片与当前覆盖互不影响。
     */
    override suspend fun saveTheme(
        name: String,
        scope: ThemeScope,
    ): BeautifyTheme? =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = name.trim().ifEmpty { "未命名主题" }
                themes().firstOrNull { it.name == trimmed }?.let { deleteTheme(it.id) }
                val id = "theme-" + System.currentTimeMillis()
                val dir = File(root, id).apply { mkdirs() }
                val iconsDir = File(dir, "icons").apply { mkdirs() }
                val configValue = config.state.value
                val desktop = configValue.desktop
                val snapshot =
                    runCatching {
                        if (AppServices.hasLayoutProvider()) AppServices.layoutProvider.snapshot() else null
                    }.getOrNull()
                val fullOrder =
                    desktop.order.ifEmpty {
                        snapshot
                            ?.pages
                            ?.flatten()
                            ?.map { it.key }
                            .orEmpty()
                    }
                val order =
                    when (scope) {
                        ThemeScope.HOME ->
                            snapshot?.pages?.firstOrNull()?.map { it.key }
                                ?: fullOrder.take(firstPageCapacity(desktop))
                        ThemeScope.ALL -> fullOrder
                    }
                val targetPackages =
                    if (scope == ThemeScope.HOME) {
                        order.filter { it.startsWith("app:") }.map { it.removePrefix("app:") }.toSet()
                    } else {
                        configValue.appearance.iconOverrides.keys
                    }
                val source = configValue.appearance.iconOverrides
                val copied = mutableMapOf<String, String>()
                source.forEach { (pkg, path) ->
                    if (pkg !in targetPackages) return@forEach
                    val from = File(path)
                    if (!from.exists()) return@forEach
                    val to = File(iconsDir, safeName(pkg) + ".png")
                    from.copyTo(to, overwrite = true)
                    copied[pkg] = to.absolutePath
                }
                val theme =
                    BeautifyTheme(
                        id = id,
                        name = trimmed,
                        createdAt = System.currentTimeMillis(),
                        iconOverrides = copied,
                        order = order,
                        layout = desktop.toThemeLayout(),
                        scope = scope,
                    )
                writeTheme(dir, theme)
                theme
            }.getOrNull()
        }

    /**
     * 应用主题。
     *
     * 复制主题图标到覆盖目录并一次性写入配置；HOME 范围只调整首页应用位置，
     * 其余应用保持原有相对顺序。源图标缺失的应用被跳过，
     * 已卸载应用的顺序条目保留，由桌面自行忽略。
     */
    override suspend fun applyTheme(id: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(root, id)
                val theme = readTheme(dir) ?: return@withContext false
                val applied = mutableMapOf<String, String>()
                theme.iconOverrides.forEach { (pkg, path) ->
                    val from = File(path)
                    if (!from.exists()) return@forEach
                    val to = File(overrideDir, safeName(pkg) + ".png")
                    from.copyTo(to, overwrite = true)
                    applied[pkg] = to.absolutePath
                }
                config.edit { current ->
                    val merged =
                        if (theme.scope == ThemeScope.ALL) {
                            theme.order
                        } else {
                            val head = theme.order
                            head + current.desktop.order.filter { it !in head.toSet() }
                        }
                    val layout = theme.layout
                    val desktop =
                        if (layout == null) {
                            current.desktop.copy(order = merged)
                        } else {
                            current.desktop.copy(
                                order = merged,
                                columns = layout.columns,
                                rows = layout.rows,
                                columnSpacingDp = layout.columnSpacingDp,
                                rowSpacingDp = layout.rowSpacingDp,
                                iconSizeDp = layout.iconSizeDp,
                                labelTextSp = layout.labelTextSp,
                                firstPageRows = layout.firstPageRows,
                                gridAlign = layout.gridAlign,
                            )
                        }
                    current.copy(
                        appearance = current.appearance.copy(iconOverrides = applied),
                        desktop = desktop,
                    )
                }
                true
            }.getOrDefault(false)
        }

    /** 删除主题目录。 */
    override suspend fun deleteTheme(id: String) =
        withContext(Dispatchers.IO) {
            File(root, id).deleteRecursively()
            Unit
        }

    /**
     * 清空桌面个性化。
     *
     * 删除全部图标覆盖并清空顺序与布局覆盖，桌面回到自适应默认排布与系统图标；
     * 已保存主题文件保留。
     */
    override suspend fun resetDesktop(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                overrideDir.listFiles()?.forEach { it.deleteRecursively() }
                config.edit { current ->
                    current.copy(
                        appearance = current.appearance.copy(iconOverrides = emptyMap()),
                        desktop =
                            current.desktop.copy(
                                order = emptyList(),
                                sortMode = SortMode.INSTALL_TIME,
                                columns = 0,
                                rows = 0,
                                columnSpacingDp = 0,
                                rowSpacingDp = 0,
                                iconSizeDp = 0,
                                labelTextSp = 0f,
                                firstPageRows = 0,
                                gridAlign = GridAlign.CENTER,
                            ),
                    )
                }
                true
            }.getOrDefault(false)
        }

    /**
     * 导出主题为单文件。
     *
     * 结构为 name、createdAt、order、icons、layout、scope；
     * icons 的值为 Base64 编码的 PNG，兼容旧版缺少 layout 与 scope 的文件。
     */
    override suspend fun exportTheme(
        id: String,
        uri: Uri,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val theme = readTheme(File(root, id)) ?: return@withContext false
                val iconsObject =
                    buildJsonObject {
                        theme.iconOverrides.forEach { (pkg, path) ->
                            val bytes = File(path).takeIf { it.exists() }?.readBytes() ?: return@forEach
                            put(pkg, JsonPrimitive(Base64.encodeToString(bytes, Base64.NO_WRAP)))
                        }
                    }
                val payload =
                    buildJsonObject {
                        put("name", JsonPrimitive(theme.name))
                        put("createdAt", JsonPrimitive(theme.createdAt))
                        put("order", JsonArray(theme.order.map { JsonPrimitive(it) }))
                        put("icons", iconsObject)
                        put("scope", JsonPrimitive(theme.scope.name))
                        theme.layout?.let { layout ->
                            put(
                                "layout",
                                buildJsonObject {
                                    put("columns", JsonPrimitive(layout.columns))
                                    put("rows", JsonPrimitive(layout.rows))
                                    put("columnSpacingDp", JsonPrimitive(layout.columnSpacingDp))
                                    put("rowSpacingDp", JsonPrimitive(layout.rowSpacingDp))
                                    put("iconSizeDp", JsonPrimitive(layout.iconSizeDp))
                                    put("labelTextSp", JsonPrimitive(layout.labelTextSp))
                                    put("firstPageRows", JsonPrimitive(layout.firstPageRows))
                                    put("gridAlign", JsonPrimitive(layout.gridAlign.name))
                                },
                            )
                        }
                    }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(payload.toString().toByteArray())
                    true
                } ?: false
            }.getOrDefault(false)
        }

    /**
     * 从单文件导入主题。
     *
     * 图片 Base64 解码后写入新主题目录，主题名称冲突时追加时间后缀；
     * 旧版文件缺少 layout 与 scope 时按 ALL 与空布局处理。
     */
    override suspend fun importTheme(uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val text =
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: return@withContext false
                val payload = AppJson.format.parseToJsonElement(text).jsonObject
                val name = payload["name"]?.jsonPrimitive?.content ?: "导入主题"
                val createdAt =
                    payload["createdAt"]?.jsonPrimitive?.content?.toLongOrNull()
                        ?: System.currentTimeMillis()
                val order = payload["order"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val icons = payload["icons"]?.jsonObject ?: JsonObject(emptyMap())
                val scope =
                    runCatching {
                        ThemeScope.valueOf(payload["scope"]?.jsonPrimitive?.content ?: "ALL")
                    }.getOrDefault(ThemeScope.ALL)
                val layout =
                    runCatching {
                        payload["layout"]?.jsonObject?.let { obj ->
                            ThemeLayout(
                                columns = obj["columns"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                rows = obj["rows"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                columnSpacingDp = obj["columnSpacingDp"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                rowSpacingDp = obj["rowSpacingDp"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                iconSizeDp = obj["iconSizeDp"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                labelTextSp = obj["labelTextSp"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                                firstPageRows = obj["firstPageRows"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                gridAlign =
                                    runCatching {
                                        GridAlign.valueOf(obj["gridAlign"]?.jsonPrimitive?.content ?: "CENTER")
                                    }.getOrDefault(GridAlign.CENTER),
                            )
                        }
                    }.getOrNull()
                val id = "theme-" + System.currentTimeMillis()
                val dir = File(root, id).apply { mkdirs() }
                val iconsDir = File(dir, "icons").apply { mkdirs() }
                val copied = mutableMapOf<String, String>()
                icons.forEach { (pkg, value) ->
                    runCatching {
                        val bytes = Base64.decode(value.jsonPrimitive.content, Base64.DEFAULT)
                        val to = File(iconsDir, safeName(pkg) + ".png")
                        to.writeBytes(bytes)
                        copied[pkg] = to.absolutePath
                    }
                }
                val theme = BeautifyTheme(id, uniqueName(name), createdAt, copied, order, layout, scope)
                writeTheme(dir, theme)
                true
            }.getOrDefault(false)
        }

    /** 读取主题描述，文件缺失或解析失败返回 null。 */
    private fun readTheme(dir: File): BeautifyTheme? {
        val file = File(dir, "theme.json")
        if (!file.exists()) return null
        return runCatching {
            AppJson.format.decodeFromString(BeautifyTheme.serializer(), file.readText())
        }.getOrNull()
    }

    /** 写入主题描述。 */
    private fun writeTheme(
        dir: File,
        theme: BeautifyTheme,
    ) {
        File(dir, "theme.json").writeText(AppJson.format.encodeToString(BeautifyTheme.serializer(), theme))
    }

    /** 生成不与现有主题重复的名称。 */
    private fun uniqueName(base: String): String {
        val names = themes().map { it.name }.toSet()
        if (base !in names) return base
        var index = 2
        while ("$base $index" in names) index++
        return "$base $index"
    }

    /** 把包名转换为可用于文件名的安全字符串。 */
    private fun safeName(packageName: String): String = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** 首页可容纳的条目数量，布局服务缺失时用于裁剪顺序。 */
    private fun firstPageCapacity(desktop: com.shuiyu.octoberrain.core.common.DesktopConfig): Int {
        val columns = desktop.columns.coerceAtLeast(3)
        val rows = desktop.firstPageRows.coerceAtLeast(1)
        return columns * rows
    }
}

/** 桌面配置转换为主题布局快照。 */
private fun com.shuiyu.octoberrain.core.common.DesktopConfig.toThemeLayout(): ThemeLayout =
    ThemeLayout(
        columns = columns,
        rows = rows,
        columnSpacingDp = columnSpacingDp,
        rowSpacingDp = rowSpacingDp,
        iconSizeDp = iconSizeDp,
        labelTextSp = labelTextSp,
        firstPageRows = firstPageRows,
        gridAlign = gridAlign,
    )
