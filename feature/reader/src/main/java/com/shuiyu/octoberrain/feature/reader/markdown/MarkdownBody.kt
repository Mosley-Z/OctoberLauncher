package com.shuiyu.octoberrain.feature.reader.markdown

import com.shuiyu.octoberrain.feature.reader.web.HtmlBoundary
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/** JVM解析，不依赖新版marked；限制输入/节点/深度后才生成离线HTML。 */
internal object MarkdownBody {
    private val extensions = listOf(TablesExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer =
        HtmlRenderer
            .builder()
            .extensions(extensions)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build()

    fun render(
        source: String,
        imageUrl: (String) -> String?,
    ): String {
        require(source.length <= 1024 * 1024)
        require(source.lineSequence().take(20001).count() <= 20000)
        require(source.lineSequence().all { it.length <= 8192 })
        val document = parser.parse(source)
        val pending = ArrayDeque<Pair<Node, Int>>()
        pending.addLast(document to 0)
        var nodes = 0
        while (pending.isNotEmpty()) {
            val (node, depth) = pending.removeLast()
            require(++nodes <= 50000 && depth <= 128)
            var child = node.firstChild
            while (child != null) {
                pending.addLast(child to depth + 1)
                child = child.next
            }
        }
        return HtmlBoundary.clean(renderer.render(document), imageUrl)
    }
}
