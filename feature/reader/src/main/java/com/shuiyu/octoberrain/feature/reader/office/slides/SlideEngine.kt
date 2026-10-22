package com.shuiyu.octoberrain.feature.reader.office.slides

import android.graphics.BitmapFactory
import com.shuiyu.octoberrain.core.common.ReaderCapabilities
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.ReaderFrame
import com.shuiyu.octoberrain.feature.reader.archive.ZipArchive
import com.shuiyu.octoberrain.feature.reader.search.SearchHit
import com.shuiyu.octoberrain.feature.reader.search.SearchProvider
import com.shuiyu.octoberrain.feature.reader.search.SearchScan
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import com.shuiyu.octoberrain.feature.reader.web.ImageResourceBudget
import com.shuiyu.octoberrain.feature.reader.web.OfficeWebMode
import com.shuiyu.octoberrain.feature.reader.web.ReflowEngine
import com.shuiyu.octoberrain.feature.reader.web.WebDocument
import com.shuiyu.octoberrain.feature.reader.web.WebPosition
import com.shuiyu.octoberrain.feature.reader.web.WebRequest
import com.shuiyu.octoberrain.feature.reader.web.WebResource
import com.shuiyu.octoberrain.feature.reader.web.WebResources
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.util.UUID

/** IO-side slide navigation/search; each frame contains exactly one slide or one outline. */
internal class SlideEngine private constructor(
    private val zip: ZipArchive,
    private val index: SlideIndex,
    private val font: Int,
    private var current: ReaderLocator.Slide,
) : ReflowEngine,
    SearchProvider {
    override val capabilities = ReaderCapabilities(search = true, pageJump = true, bookmark = true)
    val slideCount: Int get() = index.paths.size
    private var active: WebDocument? = null

    fun outline(): List<String> = index.texts.mapIndexed { i, text -> "${i + 1}. ${text.take(96).replace('\n', ' ')}" }

    override suspend fun page(locator: ReaderLocator?): ReaderFrame {
        current =
            ((locator as? ReaderLocator.Slide) ?: current).let {
                it.copy(slide = it.slide.coerceIn(0, slideCount - 1), textOffset = it.textOffset.coerceAtLeast(0))
            }
        return makeFrame()
    }

    override suspend fun turn(delta: Int): ReaderFrame =
        page(current.copy(slide = current.slide + delta, textOffset = 0, fraction = 0.0))

    override suspend fun scan(
        query: String,
        offset: Int,
    ): SearchScan = SearchScan(page = SlideSearch.scan(index.texts, current.outline, query, offset))

    override suspend fun showHit(
        hit: SearchHit,
        query: String,
    ): ReaderFrame {
        val saved = hit.locator as ReaderLocator.Slide
        val ordinal = SlideSearch.ordinal(index.texts[saved.slide], query, saved.textOffset)
        val target = page(saved)
        val request = requireNotNull(target.web).copy(kind = "hit", query = query, matchOrdinal = ordinal)
        return target.copy(web = request)
    }

    override fun atAnchor(anchor: Int): ReaderFrame {
        current = current.copy(textOffset = anchor.coerceAtLeast(0))
        return makeFrame()
    }

    override fun accept(position: WebPosition): ReaderFrame {
        current = current.copy(textOffset = position.anchor, fraction = position.fraction)
        return frame(requireNotNull(active), position)
    }

    private fun makeFrame(query: String? = null): ReaderFrame {
        active?.resources?.close()
        val shell = Document.createShell("")
        val note = "复杂图形、图表、嵌入对象及EMF/WMF可能未显示；可切换文字提纲"
        val body = shell.body()
        if (current.outline) body.appendElement("pre").text(index.texts[current.slide].ifEmpty { "本页无可提取文字" })
        body.appendElement("p").text(if (index.warnings[current.slide]) "[本页包含不支持的对象] $note" else note)
        val bytes = if (current.outline) null else SlidePackage(zip, index).build(current.slide)
        val document =
            WebDocument(
                UUID.randomUUID().toString(),
                body.html(),
                font,
                SlideResources(bytes),
                office = if (current.outline) null else OfficeWebMode.SLIDES,
            )
        active = document
        val position = WebPosition(current.textOffset, current.fraction)
        return frame(document, position, query)
    }

    private fun frame(
        document: WebDocument,
        position: WebPosition,
        query: String? = null,
    ): ReaderFrame =
        ReaderFrame(
            current,
            "${if (current.outline) "提纲" else "原版式"} · ${current.slide + 1}/$slideCount",
            web = WebRequest(document, position, query = query),
        )

    override fun close() {
        active?.resources?.close()
        active = null
        zip.close()
    }

    companion object {
        fun create(
            source: DocumentHandle,
            locator: ReaderLocator?,
            font: Int,
        ): SlideEngine {
            val file = source.archiveFile()
            if (file.length() > 8 * 1024 * 1024L) throw ReaderSourceException(ReaderFailure.LIMIT)
            val zip = ZipArchive(file)
            var retained = false
            try {
                if (zip.items.any { it.size !in 0..32 * 1024 * 1024L } ||
                    zip.items.sumOf { it.size } > 32 * 1024 * 1024L
                ) {
                    limit()
                }
                checkImages(zip)
                return SlideEngine(
                    zip,
                    SlideIndex(zip),
                    font,
                    locator as? ReaderLocator.Slide ?: ReaderLocator.Slide(),
                ).also {
                    retained = true
                }
            } finally {
                if (!retained) zip.close()
            }
        }

        private fun limit(): Nothing = throw ReaderSourceException(ReaderFailure.LIMIT)

        private fun checkImages(zip: ZipArchive) {
            val budget = ImageResourceBudget()
            zip.items.filter { it.path.startsWith("ppt/media/") && ImageResourceBudget.mime(it.path) != null }.forEach {
                if (!budget.acceptBytes(it.size)) throw ReaderSourceException(ReaderFailure.LIMIT)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                zip.open(it.path).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
                if (!budget.acceptPixels(
                        options.outWidth,
                        options.outHeight,
                    )
                ) {
                    throw ReaderSourceException(ReaderFailure.LIMIT)
                }
            }
        }
    }
}

private class SlideResources(
    private var bytes: ByteArray?,
) : WebResources {
    @Synchronized override fun open(path: String): WebResource? =
        if (path ==
            "source.pptx"
        ) {
            bytes?.let { WebResource("application/octet-stream", ByteArrayInputStream(it)) }
        } else {
            null
        }

    @Synchronized override fun close() {
        bytes = null
    }
}
