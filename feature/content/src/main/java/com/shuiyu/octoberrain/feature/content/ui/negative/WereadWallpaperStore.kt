package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 墨台壁纸写入工具。
 *
 * 负责把内核生成的壁纸位图写入相册或应用缓存：相册写入优先使用媒体库，
 * 旧系统回退到直接写文件并通知媒体扫描；缓存写入用于系统分享前的临时文件，
 * 路径与文件页分享共用同一授权目录。
 */
internal object WereadWallpaperStore {
    /**
     * 把位图写入公共图片目录的 TenRain 子目录。
     *
     * @param context 应用上下文
     * @param name 展示名
     * @param bitmap 需要保存的位图
     * @return 写入成功返回 true
     */
    fun saveToGallery(
        context: Context,
        name: String,
        bitmap: Bitmap,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, name, bitmap)
        } else {
            writeViaFile(context, name, bitmap)
        }

    /**
     * 把位图写入应用缓存目录的分享子目录。
     *
     * @param context 应用上下文
     * @param bitmap 需要写入的位图
     * @return 写入后的文件，失败返回 null
     */
    fun writeShareFile(
        context: Context,
        bitmap: Bitmap,
    ): File? {
        val directory = File(context.cacheDir, SHARE_DIRECTORY)
        var result: File? = null
        if (directory.isDirectory || directory.mkdirs()) {
            val target = File(directory, fileName())
            val stored = writeBitmap(target, bitmap)
            if (stored) {
                result = target
            } else {
                target.delete()
            }
        }
        return result
    }

    /**
     * 生成壁纸文件名，以时间戳保证同目录内不重名。
     *
     * @return 形如 tenrain-desk-20260101-120000.png 的文件名
     */
    fun fileName(): String {
        val stamp = SimpleDateFormat(FILE_STAMP_PATTERN, Locale.US).format(Date())
        return FILE_PREFIX + stamp + FILE_SUFFIX
    }

    /**
     * 通过媒体库写入公共图片目录。
     *
     * @param context 应用上下文
     * @param name 展示名
     * @param bitmap 需要保存的位图
     * @return 写入成功返回 true
     */
    private fun writeViaMediaStore(
        context: Context,
        name: String,
        bitmap: Bitmap,
    ): Boolean {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_PNG)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + ALBUM,
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        val uri =
            runCatching {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }.getOrNull()
        var stored = false
        if (uri != null) {
            stored =
                runCatching {
                    resolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                }.getOrNull() == true
            if (stored) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                runCatching { resolver.update(uri, values, null, null) }
            } else {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
        return stored
    }

    /**
     * 旧系统上直接写入公共图片目录并通知媒体扫描。
     *
     * @param context 应用上下文
     * @param name 展示名
     * @param bitmap 需要保存的位图
     * @return 写入成功返回 true
     */
    @Suppress("DEPRECATION")
    private fun writeViaFile(
        context: Context,
        name: String,
        bitmap: Bitmap,
    ): Boolean {
        val directory =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                ALBUM,
            )
        var stored = false
        if (directory.isDirectory || directory.mkdirs()) {
            val target = File(directory, name)
            stored = writeBitmap(target, bitmap)
            if (stored) {
                runCatching {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(target.absolutePath),
                        arrayOf(MIME_PNG),
                        null,
                    )
                }
            } else {
                target.delete()
            }
        }
        return stored
    }

    /**
     * 把位图压缩写入目标文件。
     *
     * @param target 目标文件
     * @param bitmap 需要写入的位图
     * @return 写入成功返回 true
     */
    private fun writeBitmap(
        target: File,
        bitmap: Bitmap,
    ): Boolean =
        runCatching {
            FileOutputStream(target).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }.getOrDefault(false)

    /** 相册子目录名，与负一屏壁纸的存放约定一致。 */
    private const val ALBUM: String = "TenRain"

    /** 分享文件所在缓存子目录名，与文件页分享共用同一授权路径。 */
    private const val SHARE_DIRECTORY: String = "share"

    /** 壁纸文件名前缀。 */
    private const val FILE_PREFIX: String = "tenrain-desk-"

    /** 壁纸文件扩展名。 */
    private const val FILE_SUFFIX: String = ".png"

    /** 文件名时间戳格式。 */
    private const val FILE_STAMP_PATTERN: String = "yyyyMMdd-HHmmss"

    /** 保存与分享使用的图片类型。 */
    private const val MIME_PNG: String = "image/png"
}
