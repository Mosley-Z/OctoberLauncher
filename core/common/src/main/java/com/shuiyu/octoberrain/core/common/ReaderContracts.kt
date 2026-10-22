package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 阅读器来源契约v1；只携带来源描述，不携带流、正文或位图。 */
@Serializable
sealed interface DocumentRef {
    @Serializable
    @SerialName("path")
    data class LocalPath(
        val path: String,
    ) : DocumentRef

    @Serializable
    @SerialName("uri")
    data class ContentUri(
        val uri: String,
    ) : DocumentRef

    @Serializable
    @SerialName("archive")
    data class ArchiveEntry(
        val archive: DocumentRef,
        val entry: String,
        val charset: String = "UTF-8",
    ) : DocumentRef
}

/** 未知长度、修改时间保持null，不用0冒充可校验版本。 */
data class DocumentMetadata(
    val id: String,
    val name: String,
    val mime: String?,
    val size: Long?,
    val modified: Long?,
    val persistentAccess: Boolean,
)

@Serializable
sealed interface ReaderLocator {
    @Serializable
    @SerialName("epub")
    data class Epub(
        val spine: Int = 0,
        val textOffset: Int = 0,
        val fraction: Double = 0.0,
    ) : ReaderLocator

    @Serializable
    @SerialName("image")
    data class Image(
        val frame: Int = 0,
    ) : ReaderLocator

    @Serializable
    @SerialName("zip")
    data class Archive(
        val directory: String = "",
        val page: Int = 0,
        val charset: String = "UTF-8",
    ) : ReaderLocator

    @Serializable
    @SerialName("pdf")
    data class Pdf(
        val page: Int = 0,
        val zoom: Float = 1f,
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        val crop: Boolean = false,
    ) : ReaderLocator

    @Serializable
    @SerialName("markdown")
    data class Markdown(
        val textOffset: Int = 0,
        val fraction: Double = 0.0,
    ) : ReaderLocator

    @Serializable
    @SerialName("text")
    data class Text(
        val byteOffset: Long = 0,
        val charset: String = "UTF-8",
    ) : ReaderLocator
}

/** 能力只由实际引擎声明，未实现的功能不会出现在工具栏。 */
data class ReaderCapabilities(
    val search: Boolean = false,
    val selectText: Boolean = false,
    val reflow: Boolean = false,
    val pageJump: Boolean = false,
    val zoom: Boolean = false,
    val bookmark: Boolean = false,
)

enum class ReaderFormat { IMAGE, PDF, TEXT, MARKDOWN, EPUB, ZIP, DOCX, SHEET, SLIDES, UNKNOWN }

enum class ReaderFailure { MISSING, PERMISSION, UNSUPPORTED, CORRUPT, LIMIT, RENDERER, PASSWORD }

/** 主线程打开入口；false表示宿主或模块尚未就绪。 */
fun interface ReaderGateway {
    fun open(ref: DocumentRef): Boolean
}
