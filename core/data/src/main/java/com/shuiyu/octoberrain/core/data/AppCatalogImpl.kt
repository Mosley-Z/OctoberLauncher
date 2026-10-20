package com.shuiyu.octoberrain.core.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.LruCache
import com.shuiyu.octoberrain.core.common.AppCatalog
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.InstalledApp
import com.shuiyu.octoberrain.core.common.Router
import com.shuiyu.octoberrain.core.common.SpecialEntry
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.render.ImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale

/** 系统设置包名：桌面以「设置」特殊入口替代该应用图标。 */
private const val SYSTEM_SETTINGS_PACKAGE = "com.android.settings"

/**
 * 应用目录实现：枚举可启动应用，缓存图标，并提供内置入口。
 *
 * 图标先查内存缓存；未命中时若启用了图标包，优先取图标包中的映射图标；
 * 仍未命中则查磁盘缓存，最后从 PackageManager 读取并按目标尺寸缩放。
 * 内存缓存键与磁盘文件名均带当前图标包标识，切换图标包后不会复用旧图标。
 */
class AppCatalogImpl(
    private val context: Context,
) : AppCatalog {
    private val packageManager: PackageManager = context.packageManager

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    override val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val memoryCache = LruCache<String, Bitmap>(48)

    private val iconDir = File(context.filesDir, "icons").apply { mkdirs() }

    private val collator = Collator.getInstance(Locale.CHINA)

    override suspend fun refresh() {
        _apps.value = withContext(Dispatchers.IO) { queryLaunchable() }
    }

    override suspend fun loadIcon(
        packageName: String,
        sizePx: Int,
    ): Bitmap? {
        // 单应用覆盖优先：美化模式或长按菜单指定的图片直接作为图标
        if (AppServices.hasIconOverrides()) {
            AppServices.iconOverrides.loadOverride(packageName, sizePx)?.let { return it }
        }
        val packId = currentIconPackId()
        val key = memoryKey(packId, packageName, sizePx)
        memoryCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val diskFile = diskCacheFile(packId, packageName, sizePx)

            // 图标包优先：命中后写入内存与磁盘缓存，后续读取不再解析 appfilter
            val fromPack =
                if (packId != null && packIconEnabled()) {
                    loadPackIcon(packageName, sizePx)
                } else {
                    null
                }
            if (fromPack != null) {
                cacheAndPersist(key, diskFile, fromPack)
                return@withContext fromPack
            }

            // 磁盘缓存是之前按目标尺寸保存的 PNG，图标带透明边缘，解码保留透明通道
            val fromDisk =
                if (diskFile.isFile) {
                    ImageDecoder.decode(diskFile.absolutePath, sizePx, keepAlpha = true)
                } else {
                    null
                }
            if (fromDisk != null) {
                memoryCache.put(key, fromDisk)
                return@withContext fromDisk
            }

            val fromPackage =
                runCatching {
                    val drawable = packageManager.getApplicationIcon(packageName)
                    val raw =
                        Bitmap.createBitmap(
                            sizePx,
                            sizePx,
                            Bitmap.Config.ARGB_8888,
                        )
                    val canvas = Canvas(raw)
                    drawable.setBounds(0, 0, sizePx, sizePx)
                    drawable.draw(canvas)
                    raw
                }.getOrNull()
            if (fromPackage != null) {
                cacheAndPersist(key, diskFile, fromPackage)
            }
            fromPackage
        }
    }

    override suspend fun loadSpecialIcon(
        entry: SpecialEntry,
        sizePx: Int,
    ): Bitmap? {
        val key = "special:${entry.name}@$sizePx"
        memoryCache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val kind =
                when (entry) {
                    SpecialEntry.BOOKSHELF -> InkGlyphs.Kind.BOOK
                    SpecialEntry.FILES -> InkGlyphs.Kind.FOLDER
                    SpecialEntry.KOREADER -> InkGlyphs.Kind.BOOK
                    SpecialEntry.SEARCH -> InkGlyphs.Kind.SEARCH
                    SpecialEntry.SETTINGS -> InkGlyphs.Kind.GEAR
                    SpecialEntry.WEREAD -> InkGlyphs.Kind.BOOK
                }
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = InkColors.INK
                    style = Paint.Style.STROKE
                    strokeWidth = maxOf(2f, sizePx * 0.055f)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
            InkGlyphs.draw(canvas, kind, sizePx / 2f, sizePx / 2f, sizePx * 0.62f, paint)
            memoryCache.put(key, bitmap)
            bitmap
        }
    }

    override fun launch(app: InstalledApp) {
        val intent =
            packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent(
                Intent.ACTION_MAIN,
            ).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(app.packageName)
            }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
    }

    override fun launchSpecial(entry: SpecialEntry) {
        val router: Router? = if (AppServices.hasRouter()) AppServices.router else null
        val destination =
            when (entry) {
                SpecialEntry.BOOKSHELF -> Destination.Bookshelf
                SpecialEntry.FILES -> Destination.Files()
                SpecialEntry.KOREADER -> Destination.Koreader
                SpecialEntry.SEARCH -> Destination.Search
                SpecialEntry.SETTINGS -> Destination.Settings
                SpecialEntry.WEREAD -> Destination.Weread
            }
        if (router != null) {
            router.navigate(destination)
        }
    }

    /** 查询所有带启动图标的应用，排除自身与系统设置，按中文标签排序。 */
    private fun queryLaunchable(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(intent, 0)
        val result = ArrayList<InstalledApp>(resolved.size)
        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val packageName = activityInfo.packageName ?: continue
            if (packageName == context.packageName) continue
            // 系统设置由桌面的「设置」特殊入口承载，避免固件设置页在小屏上排版错乱
            if (packageName == SYSTEM_SETTINGS_PACKAGE) continue
            val label =
                runCatching {
                    activityInfo.loadLabel(packageManager).toString()
                }.getOrDefault(packageName)
            val firstInstall =
                runCatching {
                    packageManager.getPackageInfo(packageName, 0).firstInstallTime
                }.getOrDefault(0L)
            val isSystem =
                (
                    activityInfo.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM
                ) != 0
            result.add(
                InstalledApp(
                    packageName = packageName,
                    label = label,
                    activityName = activityInfo.name,
                    firstInstallTime = firstInstall,
                    isSystem = isSystem,
                ),
            )
        }
        result.sortWith { left, right -> collator.compare(left.label, right.label) }
        return result
    }

    /** 把包名转换为可用的文件名。 */
    private fun safeName(packageName: String): String = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /**
     * 读取当前启用的图标包标识。
     *
     * 配置服务尚未装配或图标包为内置时返回 null；每次调用时读取，
     * 避免应用启动早期的服务装配顺序影响结果。
     */
    private fun currentIconPackId(): String? {
        val raw =
            runCatching {
                AppServices.config.state.value.appearance.iconPackId
            }.getOrNull() ?: return null
        val id = raw.trim()
        return id.takeIf { it.isNotEmpty() && it != BUILTIN_ICON_PACK_ID }
    }

    /** 图标包是否可用；外观服务尚未装配时按不可用处理。 */
    private fun packIconEnabled(): Boolean = runCatching { AppServices.appearance.hasIconPack() }.getOrDefault(false)

    /** 调用图标包解析入口，异常与未命中统一返回 null。 */
    private suspend fun loadPackIcon(
        packageName: String,
        sizePx: Int,
    ): Bitmap? = runCatching { AppServices.appearance.loadPackIcon(packageName, sizePx) }.getOrNull()

    /** 内存缓存键，包含图标包标识以避免切换图标包后复用旧图标。 */
    private fun memoryKey(
        packId: String?,
        packageName: String,
        sizePx: Int,
    ): String = "${packId ?: BUILTIN_ICON_PACK_ID}|$packageName@$sizePx"

    /** 磁盘缓存文件，文件名带图标包标识。 */
    private fun diskCacheFile(
        packId: String?,
        packageName: String,
        sizePx: Int,
    ): File {
        val prefix = packId?.let { "pack_${safeName(it)}" } ?: BUILTIN_ICON_PACK_ID
        return File(iconDir, "${prefix}_${safeName(packageName)}-$sizePx.png")
    }

    /** 把图标写入内存缓存与磁盘缓存，磁盘写入失败不影响返回结果。 */
    private fun cacheAndPersist(
        key: String,
        file: File,
        bitmap: Bitmap,
    ) {
        memoryCache.put(key, bitmap)
        runCatching {
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    private companion object {
        /** 内置线稿图标包的标识。 */
        const val BUILTIN_ICON_PACK_ID = "builtin"
    }
}
