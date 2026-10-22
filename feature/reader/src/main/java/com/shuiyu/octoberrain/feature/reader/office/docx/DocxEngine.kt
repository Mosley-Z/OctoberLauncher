package com.shuiyu.octoberrain.feature.reader.office.docx

import android.graphics.BitmapFactory
import com.shuiyu.octoberrain.core.common.ReaderCapabilities
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.ReaderFrame
import com.shuiyu.octoberrain.feature.reader.archive.ZipArchive
import com.shuiyu.octoberrain.feature.reader.epub.EpubResources
import com.shuiyu.octoberrain.feature.reader.search.SearchProvider
import com.shuiyu.octoberrain.feature.reader.search.SearchScan
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import com.shuiyu.octoberrain.feature.reader.web.ImageResourceBudget
import com.shuiyu.octoberrain.feature.reader.web.ReflowEngine
import com.shuiyu.octoberrain.feature.reader.web.ResourcePaths
import com.shuiyu.octoberrain.feature.reader.web.WebDocument
import com.shuiyu.octoberrain.feature.reader.web.WebPosition
import com.shuiyu.octoberrain.feature.reader.web.WebRequest
import com.shuiyu.octoberrain.feature.reader.web.WebResource
import com.shuiyu.octoberrain.feature.reader.web.WebResources
import java.util.UUID

/** 小型DOCX的正文/原版式双模式；源码与解压输出先按预算检查。 */
internal class DocxEngine private constructor(
    private val zip: ZipArchive,
    private val document: WebDocument,
    private val preview: Boolean,
) : ReflowEngine,
    SearchProvider {
    private var position = WebPosition()
    override val capabilities =
        ReaderCapabilities(search = true, reflow = !preview, pageJump = preview, bookmark = true)

    override suspend fun scan(
        query: String,
        offset: Int,
    ): SearchScan = SearchScan(web = WebRequest(document, position, "searchAll", query = query, searchOffset = offset))

    override suspend fun page(locator: ReaderLocator?): ReaderFrame {
        val saved = locator as? ReaderLocator.Docx
        val target = WebPosition(saved?.textOffset ?: 0, saved?.fraction ?: 0.0, saved?.page ?: 0)
        val kind = if (preview && saved != null && saved.page >= 0) "page" else "position"
        return frame(WebRequest(document, target, kind))
    }

    override suspend fun turn(delta: Int): ReaderFrame = frame(WebRequest(document, position, "turn", delta))

    override suspend fun findNext(query: String): ReaderFrame =
        frame(WebRequest(document, position, "search", query = query))

    override fun atAnchor(anchor: Int): ReaderFrame = frame(WebRequest(document, position.copy(anchor = anchor)))

    override fun accept(position: WebPosition): ReaderFrame {
        this.position = position
        return frame(WebRequest(document, position))
    }

    private fun frame(request: WebRequest): ReaderFrame =
        ReaderFrame(
            ReaderLocator.Docx(preview, request.position.anchor, request.position.fraction, request.position.page),
            (if (preview) "原版式" else "正文") + " · ${request.position.page + 1}/${request.position.pages}",
            web = request,
        )

    override fun close() {
        document.resources.close()
        zip.close()
    }

    companion object {
        fun create(
            source: DocumentHandle,
            locator: ReaderLocator?,
            font: Int,
        ): DocxEngine {
            val file = source.archiveFile()
            if (file.length() > 8 * 1024 * 1024L) limit()
            val zip = ZipArchive(file)
            var retained = false
            try {
                val maximum = 32 * 1024 * 1024L
                if (zip.items.any { it.size !in 0..maximum } || zip.items.sumOf { it.size } > maximum) limit()
                checkImages(zip)
                val main = readXml(zip, "word/document.xml")
                val rels =
                    if (zip.items.any { it.path == "word/_rels/document.xml.rels" }) {
                        readXml(zip, "word/_rels/document.xml.rels")
                    } else {
                        "<Relationships/>"
                    }
                val session = UUID.randomUUID().toString()
                val resources = DocxResources(zip, source)
                val body =
                    DocxBody.render(main, rels) { target ->
                        ResourcePaths.relative("word/$target")?.takeIf { ImageResourceBudget.mime(it) != null }?.let {
                            "https://reader.october.invalid/session/$session/res/${ResourcePaths.encode(it)}"
                        }
                    }
                val preview = (locator as? ReaderLocator.Docx)?.preview ?: false
                val document = WebDocument(session, body, font, resources, docxPreview = preview)
                return DocxEngine(zip, document, preview).also { retained = true }
            } catch (_: IllegalArgumentException) {
                limit()
            } finally {
                if (!retained) zip.close()
            }
        }

        private fun checkImages(zip: ZipArchive) {
            val budget = ImageResourceBudget()
            zip.items.filter { it.path.startsWith("word/media/") && !it.directory }.forEach { item ->
                if (ImageResourceBudget.mime(item.path) != null) {
                    if (!budget.acceptBytes(item.size)) limit()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    zip.open(item.path).use { BitmapFactory.decodeStream(it, null, bounds) }
                    if (!budget.acceptPixels(bounds.outWidth, bounds.outHeight)) limit()
                }
            }
        }

        private fun readXml(
            zip: ZipArchive,
            path: String,
        ): String = zip.open(path, DocxBody.MAX_XML_BYTES.toLong()).use { it.readBytes().toString(Charsets.UTF_8) }

        private fun limit(): Nothing = throw ReaderSourceException(ReaderFailure.LIMIT)
    }
}

private class DocxResources(
    private val zip: ZipArchive,
    private val source: DocumentHandle,
) : WebResources {
    private val images = EpubResources(zip)
    private var closed = false

    @Synchronized override fun open(path: String): WebResource? =
        when {
            closed -> null
            path == "source.docx" -> WebResource("application/octet-stream", source.openStream())
            else -> images.open(path)
        }

    @Synchronized override fun close() {
        closed = true
        images.close()
    }
}
