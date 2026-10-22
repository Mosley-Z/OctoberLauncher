package com.shuiyu.octoberrain.feature.reader.epub

import org.jsoup.Jsoup

internal data class StyledChapter(
    val body: String,
    val css: String,
)

/** A small reading-style subset. No imports, URLs, positioning, colors, fonts or arbitrary selectors. */
internal object EpubStyles {
    fun prepare(
        book: EpubPackage,
        path: String,
        html: String,
    ): StyledChapter {
        require(html.length <= 2 * 1024 * 1024 && html.count { it == '<' } <= 50000)
        val document = Jsoup.parse(html)
        document.outputSettings().prettyPrint(false)
        val css = StringBuilder()
        var count = 0
        for (style in document.select("style")) {
            if (count++ < 16) append(css, filter(style.data()))
        }
        for (link in document.select("link[rel=stylesheet][href]").take((16 - count).coerceAtLeast(0))) {
            val target = EpubPaths.resolve(path, link.attr("href")) ?: continue
            val sheet =
                try {
                    book.read(target.path)
                } catch (_: java.io.IOException) {
                    ""
                }
            append(css, filter(sheet))
        }
        document.select("[style]").take(1000).forEachIndexed { index, element ->
            val declarations = declarations(element.attr("style"))
            if (declarations.isNotEmpty()) {
                val name = "reader-epub-inline-$index"
                element.addClass(name)
                append(css, "#reader-content .$name{$declarations}")
            }
        }
        return StyledChapter(document.body().html(), css.toString())
    }

    fun filter(source: String): String {
        if (source.length > 256 * 1024) return ""
        val css = source.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        val output = StringBuilder()
        for (rule in css.split('}').take(2048).filter { rule -> rule.count { it == '{' } == 1 }) {
            val selectors = rule.substringBefore('{').split(',').take(16)
            val properties = declarations(rule.substringAfter('{'))
            if (properties.isEmpty()) continue
            for (selector in selectors) {
                val mapped = selector.trim().takeIf { it.length <= 128 && SELECTOR.matches(it) } ?: continue
                val scoped = mapped.replace(Regex("#([\\p{L}\\p{N}_-]+)"), "#doc-$1")
                append(output, "#reader-content $scoped{$properties}")
            }
        }
        return output.toString()
    }

    private fun append(
        output: StringBuilder,
        css: String,
    ) {
        if (output.length + css.length <= 128 * 1024) output.append(css)
    }

    private fun declarations(source: String): String =
        source
            .split(';')
            .take(32)
            .mapNotNull { pair ->
                val property = pair.substringBefore(':').trim().lowercase()
                val value = pair.substringAfter(':', "").trim().lowercase()
                val rule = VALUES[property] ?: return@mapNotNull null
                if (rule.matches(value)) "$property:$value" else null
            }.joinToString(";")

    private val SELECTOR = Regex("[\\p{L}._#][\\p{L}\\p{N}._#-]*(?: +[\\p{L}._#][\\p{L}\\p{N}._#-]*){0,3}")
    private val VALUES =
        mapOf(
            "font-weight" to Regex("normal|bold|bolder|lighter|[1-9]00"),
            "font-style" to Regex("normal|italic|oblique"),
            "text-align" to Regex("left|right|center|justify|start|end"),
            "text-decoration" to Regex("none|underline|line-through"),
            "white-space" to Regex("normal|pre-wrap|pre-line"),
            "text-indent" to Regex("0|[0-4](?:\\.[0-9])?em"),
            "line-height" to Regex("1(?:\\.[0-9])?|2(?:\\.[0-5])?"),
            "margin-top" to Regex("0|[0-2](?:\\.[0-9])?em"),
            "margin-bottom" to Regex("0|[0-2](?:\\.[0-9])?em"),
        )
}
