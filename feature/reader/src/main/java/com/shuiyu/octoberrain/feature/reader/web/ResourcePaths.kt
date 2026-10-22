package com.shuiyu.octoberrain.feature.reader.web

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** 仅当前文档根以内的相对路径；编码后的越界和Windows分隔符同样受约束。 */
internal object ResourcePaths {
    fun relative(value: String): String? =
        runCatching {
            val uri = URI(value.replace(" ", "%20"))
            if (uri.isAbsolute || uri.rawAuthority != null || value.startsWith('/')) return@runCatching null
            val decoded = URLDecoder.decode(uri.rawPath.replace("+", "%2B"), "UTF-8").replace('\\', '/')
            if (decoded.contains('\u0000') || decoded.contains(':') || decoded.startsWith('/')) return@runCatching null
            val parts = ArrayDeque<String>()
            for (part in decoded.split('/')) {
                when (part) {
                    "", "." -> Unit
                    ".." -> if (parts.isEmpty()) return@runCatching null else parts.removeLast()
                    else -> parts.addLast(part)
                }
            }
            parts.joinToString("/").takeIf { it.isNotEmpty() }
        }.getOrNull()

    fun encode(path: String): String =
        path.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
}
