package com.shuiyu.octoberrain.feature.reader.markdown

import android.content.Context
import android.net.Uri
import com.shuiyu.octoberrain.core.common.DocumentRef
import com.shuiyu.octoberrain.core.common.ReaderCapabilities
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.ReaderFrame
import com.shuiyu.octoberrain.feature.reader.search.SearchProvider
import com.shuiyu.octoberrain.feature.reader.search.SearchScan
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import com.shuiyu.octoberrain.feature.reader.source.copyBounded
import com.shuiyu.octoberrain.feature.reader.web.FileWebResources
import com.shuiyu.octoberrain.feature.reader.web.GrantedUriWebResources
import com.shuiyu.octoberrain.feature.reader.web.OfflineWebHost
import com.shuiyu.octoberrain.feature.reader.web.ReflowEngine
import com.shuiyu.octoberrain.feature.reader.web.ResourcePaths
import com.shuiyu.octoberrain.feature.reader.web.WebDocument
import com.shuiyu.octoberrain.feature.reader.web.WebMessagePolicy
import com.shuiyu.octoberrain.feature.reader.web.WebPosition
import com.shuiyu.octoberrain.feature.reader.web.WebRequest
import com.shuiyu.octoberrain.feature.reader.web.WebResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.CodingErrorAction
import java.util.UUID

internal class MarkdownEngine private constructor(
    private val document: WebDocument,
) : ReflowEngine,
    SearchProvider {
    private var position = WebPosition()
    override val capabilities = ReaderCapabilities(search = true, reflow = true, bookmark = true)

    override suspend fun scan(
        query: String,
        offset: Int,
    ): SearchScan = SearchScan(web = WebRequest(document, position, "searchAll", query = query, searchOffset = offset))

    override suspend fun page(locator: ReaderLocator?): ReaderFrame {
        val saved = locator as? ReaderLocator.Markdown
        val target = WebPosition(saved?.textOffset?.coerceAtLeast(0) ?: 0, saved?.fraction ?: 0.0)
        return frame(WebRequest(document, target))
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
            ReaderLocator.Markdown(request.position.anchor, request.position.fraction),
            "${request.position.page + 1}/${request.position.pages}",
            web = request,
        )

    override fun close() {
        document.resources.close()
    }

    companion object {
        private fun limit(resources: WebResources): Nothing {
            resources.close()
            throw ReaderSourceException(ReaderFailure.LIMIT)
        }

        suspend fun create(
            context: Context,
            ref: DocumentRef,
            source: DocumentHandle,
            font: Int,
        ): MarkdownEngine {
            val supported = withContext(Dispatchers.Main.immediate) { OfflineWebHost.supported() }
            if (!supported) throw ReaderSourceException(ReaderFailure.UNSUPPORTED)
            val output = ByteArrayOutputStream()
            source.openStream().use { copyBounded(it, output, 1024 * 1024L) }
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            val text = decoder.decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString().removePrefix("\uFEFF")
            val session = UUID.randomUUID().toString()
            val resources: WebResources =
                when (ref) {
                    is DocumentRef.LocalPath -> FileWebResources(File(ref.path).canonicalFile.parentFile)
                    is DocumentRef.ContentUri -> GrantedUriWebResources(context, Uri.parse(ref.uri))
                    is DocumentRef.ArchiveEntry -> FileWebResources(null)
                }
            try {
                val html =
                    MarkdownBody.render(text) { value ->
                        ResourcePaths
                            .relative(
                                value,
                            )?.let { "${WebMessagePolicy.ORIGIN}/session/$session/res/${ResourcePaths.encode(it)}" }
                    }
                return MarkdownEngine(WebDocument(session, html, font, resources))
            } catch (_: IllegalArgumentException) {
                limit(resources)
            } catch (_: StackOverflowError) {
                limit(resources)
            }
        }
    }
}
