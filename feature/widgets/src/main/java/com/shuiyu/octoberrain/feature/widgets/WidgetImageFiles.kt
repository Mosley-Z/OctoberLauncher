package com.shuiyu.octoberrain.feature.widgets

import java.io.File

/** 私有导入是单图；历史外部目录仍作为轮播来源。 */
internal object WidgetImageFiles {
    private val extensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    fun isManaged(
        file: File,
        filesDir: File,
    ): Boolean {
        val parent = file.canonicalFile.parentFile ?: return false
        val root = File(filesDir, "widget-image").canonicalFile
        return parent == root ||
            parent == File(filesDir, "widgets").canonicalFile ||
            (parent.parentFile == root && parent.name.matches(Regex("page-[0-9]+")))
    }

    fun resolve(
        path: String?,
        filesDir: File,
    ): List<File> {
        val current = path?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile } ?: return emptyList()
        return if (isManaged(current, filesDir)) {
            listOf(current)
        } else {
            current.parentFile
                ?.listFiles()
                ?.filter {
                    it.isFile && (it == current || it.extension.lowercase() in extensions)
                }?.sortedBy { it.name }
                ?.ifEmpty { listOf(current) } ?: listOf(current)
        }
    }

    fun unusedManaged(
        path: String?,
        filesDir: File,
        references: List<String>,
    ): File? {
        val file = path?.let(::File) ?: return null
        return if (isManaged(file, filesDir)) {
            file.takeUnless { candidate -> references.any { File(it).canonicalFile == candidate.canonicalFile } }
        } else {
            null
        }
    }
}
