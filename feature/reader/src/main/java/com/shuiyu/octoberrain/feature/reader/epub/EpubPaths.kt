package com.shuiyu.octoberrain.feature.reader.epub

import com.shuiyu.octoberrain.feature.reader.archive.ArchivePaths
import java.net.URI
import java.net.URLDecoder

internal data class EpubTarget(
    val path: String,
    val fragment: String = "",
)

internal object EpubPaths {
    fun resolve(
        base: String,
        href: String,
    ): EpubTarget? =
        runCatching {
            val uri = URI(href.replace(" ", "%20"))
            require(!uri.isAbsolute && uri.rawAuthority == null && !href.startsWith('/'))
            val path = uri.rawPath.orEmpty().let { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
            val target = if (path.isEmpty()) base else base.substringBeforeLast('/', "") + "/" + path
            val normalized = ArchivePaths.normalize(target.removePrefix("/"))
            require(normalized.isNotEmpty())
            EpubTarget(normalized, uri.fragment.orEmpty())
        }.getOrNull()
}
