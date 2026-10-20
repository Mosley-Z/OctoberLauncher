package com.shuiyu.octoberrain.feature.content.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * EPUB 封面提取。
 *
 * 解析流程按 EPUB 规范执行：先读 META-INF/container.xml 找到 OPF 清单文件，
 * 再在 OPF 中按优先级挑选封面资源，最后把图片从压缩包解出到目标目录。
 * 全程只读取数据，不依赖第三方 EPUB 库。
 */
object EpubCover {
    private const val MAX_XML_BYTES = 512 * 1024
    private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val COVER_IDS = setOf("cover", "cover-image", "coverimage", "cover_image")

    /** OPF 中的一个清单项。 */
    private class OpfItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String,
    )

    /** OPF 中与封面相关的聚合信息。 */
    private class OpfData {
        var coverId: String? = null
        var guideCoverHref: String? = null
        val items = mutableListOf<OpfItem>()
    }

    /**
     * 从 EPUB 中提取封面并写入 [targetDirectory]，文件名由 [stem] 决定。
     * 无法确定封面、压缩包损坏或超出体积限制时返回 null。
     */
    fun extract(
        epub: File,
        targetDirectory: File,
        stem: String,
    ): File? {
        if (!epub.isFile || epub.length() <= 0L) return null
        return runCatching {
            ZipFile(epub).use { zip ->
                val opfPath = readOpfPath(zip) ?: return@use null
                val opfText = readEntryText(zip, opfPath) ?: return@use null
                val data = parseOpf(opfText)
                val href = chooseCoverHref(data) ?: return@use null
                val entryPath = resolvePath(opfPath, href) ?: return@use null
                val entry = zip.getEntry(entryPath) ?: return@use null
                if (entry.isDirectory) return@use null
                val extension = entryPath.substringAfterLast('.', "").lowercase()
                if (extension !in IMAGE_EXTENSIONS) return@use null
                if (entry.size <= 0L || entry.size > MAX_IMAGE_BYTES) return@use null
                if (!targetDirectory.exists()) targetDirectory.mkdirs()
                val target = File(targetDirectory, "$stem.$extension")
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                }
                if (target.length() <= 0L) {
                    target.delete()
                    null
                } else {
                    target
                }
            }
        }.getOrElse { null }
    }

    /** 从 container.xml 读取根清单文件路径。 */
    private fun readOpfPath(zip: ZipFile): String? {
        val container = readEntryText(zip, "META-INF/container.xml") ?: return null
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(container))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val path = parser.getAttributeValue(null, "full-path")
                if (!path.isNullOrBlank()) return path
            }
            event = parser.next()
        }
        return null
    }

    /** 读取压缩包内文本条目，超出上限时返回 null。 */
    private fun readEntryText(
        zip: ZipFile,
        path: String,
    ): String? {
        val entry = zip.getEntry(path) ?: return null
        if (entry.isDirectory || entry.size <= 0L || entry.size > MAX_XML_BYTES) return null
        return zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(entry.size.toInt().coerceAtLeast(1024))
            val output = java.io.ByteArrayOutputStream(entry.size.toInt())
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                if (output.size() > MAX_XML_BYTES) return null
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    /** 解析 OPF 中的 meta、manifest 与 guide 节点。 */
    private fun parseOpf(text: String): OpfData {
        val data = OpfData()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(text))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "meta" -> {
                        val name = parser.getAttributeValue(null, "name")
                        val property = parser.getAttributeValue(null, "property")
                        val content = parser.getAttributeValue(null, "content")
                        if ((name == "cover" || property == "cover") && !content.isNullOrBlank()) {
                            data.coverId = content
                        }
                    }

                    "item" -> {
                        val id = parser.getAttributeValue(null, "id").orEmpty()
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                        val properties = parser.getAttributeValue(null, "properties").orEmpty()
                        if (href.isNotBlank()) data.items.add(OpfItem(id, href, mediaType, properties))
                    }

                    "reference" -> {
                        val type = parser.getAttributeValue(null, "type")
                        val href = parser.getAttributeValue(null, "href")
                        if (type == "cover" && !href.isNullOrBlank()) data.guideCoverHref = href
                    }
                }
            }
            event = parser.next()
        }
        return data
    }

    /** 按契约优先级挑选封面条目，全部命中失败时返回 null。 */
    private fun chooseCoverHref(data: OpfData): String? {
        data.coverId?.let { coverId ->
            data.items
                .firstOrNull { it.id == coverId }
                ?.takeIf { isImage(it) }
                ?.let { return it.href }
        }
        data.items
            .firstOrNull { item -> item.properties.split(' ').any { it == "cover-image" } }
            ?.let { return it.href }
        data.items.firstOrNull { it.id.lowercase() in COVER_IDS }?.let { return it.href }
        data.items
            .firstOrNull { it.href.lowercase().contains("cover") && isImage(it) }
            ?.let { return it.href }
        data.guideCoverHref?.let { return it }
        return null
    }

    /** 判断清单项是否为图片资源。 */
    private fun isImage(item: OpfItem): Boolean =
        item.mediaType.startsWith("image/") ||
            item.href.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /** 把 OPF 内的相对引用规范化为压缩包内绝对路径。 */
    private fun resolvePath(
        opfPath: String,
        href: String,
    ): String? {
        val decoded = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
        val clean = decoded.substringBefore('#')
        if (clean.isBlank()) return null
        val base = opfPath.substringBeforeLast('/', "")
        val merged = if (base.isEmpty()) clean else "$base/$clean"
        val segments = mutableListOf<String>()
        for (segment in merged.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
                else -> segments.add(segment)
            }
        }
        return if (segments.isEmpty()) null else segments.joinToString("/")
    }
}
