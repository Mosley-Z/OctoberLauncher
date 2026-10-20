package com.shuiyu.octoberrain.feature.files.ui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.feature.files.data.FileIntents
import com.shuiyu.octoberrain.feature.files.ui.viewer.PhotoScreen
import com.shuiyu.octoberrain.feature.files.ui.viewer.PhotoSource
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * 图片查看入口。
 *
 * 供其他应用以 ACTION_VIEW 打开单张图片。带 [FileIntents.EXTRA_PATH] 时直接打开该路径的本地文件；
 * 只有 content Uri 时先把内容复制到应用缓存，复制上限 64 MiB，复制成功后交给查看页显示。
 * 外部传入的内容只作为字节来源，查看页不对外部 Uri 直接执行文件操作。
 */
class PhotoEntryActivity : Activity() {
    private var screen: PhotoScreen? = null

    /**
     * 解析入口参数并建立查看页。
     *
     * 参数 savedInstanceState 为系统恢复状态，本入口不保留额外状态。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = resolvePath()
        if (path == null) {
            Toast.makeText(this, "无法打开图片", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val created = PhotoScreen(listOf(path), 0, PhotoSource.EXTERNAL) { finish() }
        screen = created
        setContentView(created.createView(this))
    }

    override fun onResume() {
        super.onResume()
        screen?.onResume()
    }

    override fun onDestroy() {
        screen?.onDestroy()
        screen = null
        super.onDestroy()
    }

    /** 优先取显式路径，其次把外部 Uri 复制到缓存；两者都不可用时返回 null。 */
    private fun resolvePath(): String? {
        val direct = intent?.getStringExtra(FileIntents.EXTRA_PATH).orEmpty()
        val external = intent?.data
        return when {
            direct.isNotEmpty() && File(direct).isFile -> direct
            external != null -> copyToCache(external)
            else -> null
        }
    }

    /** 把外部图片复制到缓存目录，超过 [MAX_COPY_BYTES] 或读取失败时返回 null。 */
    private fun copyToCache(uri: Uri): String? {
        val directory = File(cacheDir, "viewer").apply { mkdirs() }
        val target = File(directory, "${System.currentTimeMillis()}_${displayName(uri)}")
        val copied =
            runCatching {
                contentResolver.openInputStream(uri)?.use { source ->
                    FileOutputStream(target).use { output -> copyStream(source, output) }
                } ?: false
            }.getOrDefault(false)
        if (!copied) target.delete()
        return if (copied) target.absolutePath else null
    }

    /** 逐块复制输入流，累计超过上限时中止并返回 false。 */
    private fun copyStream(
        source: InputStream,
        output: OutputStream,
    ): Boolean {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        var read = source.read(buffer)
        while (read >= 0) {
            total += read
            if (total > MAX_COPY_BYTES) {
                InkLog.w(TAG, "外部图片超过复制上限：$total 字节")
                return false
            }
            output.write(buffer, 0, read)
            read = source.read(buffer)
        }
        return true
    }

    /** 查询外部 Uri 的显示名称，缺失时退回路径末段并做文件名清洗。 */
    private fun displayName(uri: Uri): String {
        val queried =
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
        val raw = queried?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "image"
        val cleaned = raw.substringAfterLast('/').replace(ILLEGAL_CHARS, "_").trim('.')
        return cleaned.ifEmpty { "image" }
    }

    private companion object {
        private const val TAG = "图片查看入口"
        private const val BUFFER_SIZE = 16 * 1024
        private const val MAX_COPY_BYTES = 64L * 1024 * 1024
        private val ILLEGAL_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
