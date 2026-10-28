package com.shuiyu.octoberrain.feature.content.data

import java.io.File

/** 在常用图片目录有界补查未入媒体索引的文件，不混入书籍与字体，也不按遍历前几项截断。 */
internal object RecentImageFiles {
    private val extensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    fun collect(
        roots: List<File>,
        limit: Int,
    ): List<File> =
        roots
            .asSequence()
            .flatMap { root ->
                if (root.isDirectory) root.walkTopDown().maxDepth(5).take(4000) else emptySequence()
            }.filter { it.isFile && it.extension.lowercase() in extensions }
            .distinctBy { it.absolutePath }
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.absolutePath })
            .take(limit.coerceAtLeast(0))
            .toList()
}
