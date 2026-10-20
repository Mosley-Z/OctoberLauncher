package com.shuiyu.octoberrain.core.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Xml
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.render.ImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * 第三方图标包解析器。
 *
 * 当前启用的图标包标识取自外观配置 `AppearanceConfig.iconPackId`，该值由设置页写入。
 * 图标包是普通安卓应用，内部以 ADW 格式的 `appfilter.xml` 声明包名与图标资源的对应关系；
 * 本类负责读取映射、按包名匹配图标并转换为目标尺寸的位图。
 *
 * 实例化时不访问全局服务，所有依赖配置的读取都发生在方法调用时，
 * 因此可以在应用服务装配早期构造。
 */
class IconPackResolver(
    private val context: Context,
) {
    /** 应用自身的包管理器，用于读取图标包的安装信息与目标应用的启动组件。 */
    private val packageManager: PackageManager = context.packageManager

    /** 保护缓存字段，图标读取可能在工作线程并发进行。 */
    private val lock = Any()

    /** 当前缓存对应的图标包标识，切换图标包后整体失效。 */
    private var cachedPackId: String? = null

    /** 图标包资源缓存，避免每次匹配都创建包上下文。 */
    private var cachedPack: PackContext? = null

    /** 解析后的 `appfilter.xml` 索引，按图标包标识缓存。 */
    private var cachedIndex: PackIndex? = null

    /** 目标应用启动活动缓存，空字符串表示该应用没有启动活动。 */
    private val launchActivities = ConcurrentHashMap<String, String>()

    /**
     * 判断当前图标包是否可用。
     *
     * 判定条件为：配置中存在非内置的图标包标识、该应用已安装、包内存在可读取的
     * `appfilter.xml`。完整解析推迟到首次取图标时进行，避免在主线程展开大文件。
     */
    fun isAvailable(): Boolean {
        val pack = packContext() ?: return false
        return runCatching {
            val resourceId = pack.resources.getIdentifier(APP_FILTER_NAME, "xml", pack.packageName)
            resourceId != 0 || appFilterAssetExists(pack)
        }.onFailure { InkLog.w("图标包", "检查 appfilter.xml 失败", it) }
            .getOrDefault(false)
    }

    /** 返回当前图标包的显示名，图标包不可用时返回 null。 */
    fun packLabel(): String? = packContext()?.label

    /**
     * 按包名读取图标并缩放到指定尺寸。
     *
     * 匹配顺序为：`ComponentName` 精确匹配、包名匹配；图标资源先查图标包的
     * drawable 与 mipmap，再查 assets 下的同名 png/webp 文件。任一环节未命中返回 null，
     * 由调用方回退到系统图标。
     *
     * 参数 packageName 为目标应用包名；sizePx 为输出位图边长（像素）；
     * 返回值在成功时为 `sizePx × sizePx` 的透明背景位图。
     */
    suspend fun iconFor(
        packageName: String,
        sizePx: Int,
    ): Bitmap? {
        if (packageName.isBlank() || sizePx <= 0) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val pack = packContext() ?: return@runCatching null
                val index = indexFor(pack) ?: return@runCatching null
                val activity = launchActivityOf(packageName)
                val drawableName = index.drawableFor(packageName, activity) ?: return@runCatching null
                val drawable = loadDrawable(pack, drawableName) ?: return@runCatching null
                drawableToBitmap(drawable, sizePx)
            }.onFailure { InkLog.w("图标包", "读取 $packageName 的图标失败", it) }
                .getOrNull()
        }
    }

    /** 读取图标包上下文、资源与显示名，结果按图标包标识缓存。 */
    private fun packContext(): PackContext? {
        val packId = currentPackId() ?: return null
        synchronized(lock) {
            val cached = cachedPack
            if (cached != null && cachedPackId == packId) return cached
            return runCatching {
                val packageContext =
                    context.createPackageContext(
                        packId,
                        Context.CONTEXT_IGNORE_SECURITY,
                    )
                val appInfo = packageManager.getApplicationInfo(packId, 0)
                val label = appInfo.loadLabel(packageManager).toString()
                val pack =
                    PackContext(
                        packageName = packId,
                        resources = packageContext.resources,
                        assets = packageContext.assets,
                        label = label,
                    )
                cachedPackId = packId
                cachedPack = pack
                cachedIndex = null
                launchActivities.clear()
                pack
            }.onFailure { InkLog.w("图标包", "图标包 $packId 不可用", it) }
                .getOrNull()
        }
    }

    /** 读取当前配置中的图标包标识。配置服务尚未就绪时按未启用处理。 */
    private fun currentPackId(): String? =
        runCatching {
            normalizePackId(AppServices.config.state.value.appearance.iconPackId)
        }.getOrNull()

    /** 解析 `appfilter.xml`，结果按图标包缓存。 */
    private fun indexFor(pack: PackContext): PackIndex? {
        synchronized(lock) {
            cachedIndex?.let { return it }
            val items = readAppFilter(pack)
            if (items.isEmpty()) return null
            val index = buildPackIndex(items.asSequence())
            cachedIndex = index
            return index
        }
    }

    /** 按资源优先、assets 兜底的顺序打开 `appfilter.xml`。 */
    private fun openAppFilter(pack: PackContext): java.io.InputStream? {
        val resourceId = pack.resources.getIdentifier(APP_FILTER_NAME, "xml", pack.packageName)
        if (resourceId != 0) {
            val resourceStream =
                runCatching {
                    pack.resources.openRawResource(resourceId)
                }.onFailure { InkLog.w("图标包", "读取资源 appfilter.xml 失败", it) }.getOrNull()
            if (resourceStream != null) return resourceStream
        }
        return runCatching { pack.assets.open("$APP_FILTER_NAME.xml") }
            .onFailure { InkLog.w("图标包", "读取 assets/appfilter.xml 失败", it) }
            .getOrNull()
    }

    /** 判断图标包 assets 内是否存在 `appfilter.xml`。 */
    private fun appFilterAssetExists(pack: PackContext): Boolean =
        runCatching {
            pack.assets.open("$APP_FILTER_NAME.xml").close()
            true
        }.getOrDefault(false)

    /** 用 XmlPullParser 读取全部 item 的 component 与 drawable 属性。 */
    private fun readAppFilter(pack: PackContext): List<Pair<String?, String?>> {
        val input = openAppFilter(pack) ?: return emptyList()
        return runCatching {
            input.use { stream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, null)
                val items = ArrayList<Pair<String?, String?>>()
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "item") {
                        items.add(
                            parser.getAttributeValue(null, "component") to
                                parser.getAttributeValue(null, "drawable"),
                        )
                    }
                    event = parser.next()
                }
                items
            }
        }.onFailure { InkLog.w("图标包", "解析 appfilter.xml 失败", it) }
            .getOrDefault(emptyList())
    }

    /** 查询目标应用的启动活动名，结果缓存；没有启动活动时返回 null。 */
    private fun launchActivityOf(packageName: String): String? {
        launchActivities[packageName]?.let { return it.ifEmpty { null } }
        val component =
            runCatching {
                packageManager.getLaunchIntentForPackage(packageName)?.component
            }.getOrNull()
        val activity = component?.className ?: ""
        launchActivities[packageName] = activity
        return activity.ifEmpty { null }
    }

    /** 先按 drawable、mipmap 类型查资源，再按同名图片查 assets。 */
    private fun loadDrawable(
        pack: PackContext,
        drawableName: String,
    ): Drawable? {
        for (type in RESOURCE_TYPES) {
            val resourceId = pack.resources.getIdentifier(drawableName, type, pack.packageName)
            if (resourceId == 0) continue
            val drawable = runCatching { pack.resources.getDrawable(resourceId, null) }.getOrNull()
            if (drawable != null) return drawable
        }
        for (asset in assetCandidates(drawableName)) {
            // 图标包内的图片资源通常不超过 256 像素，按该长边解码并保留透明边缘
            val bitmap =
                runCatching {
                    pack.assets.open(asset).use { input ->
                        ImageDecoder.decodeStream(input, ICON_ASSET_EDGE, keepAlpha = true)
                    }
                }.getOrNull() ?: continue
            return BitmapDrawable(pack.resources, bitmap)
        }
        return null
    }

    /** 把图标 Drawable 等比缩放并居中绘制到方形透明位图。 */
    private fun drawableToBitmap(
        drawable: Drawable,
        sizePx: Int,
    ): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        if (intrinsicWidth > 0 && intrinsicHeight > 0) {
            val scale =
                minOf(
                    sizePx.toFloat() / intrinsicWidth,
                    sizePx.toFloat() / intrinsicHeight,
                )
            val width = (intrinsicWidth * scale).roundToInt().coerceIn(1, sizePx)
            val height = (intrinsicHeight * scale).roundToInt().coerceIn(1, sizePx)
            val left = (sizePx - width) / 2
            val top = (sizePx - height) / 2
            drawable.setBounds(left, top, left + width, top + height)
        } else {
            drawable.setBounds(0, 0, sizePx, sizePx)
        }
        runCatching { drawable.draw(canvas) }
            .onFailure { InkLog.w("图标包", "绘制图标失败", it) }
        return output
    }

    /** 图标包上下文与资源快照。 */
    private data class PackContext(
        val packageName: String,
        val resources: Resources,
        val assets: AssetManager,
        val label: String,
    )

    private companion object {
        /** 图标包描述文件的基础名，资源与 assets 两种来源共用。 */
        const val APP_FILTER_NAME = "appfilter"

        /** 资源查找顺序：先 drawable 后 mipmap。 */
        val RESOURCE_TYPES = arrayOf("drawable", "mipmap")

        /** assets 图标解码的目标长边，图标包内图片普遍不超过该尺寸。 */
        const val ICON_ASSET_EDGE = 256
    }
}

/**
 * `appfilter.xml` 解析结果。
 *
 * exact 以 `包名/活动名` 为键，byPackage 以包名为键；两者都保留首次出现的资源名，
 * 与 ADW 图标包的覆盖规则一致。
 */
internal class PackIndex(
    val exact: Map<String, String>,
    val byPackage: Map<String, String>,
) {
    /**
     * 按包名与启动活动解析图标资源名。
     *
     * 优先使用精确的 `包名/活动名` 映射，未命中时回退到包名映射；
     * 两者都未命中返回 null，由调用方回退到系统图标。
     */
    fun drawableFor(
        packageName: String,
        launchActivity: String?,
    ): String? {
        if (!launchActivity.isNullOrEmpty()) {
            exact["$packageName/$launchActivity"]?.let { return it }
        }
        return byPackage[packageName]
    }
}

/** 图标组件声明，activityName 为空表示整个包共用同一图标。 */
internal data class ComponentRef(
    val packageName: String,
    val activityName: String?,
)

/**
 * 把图标包标识归一化。
 *
 * 空白与内置标识 `builtin` 都表示未启用第三方图标包，统一返回 null。
 */
internal fun normalizePackId(raw: String?): String? {
    val id = raw?.trim().orEmpty()
    if (id.isEmpty() || id == BUILTIN_PACK_ID) return null
    return id
}

/**
 * 解析 `appfilter.xml` 的 component 属性。
 *
 * 兼容 `ComponentInfo{包名/活动名}`、`ComponentInfo{包名}`、`包名/活动名`、`包名`
 * 四种写法；无法解析时返回 null。
 */
internal fun parseComponentRef(raw: String?): ComponentRef? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val body =
        when {
            text.startsWith("ComponentInfo{") -> text.removePrefix("ComponentInfo{").substringBefore("}")
            text.startsWith("ComponentInfo") -> {
                text
                    .removePrefix("ComponentInfo")
                    .trim()
                    .removePrefix("{")
                    .substringBefore("}")
            }
            else -> text
        }.trim()
    if (body.isEmpty()) return null
    val packageName = body.substringBefore("/").trim()
    if (packageName.isEmpty()) return null
    val activityName = body.substringAfter("/", "").trim().ifEmpty { null }
    return ComponentRef(packageName, activityName)
}

/**
 * 把 component 与 drawable 属性序列构建为查找索引。
 *
 * 同一键重复出现时保留首条记录；缺少 component 或 drawable 的条目直接忽略。
 */
internal fun buildPackIndex(items: Sequence<Pair<String?, String?>>): PackIndex {
    val exact = LinkedHashMap<String, String>()
    val byPackage = LinkedHashMap<String, String>()
    for ((rawComponent, rawDrawable) in items) {
        val drawableName = rawDrawable?.trim().orEmpty()
        if (drawableName.isEmpty()) continue
        val ref = parseComponentRef(rawComponent) ?: continue
        val activityName = ref.activityName
        if (activityName != null) {
            val key = "${ref.packageName}/$activityName"
            if (!exact.containsKey(key)) exact[key] = drawableName
        }
        if (!byPackage.containsKey(ref.packageName)) {
            byPackage[ref.packageName] = drawableName
        }
    }
    return PackIndex(exact, byPackage)
}

/**
 * 生成 assets 内的图标候选文件名。
 *
 * 依次尝试图标包根目录与 `icons/` 子目录下的 png、webp 文件。
 */
internal fun assetCandidates(drawable: String): List<String> {
    val name = drawable.trim()
    if (name.isEmpty()) return emptyList()
    return listOf(
        "$name.png",
        "$name.webp",
        "icons/$name.png",
        "icons/$name.webp",
    )
}

/** 内置图标包的标识，与外观配置的默认值保持一致。 */
private const val BUILTIN_PACK_ID = "builtin"
