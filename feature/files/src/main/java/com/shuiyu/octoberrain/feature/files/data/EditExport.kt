package com.shuiyu.octoberrain.feature.files.data

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.ImageSizing
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** 编辑结果的输出目录名，位于公共图片目录下。 */
const val EXPORT_DIRECTORY = "十雨桌面"

/** 低内存设备的内存阈值，单位 MB。 */
private const val LOW_MEMORY_MB = 192

/** 低内存设备的导出长边。 */
private const val LOW_EDGE = 1600

/** 常规设备的导出长边。 */
private const val HIGH_EDGE = 2400

/** 导出与覆盖写入共用的压缩质量；PNG 忽略该值，JPEG 与有损 WebP 按此质量编码。 */
private const val EXPORT_QUALITY = 100

/** 导出文件的 MIME 类型。 */
private const val MIME_PNG = "image/png"

/** JPEG 覆盖写入后向媒体库登记的 MIME 类型。 */
private const val MIME_JPEG = "image/jpeg"

/** WebP 覆盖写入后向媒体库登记的 MIME 类型。 */
private const val MIME_WEBP = "image/webp"

/** 文件名中的编辑标记。 */
private const val EDIT_MARK = "-编-"

/** 文件名时间戳格式。 */
private const val STAMP_PATTERN = "yyyyMMdd-HHmmss"

/** 导出结果。 */
sealed interface ExportResult {
    /**
     * 导出成功。
     *
     * 参数 displayName 为最终文件名，path 为文件路径或 MediaStore 地址。
     */
    data class Success(
        val displayName: String,
        val path: String,
    ) : ExportResult

    /**
     * 导出失败。
     *
     * 参数 message 为面向用户的中文说明。
     */
    data class Failure(
        val message: String,
    ) : ExportResult
}

/**
 * 编辑结果导出。
 *
 * 纯计算部分（长边选择、文件名与重名处理）可独立测试；写入部分优先使用 MediaStore 的
 * `Pictures/十雨桌面` 相对路径，Android 10 以下或写入失败时回退到公共目录文件写入后扫描。
 * 解码与堆预算判断复用 [ImageDecoder] 与 [ImageSizing]，不另建尺寸决策。
 */
object EditExport {
    /** 低内存设备的内存阈值，MB。 */
    const val MEMORY_THRESHOLD_MB = LOW_MEMORY_MB

    /** 低内存设备的导出长边。 */
    const val TARGET_EDGE_LOW = LOW_EDGE

    /** 常规设备的导出长边。 */
    const val TARGET_EDGE_HIGH = HIGH_EDGE

    private val stampFormat = ThreadLocal.withInitial { SimpleDateFormat(STAMP_PATTERN, Locale.US) }

    /**
     * 按设备内存选择导出长边。
     *
     * 参数 memoryClassMb 为 `ActivityManager.getMemoryClass()` 的返回值；
     * 低于 192MB 取 1600，其余取 2400。
     */
    fun targetEdge(memoryClassMb: Int): Int = if (memoryClassMb < MEMORY_THRESHOLD_MB) LOW_EDGE else HIGH_EDGE

    /**
     * 生成导出文件名。
     *
     * 参数 sourceName 为源文件名（不含扩展名）；timestamp 为毫秒时间；
     * timeZone 参与时间戳格式化，默认取系统时区。结果为「原名-编-时间戳.png」。
     */
    fun displayName(
        sourceName: String,
        timestamp: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String = sourceName + EDIT_MARK + stamp(timestamp, timeZone) + ".png"

    /**
     * 把毫秒时间格式化为 `yyyyMMdd-HHmmss`。
     *
     * 参数 timeZone 用于固定时区，便于测试与跨时区结果一致。
     */
    fun stamp(
        timestamp: Long,
        timeZone: TimeZone,
    ): String {
        val format = stampFormat.get() ?: SimpleDateFormat(STAMP_PATTERN, Locale.US)
        format.timeZone = timeZone
        return format.format(timestamp)
    }

    /**
     * 在目标目录内生成不与现有文件冲突的导出文件名。
     *
     * 参数 dir 为目标目录，允许为 null；其余参数语义同 [displayName]。
     * 冲突时沿用 [FileOps.uniqueName] 的 `_2` 至 `_999` 追加规则。
     */
    fun uniqueName(
        dir: File?,
        sourceName: String,
        timestamp: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val preferred = displayName(sourceName, timestamp, timeZone)
        return FileOps.uniqueName(dir, preferred) ?: preferred
    }

    /**
     * 解码并重放编辑操作，写入公共图片目录。
     *
     * 参数 context 为任意上下文；sourcePath 为源图片绝对路径；ops 为编辑操作；
     * timestamp 为输出文件名使用的时间。返回成功文件名与路径，或失败说明。
     * 方法同步执行，读盘、解码与运算耗时明显，调用方应在 IO 线程调用。
     */
    @Suppress("ReturnCount")
    fun export(
        context: Context,
        sourcePath: String,
        ops: List<EditOp>,
        timestamp: Long = System.currentTimeMillis(),
    ): ExportResult {
        val source = File(sourcePath)
        if (!source.isFile) return ExportResult.Failure("源图片不存在")
        val rendered = render(context, sourcePath, ops)
        if (rendered is RenderResult.Failure) return ExportResult.Failure(rendered.message)
        val edited = (rendered as RenderResult.Success).bitmap
        return try {
            val directory = StorageAccess(context).editOutputRoot()
            val name = uniqueName(directory, source.nameWithoutExtension, timestamp)
            val path = writeBitmap(context, directory, name, edited)
            if (path == null) ExportResult.Failure("写入图片失败") else ExportResult.Success(name, path)
        } finally {
            edited.recycle()
        }
    }

    /**
     * 解码并重放编辑操作，用结果覆盖源文件。
     *
     * 参数 context 为任意上下文；sourcePath 为源图片路径；ops 为编辑操作。返回成功时
     * displayName 为源文件名、path 为源文件绝对路径，失败时返回面向用户的中文说明。
     * 编码格式由源文件扩展名决定，扩展名与文件名保持不变。写入先落到源文件同目录的隐藏
     * 临时文件，再原子替换源文件；任何一步失败都会删除临时文件并保留源文件原内容，
     * 调用方据此提示改用另存。方法同步执行，应在 IO 线程调用。
     */
    @Suppress("ReturnCount")
    fun overwrite(
        context: Context,
        sourcePath: String,
        ops: List<EditOp>,
    ): ExportResult {
        val source = File(sourcePath)
        if (!source.isFile) return ExportResult.Failure("源图片不存在")
        val plan =
            planOverwrite(source)
                ?: return ExportResult.Failure(overwriteBlockReason(source) ?: "源文件不可写")
        val rendered = render(context, sourcePath, ops)
        if (rendered is RenderResult.Failure) return ExportResult.Failure(rendered.message)
        val edited = (rendered as RenderResult.Success).bitmap
        return try {
            val result = writeOverwrite(plan, { temp -> writeBitmapToFile(edited, temp, plan.encoding, plan.quality) })
            result.also {
                if (result is ExportResult.Success) syncMediaStore(context, source, plan.encoding)
            }
        } finally {
            edited.recycle()
        }
    }

    /**
     * 解码源图并重放编辑操作。
     *
     * 参数 context 用于读取设备内存档位；sourcePath 为源图片路径；ops 为编辑操作。
     * 返回成功时位图由调用方写入后回收，失败时返回带中文原因的结果。
     */
    @Suppress("ReturnCount")
    private fun render(
        context: Context,
        sourcePath: String,
        ops: List<EditOp>,
    ): RenderResult {
        val bounds = ImageDecoder.size(sourcePath) ?: return RenderResult.Failure("无法读取图片尺寸")
        if (!ImageSizing.withinLimit(bounds[0], bounds[1])) {
            return RenderResult.Failure("图片尺寸超出处理上限")
        }
        val edge = targetEdge(memoryClassOf(context))
        val sample = ImageSizing.sampleFor(maxOf(bounds[0], bounds[1]), edge)
        val decoded =
            ImageDecoder.decodeWithSample(sourcePath, sample)
                ?: return RenderResult.Failure("图片解码失败")
        // 本机尺寸操作自带目标像素，预先按长边缩放没有收益，直接交给重放阶段处理
        val scaled = if (ops.any { it is EditOp.Frame }) decoded else scaleToEdge(decoded, edge)
        val edited = applyOps(scaled, ops)
        // 逐层回收中间位图，最终结果由调用方写入后回收
        for (bitmap in listOf(decoded, scaled)) {
            if (bitmap !== edited) bitmap.recycle()
        }
        return RenderResult.Success(edited)
    }

    /** 覆盖后通知媒体库重新读取源文件，使已登记的媒体条目返回新内容；MIME 类型随编码格式给出。 */
    private fun syncMediaStore(
        context: Context,
        target: File,
        encoding: OverwriteEncoding,
    ) {
        val mime =
            when (encoding) {
                OverwriteEncoding.PNG -> MIME_PNG
                OverwriteEncoding.JPEG -> MIME_JPEG
                OverwriteEncoding.WEBP -> MIME_WEBP
            }
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        }
    }

    /** 读取设备可用内存等级，读取失败按低内存处理，保证导出尺寸保守。 */
    private fun memoryClassOf(context: Context): Int =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.memoryClass
            ?: MEMORY_THRESHOLD_MB
}

/** 把位图长边缩放到目标值以内，未超出时返回原位图。 */
private fun scaleToEdge(
    bitmap: Bitmap,
    edge: Int,
): Bitmap {
    val longEdge = maxOf(bitmap.width, bitmap.height)
    if (longEdge <= edge) return bitmap
    val ratio = edge.toFloat() / longEdge
    val width = maxOf(1, (bitmap.width * ratio).roundToInt())
    val height = maxOf(1, (bitmap.height * ratio).roundToInt())
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

/** 写入位图，优先 MediaStore，失败时回退到文件写入并扫描；成功返回路径。 */
private fun writeBitmap(
    context: Context,
    directory: File,
    name: String,
    bitmap: Bitmap,
): String? {
    val pending =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, name, bitmap)
        } else {
            null
        }
    if (pending != null) return pending
    return writeViaFile(context, directory, name, bitmap)
}

/** 通过 MediaStore 写入公共图片目录，成功返回内容地址。 */
private fun writeViaMediaStore(
    context: Context,
    name: String,
    bitmap: Bitmap,
): String? {
    val resolver = context.contentResolver
    val values =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_PNG)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + EXPORT_DIRECTORY)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    val uri =
        runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null
    val written =
        runCatching {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, EXPORT_QUALITY, stream)
            }
        }.getOrNull() == true
    return if (written) {
        runCatching {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri.toString()
    } else {
        runCatching { resolver.delete(uri, null, null) }
        null
    }
}

/** 直接写入公共图片目录并通知媒体扫描，成功返回文件路径。 */
private fun writeViaFile(
    context: Context,
    directory: File,
    name: String,
    bitmap: Bitmap,
): String? {
    if (!ensureDirectory(directory)) return null
    val target = File(directory, name)
    return if (writeBitmapToFile(bitmap, target, OverwriteEncoding.PNG, EXPORT_QUALITY)) {
        runCatching { MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(MIME_PNG), null) }
        target.absolutePath
    } else {
        target.delete()
        null
    }
}

/**
 * 按指定编码把位图写入文件，成功返回真。
 *
 * 参数 bitmap 为待写入位图；target 为目标文件；encoding 为压缩格式，JPEG 无透明通道，
 * 位图带透明度时先铺纸色底；quality 为压缩质量。写入失败返回假，由调用方清理残留文件。
 */
private fun writeBitmapToFile(
    bitmap: Bitmap,
    target: File,
    encoding: OverwriteEncoding,
    quality: Int,
): Boolean =
    runCatching {
        // JPEG 无透明通道，带透明度的位图先铺纸色底，避免出现黑底
        val source =
            if (encoding == OverwriteEncoding.JPEG && bitmap.hasAlpha()) {
                Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { flattened ->
                    Canvas(flattened).apply {
                        drawColor(InkColors.PAPER)
                        drawBitmap(bitmap, 0f, 0f, null)
                    }
                }
            } else {
                bitmap
            }
        try {
            val format =
                when (encoding) {
                    OverwriteEncoding.PNG -> Bitmap.CompressFormat.PNG
                    OverwriteEncoding.JPEG -> Bitmap.CompressFormat.JPEG
                    // 有损 WebP 自 Android 10 起提供，旧版本不会选出该编码
                    OverwriteEncoding.WEBP ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            Bitmap.CompressFormat.WEBP
                        }
                }
            FileOutputStream(target).use { stream -> source.compress(format, quality, stream) }
        } finally {
            if (source !== bitmap) source.recycle()
        }
    }.getOrDefault(false)

/** 确保目录存在，目录已存在或创建成功时返回真。 */
private fun ensureDirectory(directory: File): Boolean = directory.exists() || directory.mkdirs() || directory.exists()

/** 解码与重放结果；成功时位图由调用方负责写入并回收。 */
private sealed interface RenderResult {
    /** 成功生成的编辑结果。 */
    class Success(
        /** 编辑后的位图，调用方负责回收。 */
        val bitmap: Bitmap,
    ) : RenderResult

    /** 失败原因，面向用户显示。 */
    class Failure(
        /** 中文失败说明。 */
        val message: String,
    ) : RenderResult
}

/** 覆盖写入使用的编码格式，由源文件扩展名决定。 */
internal enum class OverwriteEncoding {
    /** PNG 无损编码，保留透明度。 */
    PNG,

    /** JPEG 有损编码，无透明通道，写入前需铺纸色底。 */
    JPEG,

    /** 有损 WebP 编码，仅 Android 10 及以上可选。 */
    WEBP,
}

/**
 * 覆盖写入计划。
 *
 * 参数 target 为被覆盖的源文件，encoding 为按源文件扩展名选定的编码格式，quality 为压缩质量。
 * 计划只做校验，不创建或删除文件。
 */
internal data class OverwritePlan(
    val target: File,
    val encoding: OverwriteEncoding,
    val quality: Int,
)

/**
 * 按源文件名选择覆盖写入使用的编码格式。
 *
 * 参数 fileName 为源文件名，扩展名按忽略大小写匹配；sdkInt 为系统接口级别，默认取
 * [Build.VERSION.SDK_INT]。扩展名为 jpg 或 jpeg 时返回 [OverwriteEncoding.JPEG]，
 * png 返回 [OverwriteEncoding.PNG]，webp 在 Android 10 及以上返回
 * [OverwriteEncoding.WEBP]；其余扩展名与无扩展名返回空，表示该格式不支持覆盖。
 */
internal fun overwriteEncodingFor(
    fileName: String,
    sdkInt: Int = Build.VERSION.SDK_INT,
): OverwriteEncoding? =
    when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
        "jpg", "jpeg" -> OverwriteEncoding.JPEG
        "png" -> OverwriteEncoding.PNG
        "webp" -> if (sdkInt >= Build.VERSION_CODES.R) OverwriteEncoding.WEBP else null
        else -> null
    }

/**
 * 判断覆盖写入被拒绝的原因。
 *
 * 参数 source 为待覆盖的源文件；sdkInt 为系统接口级别，用于判断 WebP 编码是否可用。
 * 可执行覆盖时返回空，否则返回面向用户的中文原因；格式不受支持时提示该格式不支持覆盖。
 */
@Suppress("ReturnCount")
private fun overwriteBlockReason(
    source: File,
    sdkInt: Int = Build.VERSION.SDK_INT,
): String? {
    if (!source.isFile || !source.canWrite()) return "源文件不可写"
    val directory = source.parentFile ?: return "源文件不可写"
    if (!directory.isDirectory || !directory.canWrite()) return "源文件不可写"
    if (overwriteEncodingFor(source.name, sdkInt) == null) return "该格式不支持覆盖"
    return null
}

/**
 * 构造覆盖写入计划。
 *
 * 参数 source 为待覆盖的源文件；sdkInt 为系统接口级别，默认取 [Build.VERSION.SDK_INT]。
 * 源文件非普通文件、不可写、所在目录不可写或扩展名不受支持时返回空，
 * 调用方据此提示改用另存。计划阶段不触及临时文件。
 */
@Suppress("ReturnCount")
internal fun planOverwrite(
    source: File,
    sdkInt: Int = Build.VERSION.SDK_INT,
): OverwritePlan? {
    if (overwriteBlockReason(source, sdkInt) != null) return null
    val encoding = overwriteEncodingFor(source.name, sdkInt) ?: return null
    return OverwritePlan(source, encoding, EXPORT_QUALITY)
}
