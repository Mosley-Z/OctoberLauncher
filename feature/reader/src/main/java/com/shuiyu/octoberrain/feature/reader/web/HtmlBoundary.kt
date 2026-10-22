package com.shuiyu.octoberrain.feature.reader.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

/** 文档只有允许的排版与图片，脚本/事件/iframe/原样CSS不会进入可信包装页。 */
internal object HtmlBoundary {
    fun clean(
        html: String,
        imageUrl: (String) -> String?,
    ): String = clean(html, imageUrl) { it }

    fun clean(
        html: String,
        imageUrl: (String) -> String?,
        linkUrl: (String) -> String?,
    ): String {
        require(html.length <= 2 * 1024 * 1024)
        require(html.count { it == '<' } <= 50000)
        val source = Jsoup.parseBodyFragment(html)
        require(source.getAllElements().size <= 50000)
        for (image in source.select("img[src]")) {
            val mapped = imageUrl(image.attr("src"))
            if (mapped == null) image.removeAttr("src") else image.attr("src", mapped)
        }
        for (link in source.select("a[href]")) {
            val mapped = linkUrl(link.attr("href"))
            if (mapped == null) link.removeAttr("href") else link.attr("href", mapped)
        }
        val headings = mutableSetOf<String>()
        for (heading in source.select("h1,h2,h3,h4,h5,h6")) {
            if (!heading.hasAttr("id")) {
                val slug =
                    heading
                        .text()
                        .lowercase(java.util.Locale.ROOT)
                        .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
                        .trim()
                        .replace(Regex("\\s+"), "-")
                        .ifEmpty { "section" }
                var id = slug
                var suffix = 1
                while (!headings.add(id)) id = "$slug-${suffix++}"
                heading.attr("id", id)
            }
        }
        // 本地资源已转为本会话HTTPS地址；禁止文档改变根URL或凭style发起资源请求。
        val rules =
            Safelist
                .relaxed()
                .addTags("del")
                .addAttributes(":all", "id", "class")
                .addProtocols("a", "href", "#")
                .preserveRelativeLinks(true)
        val cleaned = Cleaner(rules).clean(source)
        cleaned.outputSettings(Document.OutputSettings().prettyPrint(false))
        // 不允许原始ID遮蔽包装页的宿主节点；片段链接同步加前缀。
        for (element in cleaned.select("[id]")) element.attr("id", "doc-" + element.id())
        for (link in cleaned.select("a[href]")) {
            val target = link.attr("href")
            if (target.startsWith('#')) {
                link.attr("href", "#doc-" + target.drop(1))
            } else if (!target.startsWith("https://") && !target.startsWith("http://")) {
                link.removeAttr("href")
            }
        }
        val result = cleaned.body().html()
        require(result.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024)
        return result
    }
}
