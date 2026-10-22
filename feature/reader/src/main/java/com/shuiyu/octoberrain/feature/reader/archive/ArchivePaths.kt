package com.shuiyu.octoberrain.feature.reader.archive

/** ZIP names are raw names, never URL-decoded; an encoded percent is a literal filename character. */
internal object ArchivePaths {
    fun normalize(name: String): String {
        require(name.length <= 4096 && '\u0000' !in name)
        val path = name.replace('\\', '/')
        require(!path.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(path))
        val result = ArrayDeque<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> {
                    require(result.isNotEmpty())
                    result.removeLast()
                }
                else -> result.addLast(part)
            }
        }
        return result.joinToString("/")
    }
}
