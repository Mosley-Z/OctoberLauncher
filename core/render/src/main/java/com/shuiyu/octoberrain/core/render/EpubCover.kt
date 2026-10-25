package com.shuiyu.octoberrain.core.render

import android.graphics.Bitmap
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

private const val MAX_XML_BYTES = 512 * 1024
private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
private val extensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
private val coverIds = setOf("cover", "cover-image", "coverimage", "cover_image")

/** Bounded embedded cover lookup; no chapter rendering or whole-package extraction. */
object EpubCover {
    internal data class Cover(
        val path: String,
        val bytes: ByteArray,
    )

    fun decode(
        epub: File,
        targetEdge: Int,
    ): Bitmap? =
        read(epub)?.let {
            ByteArrayInputStream(it.bytes).use { input -> ImageDecoder.decodeStream(input, targetEdge) }
        }

    /** Compatibility entry for the library's existing extracted-cover workflow. */
    fun extract(
        epub: File,
        targetDirectory: File,
        stem: String,
    ): File? =
        runCatching {
            val cover = read(epub) ?: return null
            require(stem.isNotBlank() && '/' !in stem && '\\' !in stem && stem != "." && stem != "..")
            if (!targetDirectory.isDirectory && !targetDirectory.mkdirs()) return null
            val target = File(targetDirectory, "$stem.${cover.path.substringAfterLast('.').lowercase()}")
            target.writeBytes(cover.bytes)
            target
        }.getOrNull()

    internal fun read(epub: File): Cover? =
        runCatching {
            if (!epub.isFile || epub.length() == 0L) return null
            ZipFile(epub).use { zip -> CoverLookup(zip).read() }
        }.getOrNull()

    internal fun boundedRead(
        input: InputStream,
        limit: Int,
    ): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > limit) return null
            output.write(buffer, 0, count)
        }
    }

    internal fun resolve(
        base: String,
        href: String,
    ): String? =
        runCatching {
            val uri = URI(href.replace(" ", "%20"))
            require(!uri.isAbsolute && uri.rawAuthority == null && !href.startsWith('/'))
            val path = URLDecoder.decode(uri.rawPath.orEmpty().replace("+", "%2B"), "UTF-8")
            require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path && '\u0000' !in path)
            val segments = mutableListOf<String>()
            val joined = base.substringBeforeLast('/', "").let { if (it.isEmpty()) path else "$it/$path" }
            for (segment in joined.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> {
                        require(segments.isNotEmpty())
                        segments.removeAt(segments.lastIndex)
                    }
                    else -> segments += segment
                }
            }
            require(segments.isNotEmpty())
            segments.joinToString("/")
        }.getOrNull()
}

/** A lookup owns the open archive only for the lifetime of one cover request. */
private class CoverLookup(
    private val zip: ZipFile,
) {
    fun read(): EpubCover.Cover? {
        val container = xml(zip, "META-INF/container.xml") ?: return null
        val root = tags(container, "rootfile").firstOrNull()?.getAttribute("full-path")
        val opfPath = root?.let { EpubCover.resolve("container.xml", it) }
        val opf = opfPath?.let { xml(zip, it) }
        return if (opfPath == null || opf == null) {
            null
        } else {
            candidates(opf).firstNotNullOfOrNull { href ->
                EpubCover.resolve(opfPath, href)?.let { image(zip, it) }
            } ?: tags(opf, "reference")
                .filter { it.getAttribute("type") == "cover" }
                .firstNotNullOfOrNull { reference -> guide(opfPath, reference) }
        }
    }

    private fun candidates(opf: Element): Set<String> {
        val items = tags(opf, "item").filter { isImage(it) }
        val coverId =
            tags(opf, "meta")
                .firstOrNull {
                    it.getAttribute("name") == "cover" || it.getAttribute("property") == "cover"
                }?.getAttribute("content")
        val ordered = linkedSetOf<String>()
        items.firstOrNull { it.getAttribute("id") == coverId }?.let { ordered += it.getAttribute("href") }
        items
            .filter { "cover-image" in it.getAttribute("properties").split(Regex("\\s+")) }
            .forEach { ordered += it.getAttribute("href") }
        items
            .filter { it.getAttribute("id").lowercase() in coverIds }
            .forEach { ordered += it.getAttribute("href") }
        items
            .filter { "cover" in it.getAttribute("href").lowercase() }
            .forEach { ordered += it.getAttribute("href") }
        return ordered
    }

    private fun guide(
        opfPath: String,
        reference: Element,
    ): EpubCover.Cover? {
        val path = EpubCover.resolve(opfPath, reference.getAttribute("href")) ?: return null
        return image(zip, path) ?: xml(zip, path)?.let { page ->
            tags(page, "img").firstNotNullOfOrNull { img ->
                EpubCover.resolve(path, img.getAttribute("src"))?.let { image(zip, it) }
            }
        }
    }

    private fun isImage(item: Element): Boolean =
        item.getAttribute("media-type").startsWith("image/") ||
            item
                .getAttribute("href")
                .substringBefore('#')
                .substringBefore('?')
                .substringAfterLast('.')
                .lowercase() in
            extensions

    private fun image(
        zip: ZipFile,
        path: String,
    ): EpubCover.Cover? {
        if (path.substringAfterLast('.').lowercase() !in extensions) return null
        return entry(zip, path, MAX_IMAGE_BYTES)?.takeIf { it.isNotEmpty() }?.let { EpubCover.Cover(path, it) }
    }

    private fun entry(
        zip: ZipFile,
        path: String,
        limit: Int,
    ): ByteArray? {
        val entry = zip.getEntry(path) ?: return null
        return if (entry.isDirectory || entry.size <= 0L || entry.size > limit) {
            null
        } else {
            zip.getInputStream(entry).use { EpubCover.boundedRead(it, limit) }
        }
    }

    private fun xml(
        zip: ZipFile,
        path: String,
    ): Element? =
        runCatching {
            val text = entry(zip, path, MAX_XML_BYTES)?.toString(Charsets.UTF_8) ?: return null
            if (text.contains("<!ENTITY", true)) return null
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    isExpandEntityReferences = false
                }
            factory
                .newDocumentBuilder()
                .apply {
                    setEntityResolver { _, _ -> InputSource(StringReader("")) }
                }.parse(InputSource(StringReader(text)))
                .documentElement
        }.getOrNull()

    private fun tags(
        root: Element,
        name: String,
    ): List<Element> {
        val nodes = root.getElementsByTagNameNS("*", name)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }
}
