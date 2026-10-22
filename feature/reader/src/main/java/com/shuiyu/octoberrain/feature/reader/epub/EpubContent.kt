package com.shuiyu.octoberrain.feature.reader.epub

import com.shuiyu.octoberrain.feature.reader.web.HtmlBoundary
import com.shuiyu.octoberrain.feature.reader.web.ResourcePaths
import com.shuiyu.octoberrain.feature.reader.web.WebMessagePolicy
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

internal data class EpubContent(
    val html: String,
    val text: String,
    val anchors: Map<String, Int>,
    val links: Map<String, EpubToc>,
    val styles: String,
) {
    companion object {
        fun build(
            book: EpubPackage,
            spine: Int,
            session: String,
        ): EpubContent {
            val path = book.chapters[spine].path
            val links = linkedMapOf<String, EpubToc>()
            val styled = EpubStyles.prepare(book, path, book.read(path))
            val html =
                HtmlBoundary.clean(
                    styled.body,
                    { source ->
                        EpubPaths.resolve(path, source)?.path?.let {
                            "${WebMessagePolicy.ORIGIN}/session/$session/res/${ResourcePaths.encode(it)}"
                        }
                    },
                    { source ->
                        when {
                            source.startsWith('#') -> source
                            source.startsWith("http://") || source.startsWith("https://") -> source
                            else -> mapLink(book, spine, session, source, links)
                        }
                    },
                )
            val text = StringBuilder()
            val anchors = linkedMapOf<String, Int>()
            NodeTraversor.traverse(
                object : NodeVisitor {
                    override fun head(
                        node: Node,
                        depth: Int,
                    ) {
                        if (node is Element && node.id().startsWith("doc-")) {
                            anchors.putIfAbsent(node.id().removePrefix("doc-"), text.length)
                        }
                        if (node is TextNode) text.append(node.wholeText)
                    }

                    override fun tail(
                        node: Node,
                        depth: Int,
                    ) = Unit
                },
                Jsoup.parseBodyFragment(html).body(),
            )
            return EpubContent(html, text.toString(), anchors, links, styled.css)
        }

        private fun mapLink(
            book: EpubPackage,
            spine: Int,
            session: String,
            source: String,
            links: MutableMap<String, EpubToc>,
        ): String? =
            run {
                val target = EpubPaths.resolve(book.chapters[spine].path, source) ?: return@run null
                val index = book.chapters.indexOfFirst { it.path == target.path }
                if (index < 0) return@run null
                if (index == spine && target.fragment.isNotEmpty()) return@run "#${target.fragment}"
                val url = "${WebMessagePolicy.ORIGIN}/epub/$session/link/${links.size}"
                links[url] = EpubToc(book.chapters[index].title, index, target.fragment)
                url
            }
    }
}
