package com.shuiyu.octoberrain.feature.reader.epub

import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.feature.reader.archive.ZipArchive
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import com.shuiyu.octoberrain.feature.reader.source.copyBounded
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.nio.charset.CodingErrorAction

internal data class EpubChapter(
    val path: String,
    val title: String,
)

internal data class EpubToc(
    val title: String,
    val spine: Int,
    val fragment: String = "",
)

/** Only package metadata is retained. Chapter content is read separately with a 2MiB output budget. */
internal class EpubPackage(
    private val zip: ZipArchive,
) {
    val chapters: List<EpubChapter>
    val toc: List<EpubToc>

    init {
        if (zip.items.any { it.path == "META-INF/encryption.xml" }) unsupported()
        val container = xml("META-INF/container.xml")
        val root = tags(container, "rootfile").firstOrNull()?.attr("full-path") ?: corrupt()
        val opfPath = EpubPaths.resolve("container.xml", root)?.path ?: corrupt()
        val opf = xml(opfPath)
        if (tags(opf, "meta").any { it.attr("property") == "rendition:layout" && it.text() == "pre-paginated" }) {
            unsupported()
        }
        val items = tags(opf, "item").associateBy { it.id() }
        val spine = tags(opf, "itemref").filter { it.attr("linear") != "no" }
        if (spine.isEmpty() || spine.size > 5000) corrupt()
        if (spine.any { "rendition:layout-pre-paginated" in it.attr("properties").split(' ') }) unsupported()
        val paths =
            zip.items
                .filter { !it.directory }
                .map { it.path }
                .toHashSet()
        chapters =
            spine.mapIndexed { index, item ->
                val reference = items[item.attr("idref")] ?: corrupt()
                if (reference.attr("media-type") !in listOf("application/xhtml+xml", "text/html")) unsupported()
                val path = EpubPaths.resolve(opfPath, reference.attr("href"))?.path ?: corrupt()
                if (path !in paths) corrupt()
                EpubChapter(path, "第${index + 1}章")
            }
        toc = readToc(opfPath, opf, items).ifEmpty { chapters.mapIndexed { index, c -> EpubToc(c.title, index) } }
    }

    fun read(path: String): String {
        val bytes = ByteArrayOutputStream()
        zip.open(path, 2 * 1024 * 1024L).use { copyBounded(it, bytes, 2 * 1024 * 1024L) }
        return Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes.toByteArray()))
            .toString()
            .removePrefix("\uFEFF")
    }

    private fun xml(path: String): Document {
        val text = read(path)
        if (text.count { it == '<' } > 50000) throw ReaderSourceException(ReaderFailure.LIMIT)
        return Jsoup.parse(text, "", Parser.xmlParser())
    }

    private fun readToc(
        opfPath: String,
        opf: Document,
        items: Map<String, Element>,
    ): List<EpubToc> {
        val nav = items.values.firstOrNull { "nav" in it.attr("properties").split(' ') }
        val ncx = tags(opf, "spine").firstOrNull()?.attr("toc")?.let { items[it] }
        val path = (nav ?: ncx)?.let { EpubPaths.resolve(opfPath, it.attr("href"))?.path } ?: return emptyList()
        val document = xml(path)
        val links = if (nav != null) tags(document, "a") else tags(document, "navPoint")
        return links.take(5000).mapNotNull { link ->
            val href = if (nav != null) link.attr("href") else tags(link, "content").firstOrNull()?.attr("src")
            val target = href?.let { EpubPaths.resolve(path, it) } ?: return@mapNotNull null
            val index = chapters.indexOfFirst { it.path == target.path }
            if (index < 0) return@mapNotNull null
            val title = if (nav != null) link.text() else tags(link, "text").firstOrNull()?.text().orEmpty()
            EpubToc(title.take(256).ifBlank { chapters[index].title }, index, target.fragment)
        }
    }

    private fun tags(
        root: Element,
        name: String,
    ): List<Element> = root.getAllElements().filter { it.tagName().substringAfterLast(':') == name }

    private fun corrupt(): Nothing = throw ReaderSourceException(ReaderFailure.CORRUPT)

    private fun unsupported(): Nothing = throw ReaderSourceException(ReaderFailure.UNSUPPORTED)
}
