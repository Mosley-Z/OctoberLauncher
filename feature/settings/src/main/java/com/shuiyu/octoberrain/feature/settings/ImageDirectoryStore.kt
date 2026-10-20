package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import com.shuiyu.octoberrain.core.render.loadOrDecode
import java.io.File

/**
 * 图标图片目录的共享存取与图片采样解码。
 *
 * 美化模式与单个应用的图标选择页共用同一存储位置，切换目录后两处保持一致；
 * 目录以绝对路径写入独立 SharedPreferences，读取时校验目录仍然存在。
 */
internal object ImageDirectoryStore {
    /** 偏好文件名。 */
    private const val PREFS_NAME = "beautify_images"

    /** 目录路径键。 */
    private const val KEY_DIRECTORY = "image_directory"

    /** 允许的图片扩展名。 */
    val IMAGE_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp", "bmp")

    /** 单次最多列出的图片数量。 */
    const val MAX_IMAGES: Int = 200

    /** 默认目录：外部存储的 Pictures。 */
    val defaultDirectory: File
        get() = File(Environment.getExternalStorageDirectory(), "Pictures")

    /** 读取已保存的图片目录，缺失或目录已不存在时返回默认目录。 */
    fun load(context: Context): File {
        val raw =
            runCatching {
                prefs(context).getString(KEY_DIRECTORY, null)
            }.getOrNull()
        val saved = raw?.let(::File)
        return if (saved != null && saved.isDirectory) saved else defaultDirectory
    }

    /** 保存图片目录，供另一页面复用。 */
    fun save(
        context: Context,
        directory: File,
    ) {
        runCatching {
            prefs(context).edit().putString(KEY_DIRECTORY, directory.absolutePath).apply()
        }
    }

    /** 列出目录内的图片文件，按修改时间倒序并限制数量。 */
    fun images(
        directory: File,
        limit: Int = MAX_IMAGES,
    ): List<File> {
        val files =
            runCatching {
                directory.listFiles().orEmpty().filter { file ->
                    file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS
                }
            }.getOrDefault(emptyList())
        return files.sortedByDescending { it.lastModified() }.take(limit)
    }

    /** 目录 Uri 转换为本地目录，仅支持 file 与外部存储树 Uri。 */
    fun directoryOf(uri: Uri): File? {
        if (uri.scheme == "file") return uri.path?.let(::File)
        val documentId =
            runCatching {
                android.provider.DocumentsContract.getTreeDocumentId(uri)
            }.getOrNull() ?: return null
        val parts = documentId.split(":", limit = 2)
        val root =
            if (parts[0].equals("primary", ignoreCase = true)) {
                Environment.getExternalStorageDirectory()
            } else {
                File("/storage/${parts[0]}")
            }
        return if (parts.size == 2) File(root, parts[1]) else root
    }

    /**
     * 按目标边长取样解码图片，并读写缩略图缓存。
     *
     * 参数 context 用于定位缓存目录，path 为图片文件路径，maxSizePx 为目标长边；
     * 缓存未命中时经 [ImageDecoder] 解码，写入缓存后返回量化位图，调用方不得回收。
     */
    fun decodeSampled(
        context: Context,
        path: String,
        maxSizePx: Int,
    ): Bitmap? {
        val file = File(path).takeIf { it.isFile } ?: return null
        return ThumbCacheRegistry.get(context, maxSizePx).loadOrDecode(file) {
            ImageDecoder.decode(path, maxSizePx)
        }
    }

    /** 偏好文件读写入口。 */
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
