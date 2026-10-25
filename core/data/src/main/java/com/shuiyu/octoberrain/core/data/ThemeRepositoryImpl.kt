package com.shuiyu.octoberrain.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.BeautifyTheme
import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.GridAlign
import com.shuiyu.octoberrain.core.common.IconTargets
import com.shuiyu.octoberrain.core.common.SortMode
import com.shuiyu.octoberrain.core.common.ThemeLayout
import com.shuiyu.octoberrain.core.common.ThemeRepository
import com.shuiyu.octoberrain.core.common.ThemeScope
import com.shuiyu.octoberrain.core.common.ThemeWallpaper
import com.shuiyu.octoberrain.core.common.firstGridColumns
import com.shuiyu.octoberrain.core.common.firstGridRows
import com.shuiyu.octoberrain.core.render.ImageDecoder
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
                val previous = themes().firstOrNull { it.name == trimmed }
                val id = "theme-" + System.currentTimeMillis()
                val dir = File(root, id).apply { mkdirs() }
                val iconsDir = File(dir, "icons").apply { mkdirs() }
                val configValue = config.state.value
                val desktop = configValue.desktop
                val snapshot =
                    runCatching {
                        if (AppServices.hasLayoutProvider()) AppServices.layoutProvider.snapshot() else null
                    }.getOrNull()
                val order = themeOrder(desktop, snapshot, scope)
                val source = configValue.appearance.iconOverrides.mapKeys { IconTargets.normalizeStoredKey(it.key) }
                val targetPackages =
                    if (scope == ThemeScope.HOME) {
                        order.map { IconTargets.normalizeStoredKey(it) }.toSet()
                    } else {
                        source.keys
                    }
                val copied = saveIcons(source.filterKeys { it in targetPackages }, iconsDir)
                val theme =
                    BeautifyTheme(
                        id = id,
                        name = trimmed,
                        createdAt = System.currentTimeMillis(),
                        iconOverrides = copied,
                        order = order,
                        layout = desktop.toThemeLayout(),
                        scope = scope,
                        transparentIconTargets =
                            configValue.appearance.transparentIconTargets
                                .filter {
                                    scope ==
                                        ThemeScope.ALL ||
                                        it in targetPackages
                                }.toSet(),
                        showLabels = configValue.appearance.showLabels,
                        wallpaper = saveWallpaper(dir, configValue.appearance),
                    )
                writeTheme(dir, theme)
                previous?.let { deleteTheme(it.id) }
                theme
            }.getOrNull()
        }

    private fun themeOrder(
        desktop: DesktopConfig,
        snapshot: com.shuiyu.octoberrain.core.common.DesktopLayoutSnapshot?,
        scope: ThemeScope,
    ): List<String> {
        val fullOrder =
            desktop.order.ifEmpty {
                snapshot
                    ?.pages
                    ?.flatten()
                    ?.map { it.key }
                    .orEmpty()
            }
        return when (scope) {
            ThemeScope.HOME ->
                snapshot?.pages?.firstOrNull()?.map { it.key }
                    ?: fullOrder.take(firstPageCapacity(desktop))
            ThemeScope.ALL -> fullOrder
        }
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
                // 补位需要主题应用前的设备顺序，并剔除配置里残留但已卸载的应用条目；
                // 否则上一次导入写入的外来顺序会让补位失去参照。
                val deviceOrder = installedEntries(config.state.value.desktop.order)
                val wallpaper = theme.wallpaper?.let { applyWallpaper(it) }
                val applied = resolveIcons(theme, deviceOrder)
                config.edit { current ->
                    // 顺序属于设备自身偏好，主题只贡献图标与外观参数；
                    // 若把对方设备的顺序整表写入，本机不存在的应用会占位并让后续补位失去参照。
                    val desktop = applyThemeLayout(current.desktop, theme.layout, theme.scope)
                    current.copy(
                        appearance =
                            mergeThemeAppearance(current.appearance, theme, applied, deviceOrder).let { appearance ->
                                if (wallpaper ==
                                    null
                                ) {
                                    appearance
                                } else {
                                    appearance.copy(
                                        wallpaperPath = wallpaper.path,
                                        wallpaperDim = wallpaper.dim,
                                        wallpaperQuantize = wallpaper.quantize,
                                        wallpaperWhiteOverlay = false,
                                        wallpaperLandscapeRotation =
                                            wallpaper.landscapeRotation
                                                ?: appearance.wallpaperLandscapeRotation,
                                    )
                                }
                            },
                        desktop = desktop,
                    )
                }
                true
            }.getOrDefault(false)
        }

    /**
     * 把主题图标映射到当前设备可用的应用，返回「包名 → 图标路径」。
     *
     * 分两轮处理。第一轮按包名直接匹配，跨设备导入时同名应用优先拿到自己的图标；
     * 第二轮处理主题里存在、当前设备没有的包名，按它在主题顺序表中的位置取当前设备
     * 同位置的应用补位，使「换一台设备后仍按顺序得到前几张卡片的图标」成立。
     * 已被占用的目标应用不再被覆盖，避免同一应用收到两张图标。
     *
     * @param theme 待应用主题
     * @param deviceOrder 主题应用前的设备顺序条目，形如 app:包名 与 special:名称
     * @return 目标包名到图标文件绝对路径的映射
     */
    private fun resolveIcons(
        theme: BeautifyTheme,
        deviceOrder: List<String>,
    ): Map<String, String> {
        val targets =
            resolveThemeIconTargets(
                sourcePackages = theme.iconOverrides.keys,
                themeOrder = theme.order,
                deviceOrder = deviceOrder,
            )
        val applied = mutableMapOf<String, String>()
        targets.forEach { (sourcePkg, targetPkg) ->
            val path = theme.iconOverrides[sourcePkg] ?: return@forEach
            copyIcon(targetPkg, path)?.let { applied[targetPkg] = it }
        }
        return applied
    }

    /**
     * 复现桌面条目顺序，供主题图标按位置补位使用。
     *
     * 规则与桌面一致：可见应用按配置的排序模式排列，特殊入口追加在后，
     * 若 order 非空则按 order 的序号稳定排序，未出现在 order 中的条目排在末尾。
     * 返回序列中的 `app:` 前缀条目顺序即用户在桌面上看到的应用顺序。
     */
    private fun installedEntries(order: List<String>): List<String> {
        val desktop = AppServices.config.state.value.desktop
        val catalog =
            AppServices.catalog.apps.value
                .map { it.packageName }
                .filter { it !in desktop.hiddenPackages }
        val appEntries =
            if (catalog.isNotEmpty()) {
                catalog.map { APP_PREFIX + it }
            } else {
                context.packageManager
                    .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { APP_PREFIX + it.activityInfo.packageName }
                    .distinct()
            }
        val specialEntries = SPECIAL_ENTRIES.filter { it !in desktop.hiddenSpecials }
        val base = appEntries + specialEntries
        if (order.isEmpty()) return base
        val rank = order.withIndex().associate { it.value to it.index }
        return base.sortedBy { rank[it] ?: Int.MAX_VALUE }
    }

    /** 把源图标复制到覆盖目录，源文件缺失时返回 null。 */
    private fun copyIcon(
        targetId: String,
        path: String,
    ): String? {
        val from = File(path)
        if (!from.exists()) return null
        // 独立路径使相同目标切换主题时也能触发图标缓存刷新。
        val to = File(overrideDir, "theme-${System.nanoTime()}-${IconFileNames.fileName(targetId)}")
        return runCatching {
            from.copyTo(to, overwrite = true)
            to.absolutePath
        }.getOrNull()
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
                        appearance =
                            current.appearance.copy(
                                iconOverrides = emptyMap(),
                                transparentIconTargets = emptySet(),
                            ),
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
                        theme.transparentIconTargets?.let {
                            put(
                                "transparentIconTargets",
                                JsonArray(it.map(::JsonPrimitive)),
                            )
                        }
                        theme.showLabels?.let { put("showLabels", JsonPrimitive(it)) }
                        theme.wallpaper?.let { wallpaper ->
                            put(
                                "wallpaper",
                                buildJsonObject {
                                    put("dim", JsonPrimitive(wallpaper.dim))
                                    put("quantize", JsonPrimitive(wallpaper.quantize))
                                    put("whiteOverlay", JsonPrimitive(false))
                                    wallpaper.landscapeRotation?.let { put("landscapeRotation", JsonPrimitive(it)) }
                                    wallpaper.path?.let { path ->
                                        requireValidImage(File(path))
                                        put(
                                            "image",
                                            JsonPrimitive(
                                                Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP),
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                        theme.layout?.let { layout ->
                            put(
                                "layout",
                                AppJson.format.encodeToJsonElement(ThemeLayout.serializer(), layout),
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
                            AppJson.format.decodeFromJsonElement(ThemeLayout.serializer(), obj)
                        }
                    }.getOrNull()
                val id = "theme-" + System.currentTimeMillis()
                val dir = File(root, id).apply { mkdirs() }
                val iconsDir = File(dir, "icons").apply { mkdirs() }
                val copied = mutableMapOf<String, String>()
                icons.forEach { (storedKey, value) ->
                    val bytes = Base64.decode(value.jsonPrimitive.content, Base64.DEFAULT)
                    val targetId = IconTargets.normalizeStoredKey(storedKey)
                    val to = File(iconsDir, IconFileNames.fileName(targetId))
                    to.writeBytes(bytes)
                    requireValidImage(to)
                    copied[targetId] = to.absolutePath
                }
                val transparent =
                    payload["transparentIconTargets"]
                        ?.jsonArray
                        ?.map {
                            IconTargets.normalizeStoredKey(it.jsonPrimitive.content)
                        }?.toSet()
                val showLabels = payload["showLabels"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                val wallpaper = payload["wallpaper"]?.jsonObject?.let { importWallpaper(it, dir) }
                val theme =
                    BeautifyTheme(
                        id,
                        uniqueName(name),
                        createdAt,
                        copied,
                        order,
                        layout,
                        scope,
                        transparent,
                        showLabels,
                        wallpaper,
                    )
                writeTheme(dir, theme)
                true
            }.getOrDefault(false)
        }

    private fun saveIcons(
        source: Map<String, String>,
        directory: File,
    ): Map<String, String> {
        val copied = mutableMapOf<String, String>()
        source.forEach { (targetId, path) ->
            val from = File(path)
            if (from.exists()) {
                val to = File(directory, IconFileNames.fileName(targetId))
                from.copyTo(to, overwrite = true)
                copied[targetId] = to.absolutePath
            }
        }
        return copied
    }

    private fun importWallpaper(
        obj: JsonObject,
        dir: File,
    ): ThemeWallpaper {
        require(obj["path"]?.jsonPrimitive?.content.isNullOrBlank() || obj.containsKey("image")) { "主题缺少壁纸资源" }
        val decoded =
            AppJson.format.decodeFromJsonElement(
                ThemeWallpaper.serializer(),
                JsonObject(obj.filterKeys { it != "image" && it != "path" }),
            )
        val path =
            obj["image"]?.jsonPrimitive?.content?.let { encoded ->
                val image = File(dir, "wallpaper.img")
                image.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
                requireValidImage(image)
                image.absolutePath
            }
        return decoded.copy(path = path)
    }

    /** 保存壁纸为主题私有资源，缺失或不可解码时整个保存失败。 */
    private fun saveWallpaper(
        dir: File,
        appearance: AppearanceConfig,
    ): ThemeWallpaper {
        val path =
            appearance.wallpaperPath?.takeIf { it.isNotBlank() }?.let { source ->
                requireValidImage(File(source))
                File(source).copyTo(File(dir, "wallpaper.img"), overwrite = true).absolutePath
            }
        return ThemeWallpaper(
            path,
            appearance.wallpaperDim,
            appearance.wallpaperQuantize,
            false,
            appearance.wallpaperLandscapeRotation,
        )
    }

    /** 应用前准备独立副本，主题删除不会破坏产品壁纸。 */
    private fun applyWallpaper(wallpaper: ThemeWallpaper): ThemeWallpaper {
        val path =
            wallpaper.path?.let { source ->
                requireValidImage(File(source))
                val directory = File(context.filesDir, "wallpaper").apply { mkdirs() }
                File(source).copyTo(File(directory, "theme-${System.nanoTime()}.img"), overwrite = false).absolutePath
            }
        return wallpaper.copy(
            path = path,
            dim = wallpaper.dim.coerceIn(0f, 1f),
            quantize = wallpaper.quantize.coerceIn(0, 3),
            landscapeRotation = wallpaper.landscapeRotation?.takeIf { it in listOf(0, 90, 180, 270) },
        )
    }

    private fun requireValidImage(file: File) {
        require(file.isFile && ImageDecoder.size(file.absolutePath) != null) { "主题图片不可用" }
        val bitmap = ImageDecoder.decode(file.absolutePath, 64, keepAlpha = true)
        requireNotNull(bitmap) { "主题图片解码失败" }
        bitmap.recycle()
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

    /** 首页可容纳的条目数量，布局服务缺失时用于裁剪顺序。 */
    private fun firstPageCapacity(desktop: com.shuiyu.octoberrain.core.common.DesktopConfig): Int {
        val columns =
            if (desktop.kind == com.shuiyu.octoberrain.core.common.DesktopKind.LIST) {
                1
            } else {
                desktop.firstGridColumns(
                    desktop.orientation != com.shuiyu.octoberrain.core.common.DesktopOrientation.PORTRAIT,
                )
            }
        val rows =
            (
                if (desktop.kind == com.shuiyu.octoberrain.core.common.DesktopKind.LIST) {
                    desktop.listFirstRows
                } else {
                    desktop.firstGridRows(
                        desktop.orientation != com.shuiyu.octoberrain.core.common.DesktopOrientation.PORTRAIT,
                    )
                }
            ).coerceAtLeast(1)
        return columns * rows
    }
}

/** 桌面顺序表中的应用条目前缀，与配置里 order 的写法一致。 */
private const val APP_PREFIX = "app:"

/** 与桌面条目构建保持一致的内置特殊入口键，用于复现桌面顺序。 */
private val SPECIAL_ENTRIES =
    listOf(
        "special:weread",
        "special:files",
        "special:settings",
    )

/**
 * 计算主题图标应落到当前设备的哪些应用上。
 *
 * 第一轮按包名直接匹配，跨设备导入时同名应用优先拿到自己的图标；
 * 第二轮处理主题里存在、当前设备没有的包名，按它在主题顺序表中的位置取当前设备
 * 同位置的应用补位，使换设备后仍能按顺序得到前几张卡片的图标。
 * 已被占用的目标应用不再被覆盖，避免同一应用收到两张图标。
 *
 * @param sourcePackages 主题里存有图标的包名集合
 * @param themeOrder 主题保存时的完整顺序表
 * @param deviceOrder 当前设备的顺序表
 * @return 主题包名到当前设备包名的映射，无可用目标时该包名不出现
 */
internal fun resolveThemeIconTargets(
    sourcePackages: Collection<String>,
    themeOrder: List<String>,
    deviceOrder: List<String>,
): Map<String, String> {
    // 只按应用条目对齐：special 条目属于内置入口，不参与图标补位，也不占位。
    val themeApps = themeOrder.filter { it.startsWith(APP_PREFIX) }
    val deviceApps = deviceOrder.filter { it.startsWith(APP_PREFIX) }
    val positions = themeApps.withIndex().associate { (index, entry) -> entry to index }
    val known = deviceOrder.toSet()
    val resolved = LinkedHashMap<String, String>()
    // 第一轮：包名与特殊入口直接命中，特殊入口不参与补位。
    sourcePackages.forEach { targetId ->
        val direct = if (targetId.startsWith(IconTargets.SPECIAL_PREFIX)) targetId else APP_PREFIX + targetId
        if (known.contains(direct)) resolved[targetId] = targetId
    }
    // 第二轮：缺失应用按主题内应用序号回退到设备同序号应用。
    sourcePackages.forEach { pkg ->
        if (resolved.containsKey(pkg)) return@forEach
        val index = positions[APP_PREFIX + pkg] ?: return@forEach
        val target = deviceApps.getOrNull(index)?.removePrefix(APP_PREFIX) ?: return@forEach
        if (resolved.containsValue(target)) return@forEach
        resolved[pkg] = target
    }
    return resolved
}

/** 桌面配置转换为主题布局快照。 */
private fun com.shuiyu.octoberrain.core.common.DesktopConfig.toThemeLayout(): ThemeLayout =
    ThemeLayout(
        columns = columns,
        rows = rows,
        firstPageColumns = firstPageColumns,
        portraitFirstPageRows = portraitFirstPageRows,
        portraitFirstPageColumns = portraitFirstPageColumns,
        landscapeFirstPageRows = landscapeFirstPageRows,
        landscapeFirstPageColumns = landscapeFirstPageColumns,
        listRows = listRows,
        listFirstRows = listFirstRows,
        listAlign = listAlign,
        listMarginDp = listMarginDp,
        columnSpacingDp = 0,
        rowSpacingDp = 0,
        iconSizeDp = iconSizeDp,
        labelTextSp = labelTextSp,
        firstPageRows = firstPageRows,
        gridAlign = gridAlign,
        portraitColumns = portraitColumns,
        portraitRows = portraitRows,
        landscapeColumns = landscapeColumns,
        landscapeRows = landscapeRows,
    )

/** HOME只替换主题涉及目标；透明仅精确匹配，不参与图片的位置补位。 */
internal fun mergeThemeAppearance(
    current: AppearanceConfig,
    theme: BeautifyTheme,
    images: Map<String, String>,
    deviceOrder: List<String>,
): AppearanceConfig {
    val known = deviceOrder.map(IconTargets::normalizeStoredKey).toSet()
    val involved = theme.order.map(IconTargets::normalizeStoredKey).toSet() + images.keys
    val transparent =
        theme.transparentIconTargets
            ?.map(IconTargets::normalizeStoredKey)
            ?.filter {
                it in known &&
                    (theme.scope == ThemeScope.ALL || it in involved)
            }?.toSet()
    val baseImages =
        if (theme.scope ==
            ThemeScope.ALL
        ) {
            emptyMap()
        } else {
            current.iconOverrides.filterKeys { it !in involved }
        }
    val mergedImages = (baseImages + images).filterKeys { transparent == null || it !in transparent }
    val baseTransparent =
        when {
            transparent == null -> current.transparentIconTargets
            theme.scope == ThemeScope.ALL -> emptySet()
            else -> current.transparentIconTargets - involved
        }
    return current.copy(
        iconOverrides = mergedImages,
        transparentIconTargets = (baseTransparent + transparent.orEmpty()) - mergedImages.keys,
        showLabels = theme.showLabels ?: current.showLabels,
    )
}

/** HOME不改其他页容量；旧列表主题仍从原rows字段恢复对应容量。 */
internal fun applyThemeLayout(
    current: DesktopConfig,
    layout: ThemeLayout?,
    scope: ThemeScope,
): DesktopConfig {
    if (layout == null) return current
    val homeOnly = scope == ThemeScope.HOME
    val capacity = resolveDirectionCapacity(current, layout, homeOnly)
    return current.copy(
        columns = if (homeOnly) current.columns else layout.columns,
        rows = if (homeOnly) current.rows else layout.rows,
        firstPageColumns = layout.firstPageColumns ?: layout.columns,
        firstPageRows = layout.firstPageRows,
        portraitFirstPageRows = layout.portraitFirstPageRows,
        portraitFirstPageColumns = layout.portraitFirstPageColumns,
        landscapeFirstPageRows = layout.landscapeFirstPageRows,
        landscapeFirstPageColumns = layout.landscapeFirstPageColumns,
        listRows = if (homeOnly) current.listRows else layout.listRows ?: layout.rows,
        listFirstRows = layout.listFirstRows ?: layout.firstPageRows,
        listAlign = layout.listAlign ?: current.listAlign,
        listMarginDp = layout.listMarginDp ?: current.listMarginDp,
        portraitColumns = capacity.portraitColumns,
        portraitRows = capacity.portraitRows,
        landscapeColumns = capacity.landscapeColumns,
        landscapeRows = capacity.landscapeRows,
        columnSpacingDp = 0,
        rowSpacingDp = 0,
        iconSizeDp = layout.iconSizeDp,
        labelTextSp = layout.labelTextSp,
        gridAlign = layout.gridAlign,
    )
}

/** 方向容量的合并结果。 */
private data class DirectionCapacity(
    val portraitColumns: Int,
    val portraitRows: Int,
    val landscapeColumns: Int,
    val landscapeRows: Int,
)

/**
 * 合并主题里的方向容量。
 *
 * 参数 current 为当前桌面配置；layout 为主题布局；homeOnly 表示仅应用首页范围。
 * 只应用首页时其他页容量保持不变；主题缺少方向字段时沿用当前值。
 */
private fun resolveDirectionCapacity(
    current: DesktopConfig,
    layout: ThemeLayout,
    homeOnly: Boolean,
): DirectionCapacity {
    if (homeOnly) {
        return DirectionCapacity(
            current.portraitColumns,
            current.portraitRows,
            current.landscapeColumns,
            current.landscapeRows,
        )
    }
    return DirectionCapacity(
        layout.portraitColumns ?: current.portraitColumns,
        layout.portraitRows ?: current.portraitRows,
        layout.landscapeColumns ?: current.landscapeColumns,
        layout.landscapeRows ?: current.landscapeRows,
    )
}
