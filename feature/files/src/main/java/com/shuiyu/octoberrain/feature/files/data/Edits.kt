package com.shuiyu.octoberrain.feature.files.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 旋转另存使用的解码长边上限，与移植决策第六节一致。 */
private const val DECODE_LONG_EDGE = 1600

/** 输出文件名中的编辑标记，位于原文件名与时间戳之间。 */
internal const val EDIT_NAME_TAG = "编"

/** 输出文件的扩展名，统一写 PNG 以避免重复压缩损失。 */
internal const val EDIT_OUTPUT_EXTENSION = ".png"

/** 旋转计数在偏好文件中的键前缀，键名由前缀与绝对路径拼接。 */
internal const val ROTATION_KEY_PREFIX = "edited_rotations:"

private const val TAG = "图片编辑"

/**
 * 图片旋转另存的唯一入口。
 *
 * 旋转结果写入新文件，源文件保持不变；连续旋转四次回到原始方向，计数按文件路径记录在
 * [FilePrefs.FILE_NAME] 偏好文件中，与编辑会话的持久化键保持一致。
 *
 * 输出目录规则：源文件位于应用缓存目录（外部入口复制得到）时写入
 * [StorageAccess.editOutputRoot]，其余写入源文件所在目录。判断集中在 [targetDirectory]，
 * 调用方不各自推断。
 *
 * 全部文件写入在调用线程完成，调用方应置于 IO 线程；失败返回 null 并删除残缺文件。
 */
object Edits {
    /**
     * 按当前累计次数把图片顺时针旋转 90 度并另存。
     *
     * @param context 任意上下文，内部使用应用上下文访问缓存目录与偏好。
     * @param path 源图片绝对路径。
     * @return 新文件；解码失败、写入失败或线程被中断时返回 null。
     */
    fun rotate90(
        context: Context,
        path: String,
    ): File? {
        val appContext = context.applicationContext ?: context
        val source = ImageDecoder.decode(path, DECODE_LONG_EDGE) ?: return null
        return runCatching { writeRotated(appContext, File(path), source) }
            .onFailure { InkLog.w(TAG, "旋转另存失败：${it.message}") }
            .getOrNull()
            .also { source.recycle() }
    }

    /**
     * 读取某个源文件已记录的旋转次数。
     *
     * @param context 任意上下文。
     * @param path 源图片绝对路径。
     * @return 累计次数，取值 0 至 3；读取失败按 0 处理。
     */
    fun nextRotation(
        context: Context,
        path: String,
    ): Int {
        val appContext = context.applicationContext ?: context
        return runCatching { rotation(appContext, path) }.getOrDefault(0)
    }

    /**
     * 计算下一次旋转的角度。
     *
     * @param count 已完成的旋转次数。
     * @return 本次应旋转的角度，取值 0、90、180、270；次数可超出 0 至 3。
     */
    internal fun nextAngle(count: Int): Int = ((count.coerceAtLeast(0) % 4) + 1) % 4 * 90

    /**
     * 生成输出文件名。
     *
     * 格式为「原名-编-yyyyMMdd-HHmmss.png」，原名去掉扩展名后沿用。
     *
     * @param sourceName 源文件名，可含扩展名。
     * @param timestamp 输出时刻，毫秒。
     * @return 输出文件名。
     */
    internal fun outputName(
        sourceName: String,
        timestamp: Long,
    ): String {
        val stem = sourceName.substringBeforeLast('.', sourceName)
        val base = stem.ifBlank { "image" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestamp))
        return "$base-$EDIT_NAME_TAG-$stamp$EDIT_OUTPUT_EXTENSION"
    }

    /**
     * 判定输出目录。
     *
     * @param source 源文件。
     * @param cacheDir 应用缓存目录，外部入口的复制件位于该目录之下。
     * @param editRoot 编辑结果目录，缓存来源写入此处。
     * @return 输出目录；源文件无父目录时返回 editRoot。
     */
    internal fun targetDirectory(
        source: File,
        cacheDir: File,
        editRoot: File,
    ): File {
        val insideCache =
            runCatching {
                source.canonicalPath.startsWith(cacheDir.canonicalPath + File.separatorChar)
            }.getOrDefault(false)
        return if (insideCache) editRoot else (source.parentFile ?: editRoot)
    }

    private fun writeRotated(
        context: Context,
        sourceFile: File,
        source: Bitmap,
    ): File? {
        if (Thread.currentThread().isInterrupted) return null
        val angle = nextAngle(rotation(context, sourceFile.absolutePath))
        val rotated =
            if (angle == 0) {
                source
            } else {
                val matrix = Matrix().apply { postRotate(angle.toFloat()) }
                Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            }
        return try {
            val target = nextTarget(context, sourceFile)
            target.parentFile?.mkdirs()
            if (writePng(rotated, target)) {
                recordRotation(context, sourceFile.absolutePath)
                ThumbCacheRegistry.invalidate(context, target)
                RefreshScheduler.notifyContentChanged()
                target
            } else {
                target.delete()
                null
            }
        } finally {
            if (rotated !== source) rotated.recycle()
        }
    }

    private fun nextTarget(
        context: Context,
        sourceFile: File,
    ): File {
        val directory =
            targetDirectory(
                source = sourceFile,
                cacheDir = context.cacheDir,
                editRoot = StorageAccess(context).editOutputRoot(),
            )
        directory.mkdirs()
        val name = outputName(sourceFile.name, System.currentTimeMillis())
        val unique = FileOps.uniqueName(directory, name) ?: name
        return File(directory, unique)
    }

    private fun writePng(
        bitmap: Bitmap,
        target: File,
    ): Boolean =
        runCatching {
            FileOutputStream(target).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }.getOrDefault(false)

    private fun rotation(
        context: Context,
        path: String,
    ): Int {
        val preferences = context.getSharedPreferences(FilePrefs.FILE_NAME, Context.MODE_PRIVATE)
        return preferences.getInt(ROTATION_KEY_PREFIX + path, 0)
    }

    private fun recordRotation(
        context: Context,
        path: String,
    ) {
        val count = rotation(context, path)
        val preferences = context.getSharedPreferences(FilePrefs.FILE_NAME, Context.MODE_PRIVATE)
        preferences
            .edit()
            .putInt(ROTATION_KEY_PREFIX + path, (count + 1) % 4)
            .apply()
    }
}
