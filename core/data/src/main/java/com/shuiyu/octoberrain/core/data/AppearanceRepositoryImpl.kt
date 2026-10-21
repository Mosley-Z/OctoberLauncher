package com.shuiyu.octoberrain.core.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.AppearanceRepository
import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.IconPackInfo
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.ThemeInfo
import com.shuiyu.octoberrain.core.design.ThemePalette
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.ImageSizing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * 外观仓库实现：壁纸文件、主题导入导出与图标包枚举。
 *
 * 桌面与锁屏壁纸分别复制到私有目录后记录路径，按目标尺寸采样解码、
 * 居中裁剪并按配置档位量化；主题以单文件 JSON 形式导入导出，包含壁纸与图标映射。
 */
class AppearanceRepositoryImpl(
    private val context: Context,
    private val config: ConfigStore,
) : AppearanceRepository {
    private val wallpaperDir = File(context.filesDir, "wallpaper").apply { mkdirs() }
    private val themeDir = File(context.filesDir, "themes").apply { mkdirs() }

    /** 图标包解析器，按当前配置中的图标包标识读取包内映射。 */
    private val iconPackResolver = IconPackResolver(context)

    override suspend fun setWallpaper(uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val current = config.state.value.appearance.wallpaperPath
                val swap = desktopSwap(uri)
                swap.execute(DESKTOP_PREFIX, System.currentTimeMillis(), current)
            }.onFailure { InkLog.w("外观", "设置壁纸失败", it) }.getOrDefault(false)
        }

    override fun clearWallpaper() {
        config.state.value.appearance.wallpaperPath?.let { path ->
            File(path).takeIf { it.isFile }?.delete()
        }
        config.edit { current ->
            current.copy(appearance = current.appearance.copy(wallpaperPath = null))
        }
    }

    override fun loadWallpaper(
        width: Int,
        height: Int,
        quantize: Int,
    ): Bitmap? {
        val path = config.state.value.appearance.wallpaperPath ?: return null
        return loadWallpaperFile(path, width, height, quantize)
    }

    override suspend fun setLockWallpaper(uri: Uri): Boolean {
        val applied = applyLockWallpaper(uri)
        return applied == LockWallpaperResult.APPLIED
    }

    /**
     * 保存锁屏壁纸并返回具体结果，供设置界面区分失败原因。
     *
     * 写入新文件、校验图片与更新配置的顺序由 [WallpaperSwap] 保证；校验回调记录图片是否可读为
     * 正尺寸，据此区分读取失败、内容不是图片与配置写入失败。图片校验只读文件头而不解码像素，
     * 超大图的解码压力由 [decodeWallpaper] 的采样与堆预算判断承担。
     *
     * 外观仓库接口只返回布尔值，需要具体失败原因的调用点可按本实现类型取用本方法。
     */
    suspend fun applyLockWallpaper(uri: Uri): LockWallpaperResult =
        withContext(Dispatchers.IO) {
            var validated: Boolean? = null
            val current =
                config.state.value.appearance
                    .lockWallpaperPath
                    .takeIf { path -> path.isNotBlank() }
            val applied =
                runCatching {
                    lockSwap(uri) { valid -> validated = valid }
                        .execute(LOCK_PREFIX, System.currentTimeMillis(), current)
                }.onFailure { InkLog.w("外观", "设置锁屏壁纸失败", it) }.getOrNull()
            if (applied == null) LockWallpaperResult.UNKNOWN else lockWallpaperResultOf(applied, validated)
        }

    override fun clearLockWallpaper() {
        val path = config.state.value.appearance.lockWallpaperPath
        if (path.isNotBlank()) File(path).takeIf { it.isFile }?.delete()
        config.edit { current ->
            current.copy(appearance = current.appearance.copy(lockWallpaperPath = ""))
        }
    }

    override fun loadLockWallpaper(
        width: Int,
        height: Int,
    ): Bitmap? {
        val appearance = config.state.value.appearance
        val path = appearance.lockWallpaperPath.takeIf { it.isNotBlank() } ?: return null
        return loadWallpaperFile(path, width, height, appearance.lockWallpaperQuantize)
    }

    override suspend fun exportTheme(uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val appearance = config.state.value.appearance
                val wallpaperBase64 =
                    appearance.wallpaperPath
                        ?.let { File(it).takeIf { file -> file.isFile }?.readBytes() }
                        ?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
                val payload =
                    ThemePayload(
                        name = appearance.themeId,
                        version = 1,
                        wallpaper = wallpaperBase64,
                        iconPackId = appearance.iconPackId,
                        labelFontPath = appearance.labelFontPath,
                        clockFont = appearance.clockFont,
                    )
                val text = AppJson.format.encodeToString(payload)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入目标文件")
                true
            }.onFailure { InkLog.w("外观", "导出主题失败", it) }.getOrDefault(false)
        }

    override suspend fun importTheme(uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val text =
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法读取主题文件")
                val payload = AppJson.format.decodeFromString<ThemePayload>(text)
                val wallpaperPath =
                    payload.wallpaper?.let { base64 ->
                        val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                        val target = File(wallpaperDir, "theme_${System.currentTimeMillis()}.png")
                        target.writeBytes(bytes)
                        target.absolutePath
                    }
                val themeId = payload.name.ifBlank { "custom" }
                File(themeDir, "${sanitize(themeId)}.json").writeText(text)
                config.edit { current ->
                    current.copy(
                        appearance =
                            current.appearance.copy(
                                themeId = themeId,
                                wallpaperPath = wallpaperPath ?: current.appearance.wallpaperPath,
                                iconPackId = payload.iconPackId ?: current.appearance.iconPackId,
                                labelFontPath = payload.labelFontPath ?: current.appearance.labelFontPath,
                                clockFont = payload.clockFont ?: current.appearance.clockFont,
                            ),
                    )
                }
                true
            }.onFailure { InkLog.w("外观", "导入主题失败", it) }.getOrDefault(false)
        }

    /**
     * 枚举可用的图标包。
     *
     * 标识统一采用图标包的应用包名，与 [IconPackResolver] 的解析口径一致；
     * 内置线稿占位项固定使用 [BUILTIN_ICON_PACK]。查询失败时只返回内置项。
     */
    override fun iconPacks(): List<IconPackInfo> {
        val builtIn = IconPackInfo(id = BUILTIN_ICON_PACK, label = "内置线稿", path = null)
        val installed =
            runCatching {
                val manager = context.packageManager
                val packs = LinkedHashMap<String, IconPackInfo>()
                ICON_PACK_ACTIONS.forEach { action ->
                    manager.queryIntentActivities(Intent(action), 0).forEach { info ->
                        val packageName = info.activityInfo?.packageName ?: return@forEach
                        if (packs.containsKey(packageName)) return@forEach
                        val label =
                            runCatching { info.loadLabel(manager).toString() }
                                .getOrDefault(packageName)
                        packs[packageName] =
                            IconPackInfo(
                                id = packageName,
                                label = label,
                                path = info.activityInfo?.applicationInfo?.sourceDir,
                            )
                    }
                }
                packs.values.toList()
            }.onFailure { InkLog.w("外观", "枚举图标包失败", it) }.getOrDefault(emptyList())
        return listOf(builtIn) + installed
    }

    override fun themes(): List<ThemeInfo> {
        val builtIn =
            listOf(
                ThemeInfo(id = "ink", label = "素白", description = "纯白纸面，最高对比度"),
                ThemeInfo(id = "paper", label = "米白", description = "轻微暖色，减轻夜间刺激"),
                ThemeInfo(id = "night", label = "墨黑", description = "深色底白色线，适合暗光环境"),
                ThemeInfo(id = "highcontrast", label = "高对比", description = "纯黑白，取消中间灰阶"),
            )
        val imported =
            themeDir
                .listFiles { file -> file.extension == "json" }
                ?.map { file ->
                    ThemeInfo(
                        id = file.nameWithoutExtension,
                        label = file.nameWithoutExtension,
                        description = "导入主题",
                    )
                }
                ?: emptyList()
        return builtIn + imported
    }

    override fun applyTheme(id: String) {
        // 内置预设自带完整配色，切换时清空旧的自定义令牌；导入主题保留自身令牌。
        val isPreset = ThemePalette.preset(id) != null
        config.edit { current ->
            current.copy(
                appearance =
                    current.appearance.copy(
                        themeId = id,
                        themeTokensJson = if (isPreset) "" else current.appearance.themeTokensJson,
                    ),
            )
        }
    }

    /**
     * 从当前图标包取图标。
     *
     * 未选择图标包或解析器不可用时返回 null，调用方回退系统图标；
     * 选中图标包但包内没有该应用映射时同样返回 null。
     *
     * @param packageName 应用包名。
     * @param sizePx 期望的图标边长，单位像素。
     */
    override suspend fun loadPackIcon(
        packageName: String,
        sizePx: Int,
    ): Bitmap? {
        if (!hasIconPack()) return null
        return runCatching { iconPackResolver.iconFor(packageName, sizePx) }
            .onFailure { InkLog.w("外观", "图标包读取失败：$packageName", it) }
            .getOrNull()
    }

    /** 当前是否启用可选图标包：配置选中非内置包且解析器可用时为 true。 */
    override fun hasIconPack(): Boolean {
        if (config.state.value.appearance.iconPackId == BUILTIN_ICON_PACK) return false
        return runCatching { iconPackResolver.isAvailable() }.getOrDefault(false)
    }

    /**
     * 读取图片头信息，用于校验文件是否为合法图片。
     *
     * 只要求宽高为正，不施加像素总量上限：超出上限的图片仍可保存，解码时按目标尺寸采样。
     */
    private fun decodeBounds(path: String): Rect? {
        val size = ImageDecoder.sizeIgnoringLimit(path) ?: return null
        return Rect(0, 0, size[0], size[1])
    }

    /** 构造桌面壁纸替换事务，配置确认更新后旧桌面文件才会被删除。 */
    private fun desktopSwap(uri: Uri): WallpaperSwap =
        WallpaperSwap(
            directory = wallpaperDir,
            openSource = { context.contentResolver.openInputStream(uri) },
            isValidImage = { file -> decodeBounds(file.absolutePath) != null },
            updateConfig = { path ->
                config.edit { state ->
                    state.copy(appearance = state.appearance.copy(wallpaperPath = path))
                }
                config.state.value.appearance.wallpaperPath == path
            },
        )

    /**
     * 构造锁屏壁纸替换事务，配置确认更新后旧锁屏文件才会被删除。
     *
     * 参数 onValidated 接收图片校验结果，用于在事务失败时区分失败原因。
     */
    private fun lockSwap(
        uri: Uri,
        onValidated: (Boolean) -> Unit,
    ): WallpaperSwap =
        WallpaperSwap(
            directory = wallpaperDir,
            openSource = { context.contentResolver.openInputStream(uri) },
            isValidImage = { file ->
                val valid = decodeBounds(file.absolutePath) != null
                onValidated(valid)
                valid
            },
            updateConfig = { path ->
                config.edit { state ->
                    state.copy(appearance = state.appearance.copy(lockWallpaperPath = path))
                }
                config.state.value.appearance.lockWallpaperPath == path
            },
        )

    /** 解码壁纸并按目标尺寸裁剪与量化，失败时记录日志并返回 null。 */
    private fun loadWallpaperFile(
        path: String,
        width: Int,
        height: Int,
        quantize: Int,
    ): Bitmap? =
        runCatching { decodeWallpaper(path, width, height, quantize) }
            .onFailure { InkLog.w("外观", "壁纸解码失败", it) }
            .getOrNull()

    /**
     * 按目标尺寸解码壁纸，保持比例居中裁剪并填充白底。
     *
     * 参数 quantize 为量化档位；档位非 0 时对目标尺寸位图抖动。
     * 解码与抖动均在调用方所在的 IO 线程执行，函数本身不切换线程。
     */
    private fun decodeWallpaper(
        path: String,
        width: Int,
        height: Int,
        quantize: Int,
    ): Bitmap? {
        val size = ImageDecoder.sizeIgnoringLimit(path)
        val sample = size?.let { ImageSizing.requestSample(it[0], it[1], width, height) }
        val decoded = sample?.let { ImageDecoder.decodeWithSample(path, it, keepAlpha = true) }
        return decoded?.let { renderWallpaper(it, width, height, quantize) }
    }

    /** 把解码结果按中心裁剪绘制到目标尺寸并执行量化。 */
    private fun renderWallpaper(
        decoded: Bitmap,
        width: Int,
        height: Int,
        quantize: Int,
    ): Bitmap? {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isAntiAlias = true }
        val target = Rect(0, 0, width, height)
        val window = WallpaperGeometry.centerCrop(decoded.width, decoded.height, width, height)
        if (window == null) {
            canvas.drawBitmap(decoded, null, target, paint)
        } else {
            val source = Rect(window.left, window.top, window.right, window.bottom)
            canvas.drawBitmap(decoded, source, target, paint)
        }
        if (decoded !== output) decoded.recycle()
        val quantized = WallpaperQuantize.apply(output, quantize)
        if (quantized !== output) output.recycle()
        return quantized
    }

    /** 将主题名转换为安全的文件名。 */
    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._\\u4e00-\\u9fa5-]"), "_")

    /** 主题文件结构。 */
    @Serializable
    private data class ThemePayload(
        val name: String = "custom",
        val version: Int = 1,
        val wallpaper: String? = null,
        val iconPackId: String? = null,
        val labelFontPath: String? = null,
        val clockFont: String? = null,
    )

    private companion object {
        /** 桌面壁纸文件名前缀。 */
        const val DESKTOP_PREFIX = "wallpaper"

        /** 锁屏壁纸文件名前缀。 */
        const val LOCK_PREFIX = "lock"

        /** 内置线稿图标包的标识。 */
        const val BUILTIN_ICON_PACK = "builtin"

        /** 图标包应用在清单中声明的主题动作，覆盖 ADW 与 GO 两类来源。 */
        val ICON_PACK_ACTIONS =
            listOf(
                "org.adw.launcher.THEMES",
                "com.gau.go.launcherex.theme",
            )
    }
}

/**
 * 锁屏壁纸保存结果。
 *
 * 供设置界面区分失败原因：读取失败、内容不是图片与配置写入失败三类可预期的失败各自单列，
 * 未预期的异常归入 [UNKNOWN]。
 */
enum class LockWallpaperResult {
    /** 文件已复制并写入配置。 */
    APPLIED,

    /** 无法打开所选文件的输入流，或复制过程中断。 */
    READ_FAILED,

    /** 文件可复制但无法识别为图片。 */
    NOT_IMAGE,

    /** 图片有效但配置写入未确认。 */
    CONFIG_FAILED,

    /** 事务抛出未预期的异常，原因未知。 */
    UNKNOWN,
}

/**
 * 依据替换事务的返回值与校验回调记录推断保存结果。
 *
 * @param applied 事务是否确认写入配置。
 * @param validated 校验回调的返回值，未触发校验时为 null。
 */
internal fun lockWallpaperResultOf(
    applied: Boolean,
    validated: Boolean?,
): LockWallpaperResult =
    when {
        applied -> LockWallpaperResult.APPLIED
        validated == false -> LockWallpaperResult.NOT_IMAGE
        validated == true -> LockWallpaperResult.CONFIG_FAILED
        else -> LockWallpaperResult.READ_FAILED
    }
