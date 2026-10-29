package com.shuiyu.octoberrain.service.wallpaper

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 壁纸副本准备：把源图片复制到厂商应用可扫描的公共图片目录。
 *
 * 厂商壁纸应用只从 `Pictures` 目录读取图片，且不接收图片位置参数，因此必须先把图片
 * 落到该目录中。文件统一使用固定前缀，便于在设置前清理上一轮副本；每次生成唯一名称；方向修正、等比铺白后统一输出 JPEG。
 */
internal object WallpaperCopy {
    /** 副本文件名前缀，用于清理历史副本并区别于其他应用。 */
    const val PREFIX = "!!shiyu_wallpaper"

    /** 厂商应用可识别的扩展名集合。 */
    private val EXTENSIONS = setOf("jpeg", "png", "jpg", "gif", "bmp")

    /** 转码 JPEG 的质量。 */
    private const val JPEG_QUALITY = 95

    /**
     * 准备一次设置所需的图片副本。
     *
     * 参数 context 用于访问媒体库；sourcePath 为源图片绝对路径。
     * 返回副本显示名，失败返回 null。
     */
    fun prepare(
        context: Context,
        sourcePath: String,
    ): String? {
        val source = File(sourcePath)
        if (!source.isFile) return null

        val name = "${PREFIX}_${java.util.UUID.randomUUID()}.jpg"
        val bytes = transcode(source, context)
        val written = bytes != null && bytes.isNotEmpty() && write(context, name, "image/jpeg", bytes)
        if (written) prune(context, name)
        return name.takeIf { written }
    }

    /** 判断扩展名是否在厂商可识别范围内。 */
    fun isSupportedName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /** 把非白名单格式解码后压缩为 JPEG 字节。 */
    private fun transcode(
        file: File,
        context: Context,
    ): ByteArray? =
        runCatching {
            val pixels =
                com.shuiyu.octoberrain.core.device.ScreenSizeProvider
                    .read(context)
            if (!pixels.isValid) return@runCatching null
            val bitmap =
                com.shuiyu.octoberrain.core.render.ImageDecoder.decode(
                    file.absolutePath,
                    maxOf(pixels.widthPx, pixels.heightPx),
                    true,
                )
                    ?: return@runCatching null
            try {
                ByteArrayOutputStream().use { output ->
                    val prepared = Bitmap.createBitmap(pixels.widthPx, pixels.heightPx, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = android.graphics.Canvas(prepared)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        val scale =
                            minOf(
                                pixels.widthPx.toFloat() / bitmap.width,
                                pixels.heightPx.toFloat() / bitmap.height,
                            )
                        val w = bitmap.width * scale
                        val h = bitmap.height * scale
                        val x = (pixels.widthPx - w) / 2f
                        val y = (pixels.heightPx - h) / 2f
                        canvas.drawBitmap(
                            bitmap,
                            null,
                            android.graphics.RectF(x, y, x + w, y + h),
                            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                        )
                        if (!prepared.compress(
                                Bitmap.CompressFormat.JPEG,
                                JPEG_QUALITY,
                                output,
                            )
                        ) {
                            return@runCatching null
                        }
                    } finally {
                        prepared.recycle()
                    }
                    if (output.size() == 0) {
                        return@runCatching null
                    }
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()

    /** 写入公共图片目录；Android 10 起经媒体库插入，低版本直接写文件。 */
    private fun write(
        context: Context,
        name: String,
        mime: String,
        bytes: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, name, mime, bytes)
        } else {
            writeDirectly(name, bytes)
        }

    /** 经媒体库插入图片，写入完成后清除待处理标记；写入失败时删除占位条目。 */
    private fun writeViaMediaStore(
        context: Context,
        name: String,
        mime: String,
        bytes: ByteArray,
    ): Boolean {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri: Uri? = runCatching { resolver.insert(collection, values) }.getOrNull()
        val written =
            uri != null &&
                runCatching {
                    val stream = resolver.openOutputStream(uri) ?: return@runCatching false
                    stream.use { output -> output.write(bytes) }
                    true
                }.getOrDefault(false)
        if (written) {
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            return runCatching { resolver.update(uri, published, null, null) > 0 }.getOrDefault(false)
        } else if (uri != null) {
            runCatching { resolver.delete(uri, null, null) }
        }
        return written
    }

    /** 低版本直接写入公共图片目录。 */
    private fun writeDirectly(
        name: String,
        bytes: ByteArray,
    ): Boolean =
        runCatching {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (!directory.exists() && !directory.mkdirs()) return false
            File(directory, name).writeBytes(bytes)
            true
        }.getOrDefault(false)

    /** 清理同前缀的历史副本，保留本次写入的条目。 */
    private fun prune(
        context: Context,
        keepName: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pruneMediaStore(context, keepName)
        } else {
            pruneFiles(keepName)
        }
    }

    /** 经媒体库查询并删除历史副本。 */
    private fun pruneMediaStore(
        context: Context,
        keepName: String,
    ) {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("$PREFIX%")
        val doomed = ArrayList<Long>()
        runCatching {
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    if (isOldCopy(name, keepName)) {
                        doomed += cursor.getLong(0)
                    }
                }
            }
        }
        doomed.forEach { id ->
            runCatching {
                resolver.delete(collection, "${MediaStore.MediaColumns._ID} = ?", arrayOf(id.toString()))
            }
        }
    }

    private fun isOldCopy(
        name: String?,
        keepName: String,
    ): Boolean {
        val owned = name?.let { it.startsWith("${PREFIX}_") || it.startsWith("$PREFIX.") } == true
        return owned && name != keepName
    }

    /** 旧存储模型下按文件名前缀删除历史副本。 */
    private fun pruneFiles(keepName: String) {
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        runCatching {
            directory.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("$PREFIX.") && file.name != keepName) file.delete()
            }
        }
    }
}
