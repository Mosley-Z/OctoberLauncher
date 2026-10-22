package com.shuiyu.octoberrain.feature.reader.office.docx

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/** 只保留Word正文语义；忽略域指令、嵌入对象与外部关系，不执行宏。 */
internal object DocxBody {
    fun render(
        xml: String,
        relationships: String,
        imageUrl: (String) -> String?,
    ): String {
        val source = parse(xml)
        val links =
            parse(relationships)
                .getAllElements()
                .filter {
                    it.tagName().substringAfterLast(':') == "Relationship" && it.attr("TargetMode") != "External"
                }.associate { it.attr("Id") to it.attr("Target") }
        val body = source.getAllElements().firstOrNull { it.tagName().substringAfterLast(':') == "body" }
        requireNotNull(body)
        val shell = Document.createShell("")
        shell.outputSettings().prettyPrint(false)
        val output = shell.body().appendElement("div")
        val stack = ArrayDeque<Pair<Element, Element>>()
        body.children().reversed().forEach { stack.addLast(it to output) }
        while (stack.isNotEmpty()) {
            val (node, parent) = stack.removeLast()
            val name = node.tagName().substringAfterLast(':')
            when (name) {
                "t" -> parent.appendText(node.wholeText())
                "tab" -> parent.appendText("    ")
                "br", "cr" -> parent.appendElement("br")
                "blip" -> appendImage(node, parent, links, imageUrl)
                "instrText", "del", "pPr", "rPr", "sectPr", "tblPr", "tcPr" -> Unit
                else -> {
                    val tag = mapOf("p" to "p", "tbl" to "table", "tr" to "tr", "tc" to "td")[name]
                    val into = if (tag == null) parent else parent.appendElement(tag)
                    node.children().reversed().forEach { stack.addLast(it to into) }
                }
            }
        }
        return output.html().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_XML_BYTES) }
    }

    private fun appendImage(
        node: Element,
        parent: Element,
        links: Map<String, String>,
        imageUrl: (String) -> String?,
    ) {
        val id = node.attributes().firstOrNull { it.key.substringAfterLast(':') == "embed" }?.value
        val target = id?.let { links[it] }?.let(imageUrl)
        if (target == null) parent.appendText("[图片未显示]") else parent.appendElement("img").attr("src", target)
    }

    private fun parse(xml: String): Document {
        require(xml.toByteArray(Charsets.UTF_8).size <= MAX_XML_BYTES)
        require(xml.count { it == '<' } <= 50000)
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true))
        return Jsoup.parse(xml, "", Parser.xmlParser())
    }

    const val MAX_XML_BYTES = 2 * 1024 * 1024
}
