package com.shuiyu.octoberrain.feature.reader.web

/** 固定资源白名单，不从文档或消息接受脚本路径。 */
internal object OfficeWebAssets {
    private val sheets = listOf("sheets/xlsx.full.min.js", "sheets/sheets-host.js")
    private val slides =
        listOf(
            "slides/jquery.min.js",
            "slides/jszip2.min.js",
            "slides/d3.min.js",
            "slides/nv.d3.min.js",
            "slides/filereader.js",
            "slides/divs2slides.js",
            "slides/pptxjs.js",
            "slides/slides-host.js",
        )
    private val styles = listOf("slides/pptxjs.css", "slides/nv.d3.min.css")
    private val docx = listOf("jszip3.min.js", "docx-preview.min.js", "search-host.js", "docx-host.js")

    fun allows(
        doc: WebDocument,
        path: String,
    ): Boolean = path in scripts(doc) || (doc.office == OfficeWebMode.SLIDES && path in styles)

    fun markup(doc: WebDocument): String {
        val css =
            if (doc.office == OfficeWebMode.SLIDES) {
                styles.joinToString("") { "<link rel=\"stylesheet\" href=\"/static/$it\">" }
            } else {
                ""
            }
        return css + scripts(doc).joinToString("") { "<script src=\"/static/$it\"></script>" }
    }

    private fun scripts(doc: WebDocument): List<String> =
        when {
            doc.office == OfficeWebMode.SHEETS -> sheets
            doc.office == OfficeWebMode.SLIDES -> slides
            doc.docxPreview -> docx
            else -> listOf("search-host.js", "host.js")
        }
}
