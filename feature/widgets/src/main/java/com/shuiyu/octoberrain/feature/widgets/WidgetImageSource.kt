package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException

/** 权限不可用不等于原图删除。 */
internal object WidgetImageSource {
    /** 系统照片选择器也可能经 GET_CONTENT 返回可持久授权的 URI。 */
    fun retainAccess(
        context: Context,
        uri: Uri,
    ) {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    fun missing(
        context: Context,
        source: String?,
    ): Boolean {
        if (source.isNullOrBlank()) return false
        return runCatching {
            val uri = Uri.parse(source)
            when (uri.scheme) {
                "file" -> uri.path?.let { missingFile(File(it)) } ?: false
                "content" -> missingContent(context, uri)
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun missingContent(
        context: Context,
        uri: Uri,
    ): Boolean {
        if (uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isDocumentUri(context, uri)) {
            val id = DocumentsContract.getDocumentId(uri)
            if (id.startsWith("primary:")) {
                val root = Environment.getExternalStorageDirectory().canonicalFile
                val file = File(root, id.removePrefix("primary:")).canonicalFile
                if (file.path.startsWith(root.path + File.separator) && missingFile(file)) return true
            }
        }
        val permitted =
            context.checkUriPermission(
                uri,
                Process.myPid(),
                Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
        return permitted && unavailableGrantedContent(context, uri)
    }

    private fun unavailableGrantedContent(
        context: Context,
        uri: Uri,
    ): Boolean {
        val empty = context.contentResolver.query(uri, null, null, null, null)?.use { !it.moveToFirst() } ?: false
        if (empty) return true
        // 仅本地系统照片来源可用文件缺失判定；云端离线和授权丢失仍为未知。
        val parts = uri.pathSegments
        val localPicker =
            uri.authority == "media" &&
                parts.size == 5 &&
                parts[0] == "picker" &&
                parts[2] == "com.android.providers.media.photopicker" &&
                parts[3] == "media"
        return if (localPicker && Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { }
                false
            } catch (_: FileNotFoundException) {
                true
            }
        } else {
            false
        }
    }

    internal fun missingFile(file: File): Boolean =
        runCatching {
            !file.isFile && file.parentFile?.list()?.let { file.name !in it } == true
        }.getOrDefault(false)
}
