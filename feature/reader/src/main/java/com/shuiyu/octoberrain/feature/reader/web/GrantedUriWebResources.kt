package com.shuiyu.octoberrain.feature.reader.web

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Process
import com.shuiyu.octoberrain.feature.reader.source.copyBounded
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** 单文件URI不等于目录授权；只读取已经显式授予的同目录图片。 */
internal class GrantedUriWebResources(
    context: Context,
    private val source: Uri,
) : WebResources {
    private val context = context.applicationContext
    private val budget = ImageResourceBudget()

    @Volatile private var closed = false

    override fun open(path: String): WebResource? {
        if (closed) return null
        return ResourcePaths.relative(path)?.let { relative ->
            val target = sibling(relative)
            val granted =
                context.checkUriPermission(
                    target,
                    Process.myPid(),
                    Process.myUid(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            if (granted != PackageManager.PERMISSION_GRANTED) return@let null
            val mime = ImageResourceBudget.mime(relative) ?: return@let null
            readImage(target, mime)
        }
    }

    private fun sibling(path: String): Uri =
        source
            .buildUpon()
            .encodedPath(source.encodedPath.orEmpty().substringBeforeLast('/') + "/" + ResourcePaths.encode(path))
            .clearQuery()
            .fragment(null)
            .build()

    private fun readImage(
        uri: Uri,
        mime: String,
    ): WebResource? =
        run {
            val output = ByteArrayOutputStream()
            context.contentResolver.openInputStream(uri)?.use { copyBounded(it, output, 8 * 1024 * 1024L) }
                ?: return@run null
            if (!budget.acceptBytes(output.size().toLong())) return@run null
            val data = output.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (!budget.acceptPixels(bounds.outWidth, bounds.outHeight)) return@run null
            WebResource(mime, ByteArrayInputStream(data))
        }

    override fun close() {
        closed = true
    }
}
