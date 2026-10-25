package com.shuiyu.octoberrain.feature.reader.source

import com.shuiyu.octoberrain.core.common.ReaderFormat
import java.util.Locale

/** 文件头优先；扩展名和 MIME 只用于文本与容器候选，不能证明引擎已支持。 */
internal object DocumentKinds {
    fun isCsv(
        name: String,
        mime: String?,
    ): Boolean =
        name.substringAfterLast('.', "").equals("csv", ignoreCase = true) ||
            mime?.substringBefore(';')?.lowercase(Locale.ROOT) in setOf("text/csv", "application/csv")

    /**
     * 在缺少容器内部标记时判定格式。
     *
     * @param name 文件名，用于取扩展名
     * @param mime 声明或推断的 MIME
     * @param header 文件头字节，至少包含可判定的魔数
     * @return 判定结果
     */
    fun detect(
        name: String,
        mime: String?,
        header: ByteArray,
    ): ReaderFormat = detect(name, mime, header, null)

    /**
     * 在文件头与扩展名之外接受 ZIP 容器内部标记。
     *
     * [container] 由 [ZipContainerProbe] 在条目枚举成功后给出，因此优先于扩展名；
     * 为 null 时保持图片与 PDF 魔数、ZIP 扩展名回退、文本候选的原判定顺序。
     *
     * @param name 文件名，用于取扩展名
     * @param mime 声明或推断的 MIME
     * @param header 文件头字节，至少包含可判定的魔数
     * @param container ZIP 容器内部标记；非 ZIP 文件或预检失败时为 null
     * @return 判定结果
     */
    fun detect(
        name: String,
        mime: String?,
        header: ByteArray,
        container: ZipContainer?,
    ): ReaderFormat {
        val signature = header.take(5).toByteArray().toString(Charsets.ISO_8859_1)
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val exact =
            when {
                isImage(header) -> ReaderFormat.IMAGE
                signature == "%PDF-" -> ReaderFormat.PDF
                else -> null
            }
        return when {
            container == ZipContainer.ZIP -> zipKind(extension, mime)
            container != null -> containerFormat(container)
            exact != null -> exact
            header.take(8) == listOf(0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1).map { it.toByte() } &&
                extension == "xls" -> ReaderFormat.SHEET
            isZipHeader(header) -> extensionFormat(extension, mime)
            else -> textKind(extension, mime, header)
        }
    }

    /**
     * 判断文件头是否为 ZIP 局部文件头魔数。
     *
     * @param header 文件头字节
     * @return 前四字节以 `PK\x03\x04` 开头时返回 true
     */
    fun isZipHeader(header: ByteArray): Boolean =
        header.size >= ZIP_HEADER_BYTES &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4b.toByte()

    private fun containerFormat(container: ZipContainer): ReaderFormat =
        when (container) {
            ZipContainer.EPUB -> ReaderFormat.EPUB
            ZipContainer.DOCX -> ReaderFormat.DOCX
            ZipContainer.SHEET -> ReaderFormat.SHEET
            ZipContainer.SLIDES -> ReaderFormat.SLIDES
            ZipContainer.ZIP -> ReaderFormat.ZIP
        }

    private fun extensionFormat(
        extension: String,
        mime: String?,
    ): ReaderFormat =
        when (extension) {
            "epub" -> ReaderFormat.EPUB
            "docx" -> ReaderFormat.DOCX
            "xlsx" -> ReaderFormat.SHEET
            "pptx" -> ReaderFormat.SLIDES
            else -> zipKind(extension, mime)
        }

    /** APK由文件页显式选择查看结构；其他普通ZIP容器仅按常见压缩格式进入浏览。 */
    private fun zipKind(
        extension: String,
        mime: String?,
    ): ReaderFormat {
        val declared = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        return if (extension in ARCHIVE_EXTENSIONS || extension == "apk" || declared in ARCHIVE_MIMES) {
            ReaderFormat.ZIP
        } else {
            ReaderFormat.UNKNOWN
        }
    }

    private fun isImage(header: ByteArray): Boolean {
        val hex = header.take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
        return hex.startsWith("89504e470d0a1a0a") ||
            hex.startsWith("ffd8ff") ||
            hex.startsWith("474946383761") ||
            hex.startsWith("474946383961") ||
            (hex.startsWith("52494646") && hex.endsWith("57454250"))
    }

    private fun textKind(
        extension: String,
        mime: String?,
        header: ByteArray,
    ): ReaderFormat {
        val prefix = header.take(2).map { it.toInt() and 0xff }
        val utf16 = prefix == listOf(255, 254) || prefix == listOf(254, 255)
        if (header.any { it == 0.toByte() } && !utf16) return ReaderFormat.UNKNOWN
        return when {
            isCsv("file.$extension", mime) -> ReaderFormat.SHEET
            extension in setOf("md", "markdown") -> ReaderFormat.MARKDOWN
            extension in TEXT_EXTENSIONS || mime?.startsWith("text/") == true -> ReaderFormat.TEXT
            else -> ReaderFormat.UNKNOWN
        }
    }

    private const val ZIP_HEADER_BYTES = 4

    private val ARCHIVE_EXTENSIONS = setOf("zip", "cbz", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
    private val ARCHIVE_MIMES =
        setOf("application/zip", "application/x-zip-compressed", "application/vnd.android.package-archive")

    private val TEXT_EXTENSIONS =
        setOf(
            "txt",
            "log",
            "json",
            "xml",
            "csv",
            "tsv",
            "kt",
            "java",
            "py",
            "js",
            "css",
            "yaml",
            "yml",
            "ini",
            "conf",
            "c",
            "cpp",
            "h",
            "sh",
        )
}
