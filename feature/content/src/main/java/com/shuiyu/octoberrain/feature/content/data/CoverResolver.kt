package com.shuiyu.octoberrain.feature.content.data

import android.content.Context
import java.io.File

/**
 * 书籍封面解析。
 *
 * 按约定优先级寻找封面：显式封面目录 → 书库封面目录 → 同目录同名图片 →
 * 侧边车目录图片 → EPUB 内嵌封面。命中后返回可交给图片解码器的本地路径。
 */
object CoverResolver {
    private val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val SIDECAR_COVER_NAMES = listOf("cover", "coverpage", "thumbnail")

    /** 解析书籍封面路径，全部来源缺失时返回 null。 */
    fun resolve(
        context: Context,
        book: File,
    ): String? {
        libraryCover(book)?.let { return it.absolutePath }
        siblingCover(book)?.let { return it.absolutePath }
        sidecarCover(book)?.let { return it.absolutePath }
        if (book.extension.equals("epub", ignoreCase = true)) {
            val directory = ContentPaths.cacheDirectory(context, "book_covers")
            EpubCover.extract(book, directory, stem(book))?.let { return it.absolutePath }
        }
        return null
    }

    /** 在显式封面目录 Books/cover 中按书名词干查找封面。 */
    fun libraryCover(book: File): File? {
        val directory = ContentPaths.coverRoot()
        if (!directory.isDirectory) return null
        return findByStem(directory, stem(book))
    }

    /** 在书籍所在目录查找与书名同名的图片。 */
    fun siblingCover(book: File): File? {
        val parent = book.parentFile ?: return null
        return findByStem(parent, book.nameWithoutExtension)
    }

    /** 在侧边车目录 <书名>.sdr 中查找封面图片。 */
    fun sidecarCover(book: File): File? {
        val sidecar = File(book.absolutePath + ".sdr")
        if (!sidecar.isDirectory) return null
        for (name in SIDECAR_COVER_NAMES) {
            findByStem(sidecar, name)?.let { return it }
        }
        return null
    }

    /** 生成稳定且可作文件名的封面词干，避免不同目录同名书互相覆盖。 */
    fun stem(book: File): String {
        val readable =
            book.nameWithoutExtension
                .take(40)
                .map { char -> if (char.isLetterOrDigit()) char else '_' }
                .joinToString("")
        val suffix = Integer.toHexString(book.absolutePath.hashCode())
        return if (readable.isBlank()) "cover_$suffix" else "${readable}_$suffix"
    }

    /** 在目录内按图片扩展名依次尝试同名文件。 */
    private fun findByStem(
        directory: File,
        stem: String,
    ): File? {
        if (stem.isBlank()) return null
        for (extension in IMAGE_EXTENSIONS) {
            val candidate = File(directory, "$stem.$extension")
            if (candidate.isFile && candidate.length() > 0L) return candidate
        }
        return null
    }
}
