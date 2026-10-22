package com.shuiyu.octoberrain.feature.reader.epub

import com.shuiyu.octoberrain.core.common.ReaderCapabilities
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.ReaderFrame
import com.shuiyu.octoberrain.feature.reader.archive.ZipArchive
import com.shuiyu.octoberrain.feature.reader.search.SearchCollector
import com.shuiyu.octoberrain.feature.reader.search.SearchProvider
import com.shuiyu.octoberrain.feature.reader.search.SearchScan
import com.shuiyu.octoberrain.feature.reader.search.collectMatches
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import com.shuiyu.octoberrain.feature.reader.web.OfflineWebHost
import com.shuiyu.octoberrain.feature.reader.web.ReflowEngine
import com.shuiyu.octoberrain.feature.reader.web.WebDocument
import com.shuiyu.octoberrain.feature.reader.web.WebPosition
import com.shuiyu.octoberrain.feature.reader.web.WebRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

internal class EpubEngine private constructor(
    private val zip: ZipArchive,
    private val book: EpubPackage,
    private val font: Int,
) : ReflowEngine,
    SearchProvider {
    private var spine = 0
    private var position = WebPosition()
    private var content: EpubContent? = null
    private var document: WebDocument? = null
    val toc: List<EpubToc> get() = book.toc
    override val capabilities = ReaderCapabilities(search = true, reflow = true, bookmark = true)

    override suspend fun scan(
        query: String,
        offset: Int,
    ): SearchScan {
        val collector = SearchCollector(offset)
        for (index in book.chapters.indices) {
            currentCoroutineContext().ensureActive()
            val chapter = EpubContent.build(book, index, "search")
            collectMatches(chapter.text, query, collector) { ReaderLocator.Epub(index, it) }
        }
        return SearchScan(page = collector.page())
    }

    override suspend fun page(locator: ReaderLocator?): ReaderFrame {
        val saved = locator as? ReaderLocator.Epub
        return load(saved?.spine ?: 0, saved?.textOffset ?: 0, fraction = saved?.fraction ?: 0.0)
    }

    override suspend fun turn(delta: Int): ReaderFrame =
        when {
            delta > 0 && position.page >= position.pages - 1 && spine < book.chapters.lastIndex -> load(spine + 1)
            delta < 0 && position.page == 0 && spine > 0 -> load(spine - 1, Int.MAX_VALUE, fraction = 1.0)
            else -> frame(WebRequest(checkNotNull(document), position, "turn", delta))
        }

    suspend fun chapter(target: EpubToc): ReaderFrame = load(target.spine, fragment = target.fragment)

    override fun atAnchor(anchor: Int): ReaderFrame =
        frame(WebRequest(checkNotNull(document), position.copy(anchor = anchor)))

    override fun accept(position: WebPosition): ReaderFrame {
        this.position = position
        return frame(WebRequest(checkNotNull(document), position))
    }

    override suspend fun followLink(url: String): ReaderFrame? = content?.links?.get(url)?.let { chapter(it) }

    private fun load(
        index: Int,
        anchor: Int = 0,
        fragment: String = "",
        fraction: Double = 0.0,
    ): ReaderFrame {
        val target = index.coerceIn(book.chapters.indices)
        val session = UUID.randomUUID().toString()
        val prepared = EpubContent.build(book, target, session)
        val next = WebDocument(session, prepared.html, font, EpubResources(zip), prepared.links.keys, prepared.styles)
        document?.resources?.close()
        document = next
        content = prepared
        spine = target
        position =
            WebPosition(
                prepared.anchors[fragment] ?: anchor.coerceIn(0, prepared.text.length),
                fraction.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0,
            )
        return frame(WebRequest(next, position))
    }

    private fun frame(request: WebRequest): ReaderFrame =
        ReaderFrame(
            ReaderLocator.Epub(spine, request.position.anchor, request.position.fraction),
            "第${spine + 1}/${book.chapters.size}章 · ${request.position.page + 1}/${request.position.pages}",
            web = request,
        )

    override fun close() {
        document?.resources?.close()
        document = null
        content = null
        zip.close()
    }

    companion object {
        suspend fun create(
            source: DocumentHandle,
            font: Int,
        ): EpubEngine {
            val supported = withContext(Dispatchers.Main.immediate) { OfflineWebHost.supported() }
            if (!supported) throw ReaderSourceException(ReaderFailure.UNSUPPORTED)
            val zip = ZipArchive(source.archiveFile())
            var retained = false
            try {
                val engine = EpubEngine(zip, EpubPackage(zip), font)
                retained = true
                return engine
            } finally {
                if (!retained) zip.close()
            }
        }
    }
}
