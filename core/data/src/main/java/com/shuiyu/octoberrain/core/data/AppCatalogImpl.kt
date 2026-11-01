package com.shuiyu.octoberrain.core.data

import android.content.ComponentName
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
import com.shuiyu.octoberrain.core.common.IconTargets
import com.shuiyu.octoberrain.core.common.InstalledApp
import com.shuiyu.octoberrain.core.common.Router
import com.shuiyu.octoberrain.core.common.SpecialEntry
import com.shuiyu.octoberrain.core.common.VendorSettingsSession
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

/**
 * 应用目录实现：枚举可启动应用，缓存图标，并提供内置入口。
 *
 * 图标先查内存缓存；未命中时若启用了图标包，优先取图标包中的映射图标；
 * 仍未命中则查磁盘缓存，最后从 PackageManager 读取并按目标尺寸缩放。
 * 内存缓存键与磁盘文件名均带当前图标包标识，切换图标包后不会复用旧图标。
 */
class AppCatalogImpl(
    private val context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : AppCatalog {
    private val packageManager: PackageManager = context.packageManager

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    override val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val memoryCache = LruCache<String, Bitmap>(48)

    /** 应用级名称缓存，供自动成组取文件夹名使用。 */
    private val applicationLabels = HashMap<String, String>()

    private val iconDir = File(context.filesDir, "icons").apply { mkdirs() }

    private val collator = Collator.getInstance(Locale.CHINA)

    /** 上一轮目录快照的入口键集合，用于判定快照是否已稳定。 */
    private var lastSnapshotKeys: Set<String>? = null

    /** 首次目录刷新时刻，用于判定快照是否已越过稳定等待窗口。 */
    private var firstRefreshAtMillis: Long? = null

    /** 旧入口键解析是否已完成，完成后不再重复执行。 */
    private var legacyKeysMigrated: Boolean = false

    override suspend fun refresh() {
        val snapshot = withContext(Dispatchers.IO) { queryLaunchable() }
        _apps.value = snapshot
        // 首轮查询可能因包管理器冷启动返回不完整结果，前几轮彼此一致仍可能同属不完整阶段；
        // 因此除连续两轮一致外，还要求本轮距首次刷新已越过稳定等待窗口
        val now = nowMillis()
        val first = firstRefreshAtMillis ?: now.also { firstRefreshAtMillis = it }
        val keys = catalogKeys(snapshot)
        val migrate =
            !legacyKeysMigrated &&
                shouldMigrateLegacyKeys(
                    previous = lastSnapshotKeys,
                    current = keys,
                    hasConfig = AppServices.hasConfig(),
                    elapsedSinceFirstRefreshMs = now - first,
                )
        lastSnapshotKeys = keys
        // 判据满足后解析旧包级入口键；未装配配置、已完成或写入失败时为空操作
        if (migrate) {
            legacyKeysMigrated =
                runCatching { ComponentKeyResolver.runOnce(AppServices.config, snapshot) }
                    .getOrDefault(false)
        }
    }

    override fun applicationLabel(packageName: String): String? {
        // 名称查询走 PackageManager，命中缓存后不再重复读取
        applicationLabels[packageName]?.let { return it }
        // 包可能已卸载或包管理器拒绝查询，异常按无名称处理，调用方回退组件名称
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager
                .getApplicationLabel(info)
                .toString()
                .trim()
                .takeIf { it.isNotEmpty() }
        }.getOrNull()?.also { applicationLabels[packageName] = it }
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

    override suspend fun loadActivityIcon(
        activityName: String,
        packageName: String,
        sizePx: Int,
    ): Bitmap? {
        // 覆盖图与内存缓存都不区分组件图标来源，命中即返回
        val key = "activity:$packageName/$activityName@$sizePx"
        val cached = componentOverride(activityName, packageName, sizePx) ?: memoryCache.get(key)
        if (cached != null) return cached
        // 组件图标随应用更新变化，只留内存缓存，不与包级磁盘缓存串用；失败回退包级图标
        return withContext(Dispatchers.IO) {
            val loaded = runCatching { drawActivityIcon(packageName, activityName, sizePx) }.getOrNull()
            if (loaded != null) {
                memoryCache.put(key, loaded)
                loaded
            } else {
                loadIcon(packageName, sizePx)
            }
        }
    }

    /**
     * 读取组件级或包级的图标覆盖。
     *
     * 参数 activityName 为启动 Activity 类名，packageName 为包名，sizePx 为目标边长。
     * 未启用覆盖存储时返回空，调用方继续走图标缓存。
     */
    private suspend fun componentOverride(
        activityName: String,
        packageName: String,
        sizePx: Int,
    ): Bitmap? =
        if (!AppServices.hasIconOverrides()) {
            null
        } else {
            AppServices.iconOverrides
                .loadOverride(IconTargets.componentTarget(packageName, activityName), sizePx)
                ?: AppServices.iconOverrides.loadOverride(packageName, sizePx)
        }

    /**
     * 把指定组件的启动图标绘制为位图。
     *
     * 参数 packageName 为包名，activityName 为 Activity 类名，sizePx 为目标边长。
     * 组件不存在时由 PackageManager 抛异常，调用方按图标不可用处理。
     */
    private fun drawActivityIcon(
        packageName: String,
        activityName: String,
        sizePx: Int,
    ): Bitmap {
        val drawable = packageManager.getActivityIcon(ComponentName(packageName, activityName))
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    override suspend fun loadSpecialIcon(
        entry: SpecialEntry,
        sizePx: Int,
    ): Bitmap? {
        // 自定义图标优先于内置品牌图标，恢复默认后再回退品牌图标。
        val override =
            if (AppServices.hasIconOverrides()) {
                AppServices.iconOverrides.loadOverride(IconTargets.specialKey(entry), sizePx)
            } else {
                null
            }
        // 用户指定十雨设置采用品牌图标；使用独立版本键，避免旧齿轮缓存继续命中。
        if (entry == SpecialEntry.SETTINGS) {
            val settingsKey = "launcher-settings-v2@$sizePx"
            return override ?: memoryCache.get(settingsKey) ?: withContext(Dispatchers.Default) {
                com.shuiyu.octoberrain.core.design.LauncherSettingsIcon.bitmap(context, sizePx).also {
                    memoryCache.put(settingsKey, it)
                }
            }
        }
        val key = "special:${entry.name}@$sizePx"
        return override ?: memoryCache.get(key) ?: withContext(Dispatchers.Default) {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val kind =
                when (entry) {
                    SpecialEntry.BOOKSHELF -> InkGlyphs.Kind.BOOK
                    SpecialEntry.FILES -> InkGlyphs.Kind.FOLDER
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

    /**
     * 启动应用。
     *
     * 参数 app 为目标应用条目。返回是否真正发起了启动：目标 Activity 不存在或系统拒绝时返回假，
     * 由界面提示用户；失败时不改用同包其他入口，避免启动目标与点击目标不一致。
     */
    override fun launch(app: InstalledApp): Boolean {
        if (launchVendorSettings(app)) return true
        val intent =
            app.activityName?.let { activity ->
                Intent.makeMainActivity(android.content.ComponentName(app.packageName, activity))
            } ?: packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent(
                Intent.ACTION_MAIN,
            ).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(app.packageName)
            }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** S4真正的设置页由原生桌面承载；保留独立图标和原名，只适配启动目标。 */
    private fun launchVendorSettings(app: InstalledApp): Boolean {
        if (!android.os.Build.MODEL
                .equals("S4", ignoreCase = true) ||
            app.packageName != "com.android.settings"
        ) {
            return false
        }
        return runCatching {
            val component =
                android.content.ComponentName(
                    "com.wetao.elauncher",
                    "com.wetao.elauncher.ui.main.MainActivity",
                )
            val info = packageManager.getActivityInfo(component, 0)
            if (!info.exported || !info.enabled || !info.applicationInfo.enabled) return@runCatching false
            VendorSettingsSession.begin()
            context.startActivity(
                Intent
                    .makeMainActivity(component)
                    .putExtra("fragment", "SettingFragment")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
            true
        }.onFailure { VendorSettingsSession.clear() }.getOrDefault(false)
    }

    override fun launchSpecial(entry: SpecialEntry) {
        val router: Router? = if (AppServices.hasRouter()) AppServices.router else null
        val destination =
            when (entry) {
                SpecialEntry.BOOKSHELF -> Destination.Negative
                SpecialEntry.FILES -> Destination.Files()
                SpecialEntry.SEARCH -> Destination.Search
                SpecialEntry.SETTINGS -> Destination.Settings
                SpecialEntry.WEREAD -> Destination.Weread
            }
        if (router != null) {
            router.navigate(destination)
        }
    }

    /** 查询所有带启动图标的应用，仅排除自身，按中文标签排序。 */
    private fun queryLaunchable(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(intent, 0).toMutableList()
        // 某些设备只通过标准系统设置action公开入口，没有独立LAUNCHER图标。
        packageManager.queryIntentActivities(Intent(android.provider.Settings.ACTION_SETTINGS), 0).forEach { entry ->
            if (entry.activityInfo?.exported == true &&
                resolved.none { it.activityInfo?.packageName == entry.activityInfo.packageName }
            ) {
                resolved.add(entry)
            }
        }
        val result = ArrayList<InstalledApp>(resolved.size)
        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val packageName = activityInfo.packageName ?: continue
            if (packageName == context.packageName) continue
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

/** 把目录快照映射为「包名/Activity」键集合，供快照稳定性比较。 */
internal fun catalogKeys(snapshot: List<InstalledApp>): Set<String> =
    snapshot.mapTo(LinkedHashSet()) { "${it.packageName}/${it.activityName.orEmpty()}" }

/**
 * 判断目录快照是否已稳定。
 *
 * 参数 previous 为上一轮入口键集合，首次刷新时为 null；current 为本轮集合。
 * 本轮集合非空且与上一轮完全一致时返回 true，表示可以据此判定入口键归属。
 */
internal fun isStableCatalog(
    previous: Set<String>?,
    current: Set<String>,
): Boolean = current.isNotEmpty() && current == previous

/** 旧入口键解析要求的最短等待窗口，单位为毫秒。 */
internal const val LEGACY_MIGRATION_SETTLE_MS = 2500L

/**
 * 判定本轮是否应当执行旧包级入口键解析。
 *
 * 参数 previous 为上一轮入口键集合；current 为本轮集合；hasConfig 表示配置仓库已装配；
 * elapsedSinceFirstRefreshMs 为本轮距首次刷新的时长，默认按最短等待窗口比较。
 * 配置已装配、连续两轮一致且已越过等待窗口时才返回 true：前几轮可能同属不完整阶段，
 * 只有等待窗口内的稳定快照才足以判定入口键归属。
 */
internal fun shouldMigrateLegacyKeys(
    previous: Set<String>?,
    current: Set<String>,
    hasConfig: Boolean,
    elapsedSinceFirstRefreshMs: Long,
    settleMs: Long = LEGACY_MIGRATION_SETTLE_MS,
): Boolean =
    hasConfig &&
        isStableCatalog(previous, current) &&
        elapsedSinceFirstRefreshMs >= settleMs
