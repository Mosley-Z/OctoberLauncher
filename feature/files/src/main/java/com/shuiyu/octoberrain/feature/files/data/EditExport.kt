package com.shuiyu.octoberrain.feature.files.data

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

/** 导出文件的 MIME 类型。 */
private const val MIME_PNG = "image/png"

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
        val bounds = ImageDecoder.size(sourcePath) ?: return ExportResult.Failure("无法读取图片尺寸")
        if (!ImageSizing.withinLimit(bounds[0], bounds[1])) return ExportResult.Failure("图片尺寸超出处理上限")
        val edge = targetEdge(memoryClassOf(context))
        val sample = ImageSizing.sampleFor(maxOf(bounds[0], bounds[1]), edge / 2)
        val decoded = ImageDecoder.decodeWithSample(sourcePath, sample) ?: return ExportResult.Failure("图片解码失败")
        // 本机尺寸操作自带目标像素，预先按长边缩放没有收益，直接交给重放阶段处理
        val scaled = if (ops.any { it is EditOp.Frame }) decoded else scaleToEdge(decoded, edge)
        val edited = applyOps(scaled, ops)
        val directory = StorageAccess(context).editOutputRoot()
        val name = uniqueName(directory, source.nameWithoutExtension, timestamp)
        val path = writeBitmap(context, directory, name, edited)
        if (edited !== scaled) edited.recycle()
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return if (path == null) ExportResult.Failure("写入图片失败") else ExportResult.Success(name, path)
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
            resolver.openOutputStream(uri)?.use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
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
    val written =
        runCatching {
            FileOutputStream(target).use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        }.getOrDefault(false)
    return if (written) {
        runCatching { MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(MIME_PNG), null) }
        target.absolutePath
    } else {
        target.delete()
        null
    }
}

/** 确保目录存在，目录已存在或创建成功时返回真。 */
private fun ensureDirectory(directory: File): Boolean = directory.exists() || directory.mkdirs() || directory.exists()
