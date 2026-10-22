package com.shuiyu.octoberrain.feature.reader.office.slides

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Removes unsupported and active objects from the bounded renderer package. */
internal object SlideSanitizer {
    fun clean(
        doc: Document,
        path: String,
        included: Set<String>,
        selected: String,
        links: Map<String, String>,
    ): String {
        when {
            path == "[Content_Types].xml" ->
                doc
                    .getAllElements()
                    .filter {
                        SlideIndex.local(it.tagName()) == "Override" &&
                            it.attr("PartName").removePrefix("/") !in included
                    }.forEach { it.remove() }
            path.endsWith(".rels") -> cleanRelations(doc, path, included)
            else -> cleanContent(doc, path, selected, links)
        }
        return doc.outerHtml()
    }

    private fun cleanRelations(
        doc: Document,
        path: String,
        included: Set<String>,
    ) {
        val owner = path.replace("/_rels/", "/").removeSuffix(".rels")
        doc
            .getAllElements()
            .filter { SlideIndex.local(it.tagName()) == "Relationship" }
            .filter {
                it.attr("TargetMode") == "External" || SlideIndex.resolve(owner, it.attr("Target")) !in included
            }.forEach { it.remove() }
    }

    private fun cleanContent(
        doc: Document,
        path: String,
        selected: String,
        links: Map<String, String>,
    ) {
        if (path == "ppt/presentation.xml") {
            val allowed = links.filterValues { it == selected }.keys
            doc
                .getAllElements()
                .filter { SlideIndex.local(it.tagName()) == "sldId" }
                .filter {
                    it.attributes().none { attribute -> attribute.key.contains(':') && attribute.value in allowed }
                }.forEach { it.remove() }
        }
        doc.getAllElements().filter { unsupported(it, links) }.forEach { it.remove() }
    }

    private fun unsupported(
        node: Element,
        links: Map<String, String>,
    ): Boolean =
        when (SlideIndex.local(node.tagName())) {
            in OMIT -> true
            "graphicFrame" -> node.getAllElements().none { SlideIndex.local(it.tagName()) == "tbl" }
            "pic" ->
                node.getAllElements().filter { SlideIndex.local(it.tagName()) == "blip" }.any {
                    it.attr("r:embed") !in
                        links
                }
            else -> false
        }

    private val OMIT =
        setOf("oleObj", "videoFile", "audioFile", "contentPart", "hlinkClick", "hlinkMouseOver", "timing", "transition")
}
