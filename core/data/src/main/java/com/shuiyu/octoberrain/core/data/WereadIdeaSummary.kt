package com.shuiyu.octoberrain.core.data

import java.io.File

/** 按书目文件版本缓存想法字数，逐书归并，不构造整库 NoteItem 列表；仅供后台调用。 */
class WereadIdeaSummary(
    private val root: File,
) {
    private data class Count(
        val stamp: List<Long>,
        val chars: Int,
    )

    private val counts = mutableMapOf<String, Count>()

    @Synchronized
    fun characters(checkpoint: () -> Unit = {}): Int {
        val library = WereadNoteLibrary(root)
        val ids =
            library
                .books()
                .map { it.text("bookId") }
                .filter { it.isNotBlank() }
                .distinct()
        counts.keys.retainAll(ids.toSet())
        return ids.sumOf { id ->
            checkpoint()
            val files = listOf(File(root, "nt_$id.json"), File(root, "ni_$id.json"))
            val stamp = files.flatMap { listOf(it.lastModified(), it.length()) }
            counts[id]?.takeIf { it.stamp == stamp }?.chars ?: run {
                val chars = library.items(id).sumOf { it.ideaText.orEmpty().length }
                counts[id] = Count(stamp, chars)
                chars
            }
        }
    }
}
