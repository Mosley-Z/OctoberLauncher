package com.shuiyu.octoberrain.feature.reader.web

import android.graphics.BitmapFactory
import java.io.File

/** 图片限当前目录/像素/累计压缩字节；读取由AssetLoader后台线程执行。 */
internal class FileWebResources(
    private val root: File?,
) : WebResources {
    private val budget = ImageResourceBudget()

    @Volatile private var closed = false

    override fun open(path: String): WebResource? {
        if (closed || root == null) return null
        return ResourcePaths.relative(path)?.let { relative ->
            val file = File(root, relative).canonicalFile
            if (!file.path.startsWith(root.canonicalPath + File.separator) || !file.isFile) return@let null
            image(file)
        }
    }

    private fun image(file: File): WebResource? =
        run {
            val mime = ImageResourceBudget.mime(file.name) ?: return@run null
            if (!budget.acceptBytes(file.length())) return@run null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (!budget.acceptPixels(bounds.outWidth, bounds.outHeight)) return@run null
            WebResource(mime, file.inputStream())
        }

    override fun close() {
        closed = true
    }
}
