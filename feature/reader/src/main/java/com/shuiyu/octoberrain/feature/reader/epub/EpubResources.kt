package com.shuiyu.octoberrain.feature.reader.epub

import android.graphics.BitmapFactory
import com.shuiyu.octoberrain.feature.reader.archive.ArchivePaths
import com.shuiyu.octoberrain.feature.reader.archive.ZipArchive
import com.shuiyu.octoberrain.feature.reader.source.copyBounded
import com.shuiyu.octoberrain.feature.reader.web.ImageResourceBudget
import com.shuiyu.octoberrain.feature.reader.web.WebResource
import com.shuiyu.octoberrain.feature.reader.web.WebResources
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** AssetLoader already decodes its path. Do not URL-decode ZIP names a second time. */
internal class EpubResources(
    private val zip: ZipArchive,
) : WebResources {
    private val budget = ImageResourceBudget()

    @Volatile private var closed = false

    @Synchronized override fun open(path: String): WebResource? =
        run {
            if (closed) return@run null
            val normalized = runCatching { ArchivePaths.normalize(path) }.getOrNull() ?: return@run null
            val mime = ImageResourceBudget.mime(normalized) ?: return@run null
            val output = ByteArrayOutputStream()
            zip.open(normalized, 8 * 1024 * 1024L).use { copyBounded(it, output, 8 * 1024 * 1024L) }
            if (!budget.acceptBytes(output.size().toLong())) return@run null
            val data = output.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (budget.acceptPixels(bounds.outWidth, bounds.outHeight)) {
                WebResource(mime, ByteArrayInputStream(data))
            } else {
                null
            }
        }

    @Synchronized override fun close() {
        closed = true
    }
}
