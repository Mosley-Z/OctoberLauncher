package com.shuiyu.octoberrain.feature.reader.source

import com.shuiyu.octoberrain.feature.reader.archive.ArchivePaths
import com.shuiyu.octoberrain.feature.reader.archive.ZipPreflight
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** ZIP 容器内部的格式标记，由 [ZipContainerProbe] 在预检与条目枚举成功后给出。 */
internal enum class ZipContainer {
    EPUB,
    DOCX,
    SHEET,
    SLIDES,
    ZIP,
}

/**
 * 依据 ZIP 中央目录中的条目名鉴别容器格式。
 *
 * 判定前先调用 [ZipPreflight.check] 约束中央目录体积与条目总数，随后只枚举条目名并按固定顺序匹配。
 * mimetype 条目读取不超过 [MIMETYPE_PREFIX_BYTES] 字节，其余条目内容不展开。
 * 预检失败、条目数超预算、条目名无法归一或读取过程中出现异常时返回 null，调用方据此回退到扩展名判定。
 * 判定不参考文件扩展名，也不校验 mimetype 是否为首个条目或采用 STORED 方式存储。
 */
internal object ZipContainerProbe {
    /** mimetype 条目的读取上限，EPUB 固定取值远小于该长度。 */
    private const val MIMETYPE_PREFIX_BYTES = 64
    private const val EPUB_MIMETYPE = "application/epub+zip"
    private const val EPUB_CONTAINER = "META-INF/container.xml"
    private const val MIMETYPE = "mimetype"
    private const val CONTENT_TYPES = "[Content_Types].xml"
    private const val DOCX_DOCUMENT = "word/document.xml"
    private const val DOCX_DIRECTORY = "word"
    private const val SHEET_WORKBOOK = "xl/workbook.xml"
    private const val SLIDES_PRESENTATION = "ppt/presentation.xml"

    /**
     * 鉴别 ZIP 容器格式。
     *
     * @param file 待检查的本地文件，调用方应已确认为 ZIP 文件头
     * @return 识别出的容器格式；无法解析、损坏、超预算或读取失败时返回 null，不抛出异常
     */
    fun probe(file: File): ZipContainer? =
        try {
            ZipPreflight.check(file)
            ZipFile(file, Charsets.UTF_8).use(::classify)
        } catch (_: ZipException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    /**
     * 枚举条目并给出容器格式。
     *
     * @param zip 已通过预检的 ZIP 文件
     * @return 按 EPUB、DOCX、SHEET、SLIDES、ZIP 的优先级得到的判定结果
     */
    private fun classify(zip: ZipFile): ZipContainer {
        val paths = entryPaths(zip)
        val epubMimetype = paths[MIMETYPE]?.takeIf { !it.isDirectory }?.let { isEpubMimetype(zip, it) } ?: false
        val wordPart = paths.keys.any { it == DOCX_DIRECTORY || it.startsWith("$DOCX_DIRECTORY/") }
        return when {
            epubMimetype || EPUB_CONTAINER in paths -> ZipContainer.EPUB
            DOCX_DOCUMENT in paths || (CONTENT_TYPES in paths && wordPart) -> ZipContainer.DOCX
            SHEET_WORKBOOK in paths -> ZipContainer.SHEET
            SLIDES_PRESENTATION in paths -> ZipContainer.SLIDES
            else -> ZipContainer.ZIP
        }
    }

    /**
     * 枚举全部条目名并归一为容器内路径，同时约束条目总数。
     *
     * @param zip 已通过预检的 ZIP 文件
     * @return 归一化路径到条目的映射；同名条目保留最后出现的一个
     */
    private fun entryPaths(zip: ZipFile): Map<String, ZipEntry> {
        val paths = LinkedHashMap<String, ZipEntry>()
        val entries = zip.entries()
        var count = 0
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            count += 1
            if (count > ZipPreflight.MAX_ENTRIES) throw ZipException("ZIP index budget exceeded")
            val path = ArchivePaths.normalize(entry.name)
            if (path.isNotEmpty()) paths[path] = entry
        }
        return paths
    }

    /**
     * 读取 mimetype 条目的有限前缀并与 EPUB 固定取值比对。
     *
     * @param zip 已打开的 ZIP 文件
     * @param entry mimetype 条目
     * @return 去掉首尾空白后的前缀内容等于 `application/epub+zip` 时返回 true
     */
    private fun isEpubMimetype(
        zip: ZipFile,
        entry: ZipEntry,
    ): Boolean =
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(MIMETYPE_PREFIX_BYTES)
            var size = 0
            while (size < buffer.size) {
                val read = input.read(buffer, size, buffer.size - size)
                if (read < 0) break
                size += read
            }
            buffer.copyOf(size).toString(Charsets.ISO_8859_1).trim() == EPUB_MIMETYPE
        }
}
