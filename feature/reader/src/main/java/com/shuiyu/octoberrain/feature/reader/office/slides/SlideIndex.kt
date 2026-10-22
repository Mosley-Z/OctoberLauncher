package com.shuiyu.octoberrain.feature.reader.office.slides

import com.shuiyu.octoberrain.feature.reader.archive.ZipArchive
import com.shuiyu.octoberrain.feature.reader.web.ResourcePaths
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/** Bounded OOXML index, in presentation order; only plain text is retained between slides. */
internal class SlideIndex(
    private val zip: ZipArchive,
) {
    val paths: List<String>
    val texts: List<String>
    val warnings: List<Boolean>

    init {
        val rels = relationships("ppt/presentation.xml")
        val presentation = xml("ppt/presentation.xml").children().single { local(it.tagName()) == "presentation" }
        val slideList = requireNotNull(presentation.children().firstOrNull { local(it.tagName()) == "sldIdLst" })
        paths =
            slideList.children().filter { local(it.tagName()) == "sldId" }.map {
                val id =
                    it
                        .attributes()
                        .firstOrNull { attribute ->
                            local(attribute.key) == "id" &&
                                attribute.key.contains(':')
                        }?.value
                requireNotNull(id?.let { rels[it] })
            }
        require(paths.isNotEmpty() && paths.size <= 1000 && paths.distinct().size == paths.size)
        var total = 0
        val notes = mutableListOf<Boolean>()
        texts =
            paths.map { path ->
                val document = xml(path)
                notes.add(document.getAllElements().any { local(it.tagName()) in UNSUPPORTED })
                val text = extractText(document)
                total += text.length
                require(total <= 2_000_000)
                text
            }
        warnings = notes
    }

    fun xml(path: String): Document =
        parse(
            zip.open(path, MAX_XML.toLong()).use {
                it.readBytes().toString(Charsets.UTF_8)
            },
        )

    fun relationships(path: String): Map<String, String> {
        val relPath = relPath(path)
        if (zip.items.none { it.path == relPath }) return emptyMap()
        return xml(relPath)
            .getAllElements()
            .filter {
                local(it.tagName()) == "Relationship" && it.attr("TargetMode") != "External"
            }.mapNotNull { item -> resolve(path, item.attr("Target"))?.let { item.attr("Id") to it } }
            .toMap()
    }

    companion object {
        const val MAX_XML = 2 * 1024 * 1024
        private val UNSUPPORTED = setOf("chart", "oleObj", "videoFile", "audioFile", "relIds", "contentPart")

        /** Formatting runs belong to one logical paragraph; retain orphan text as separate lines. */
        fun extractText(document: Document): String {
            val output = StringBuilder()
            var paragraph: Element? = null
            for (node in document.getAllElements()) {
                if (local(node.tagName()) != "t") continue
                val owner = node.parents().firstOrNull { local(it.tagName()) == "p" } ?: node
                if (owner !== paragraph && output.isNotEmpty()) output.append('\n')
                output.append(node.wholeText())
                paragraph = owner
            }
            return output.toString()
        }

        fun local(name: String): String = name.substringAfterLast(':')

        fun relPath(path: String): String =
            path.substringBeforeLast('/', "") + "/_rels/" + path.substringAfterLast('/') + ".rels"

        fun resolve(
            owner: String,
            target: String,
        ): String? = ResourcePaths.relative(owner.substringBeforeLast('/', "") + "/" + target)

        fun parse(xml: String): Document {
            require(xml.toByteArray(Charsets.UTF_8).size <= MAX_XML && xml.count { it == '<' } <= 50000)
            require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true))
            return Jsoup.parse(xml, "", Parser.xmlParser()).apply { outputSettings().prettyPrint(false) }
        }
    }
}
