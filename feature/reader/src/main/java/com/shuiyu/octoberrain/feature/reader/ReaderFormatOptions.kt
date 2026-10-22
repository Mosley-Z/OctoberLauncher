package com.shuiyu.octoberrain.feature.reader

import android.app.AlertDialog
import android.content.Context
import com.shuiyu.octoberrain.core.common.ReaderFormat
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.epub.EpubToc
import com.shuiyu.octoberrain.feature.reader.pdf.PdfAdjustment

/** 格式菜单只收集操作，文档与动作队列始终由阅读宿主持有。 */
internal class ReaderFormatOptions(
    private val context: Context,
) {
    fun add(
        actions: MutableList<Pair<String, () -> Unit>>,
        format: ReaderFormat,
        locator: ReaderLocator?,
        session: ReaderSession?,
        reopen: (ReaderLocator?) -> Unit,
        epub: (EpubToc) -> Unit,
        archiveEncoding: (String) -> Unit,
        pdf: (PdfAdjustment) -> Unit,
    ) {
        if (format == ReaderFormat.ZIP) {
            listOf("UTF-8", "GB18030").forEach { value ->
                actions += "文件名编码 $value" to { archiveEncoding(value) }
            }
        }
        if (format == ReaderFormat.EPUB) {
            actions += "章节目录" to {
                val toc = session?.epubToc().orEmpty()
                AlertDialog
                    .Builder(context)
                    .setTitle("章节目录")
                    .setItems(toc.map { it.title }.toTypedArray()) { _, index -> epub(toc[index]) }
                    .show()
            }
        }
        if (format == ReaderFormat.DOCX) {
            val current = locator as? ReaderLocator.Docx ?: ReaderLocator.Docx()
            actions += (if (current.preview) "正文阅读" else "原版式预览") to {
                reopen(current.copy(preview = !current.preview, page = -1))
            }
        }
        if (session?.capabilities?.zoom == true) addPdf(actions, pdf)
    }

    private fun addPdf(
        actions: MutableList<Pair<String, () -> Unit>>,
        adjust: (PdfAdjustment) -> Unit,
    ) {
        listOf(
            "放大" to PdfAdjustment.ZOOM_IN,
            "缩小" to PdfAdjustment.ZOOM_OUT,
            "视口左移" to PdfAdjustment.LEFT,
            "视口右移" to PdfAdjustment.RIGHT,
            "视口上移" to PdfAdjustment.UP,
            "视口下移" to PdfAdjustment.DOWN,
            "切换裁边5%" to PdfAdjustment.CROP,
            "适合整页" to PdfAdjustment.FIT,
        ).forEach { (label, mode) -> actions += label to { adjust(mode) } }
    }
}
