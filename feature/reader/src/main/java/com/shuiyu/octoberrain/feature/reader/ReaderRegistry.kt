package com.shuiyu.octoberrain.feature.reader

import android.content.Context
import android.os.Build
import com.shuiyu.octoberrain.core.common.DocumentRef
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.core.common.ReaderFormat
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.archive.ArchiveEngine
import com.shuiyu.octoberrain.feature.reader.epub.EpubEngine
import com.shuiyu.octoberrain.feature.reader.image.ImageEngine
import com.shuiyu.octoberrain.feature.reader.markdown.MarkdownEngine
import com.shuiyu.octoberrain.feature.reader.office.docx.DocxEngine
import com.shuiyu.octoberrain.feature.reader.pdf.PdfEngine
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import com.shuiyu.octoberrain.feature.reader.text.TextEngine

/** 集成者拥有的格式分发点；只有真实可创建的引擎进入此表。 */
internal object ReaderRegistry {
    @Suppress("CyclomaticComplexMethod") // Explicit central format factory; engines remain independent.
    suspend fun create(
        context: Context,
        ref: DocumentRef,
        locator: ReaderLocator?,
        format: ReaderFormat,
        source: DocumentHandle,
        width: Int,
        height: Int,
        fontSp: Int,
        charset: String?,
    ): ReaderEngine =
        when (format) {
            ReaderFormat.DOCX -> DocxEngine.create(source, locator, fontSp)
            ReaderFormat.EPUB -> EpubEngine.create(source, fontSp)
            ReaderFormat.IMAGE -> ImageEngine(source, width, height)
            ReaderFormat.ZIP -> {
                if (ref is DocumentRef.ArchiveEntry) throw ReaderSourceException(ReaderFailure.UNSUPPORTED)
                ArchiveEngine(source, charset ?: (locator as? ReaderLocator.Archive)?.charset ?: "UTF-8")
            }
            ReaderFormat.MARKDOWN ->
                if (charset == null && locator !is ReaderLocator.Text) {
                    MarkdownEngine.create(context, ref, source, fontSp)
                } else {
                    TextEngine(source, width, height, fontSp * context.resources.displayMetrics.scaledDensity, charset)
                }
            ReaderFormat.PDF -> {
                if (Build.VERSION.SDK_INT < 27) throw ReaderSourceException(ReaderFailure.UNSUPPORTED)
                PdfEngine.create(context, source, width, height)
            }
            ReaderFormat.TEXT, ReaderFormat.UNKNOWN -> {
                if (needsFormatChoice(format, charset, locator)) {
                    throw ReaderSourceException(ReaderFailure.UNSUPPORTED)
                }
                TextEngine(source, width, height, fontSp * context.resources.displayMetrics.scaledDensity, charset)
            }
            else -> throw ReaderSourceException(ReaderFailure.UNSUPPORTED)
        }

    private fun needsFormatChoice(
        format: ReaderFormat,
        charset: String?,
        locator: ReaderLocator?,
    ): Boolean = format == ReaderFormat.UNKNOWN && charset == null && locator !is ReaderLocator.Text
}
