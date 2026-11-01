package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.WidgetImageSelection
import java.io.File

/** 显式图片集合优先；无集合时保留历史单图、私有副本及外部同目录轮播行为。 */
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

    fun resolveSelection(
        selections: List<WidgetImageSelection>,
        legacyPath: String?,
        legacySource: String?,
        filesDir: File,
        missing: (String?) -> Boolean,
    ): List<File> =
        if (selections.isNotEmpty()) {
            selections.filterNot { missing(it.sourceUri) }.map { File(it.path) }.filter { it.isFile }
        } else if (missing(legacySource)) {
            emptyList()
        } else {
            resolve(legacyPath, filesDir)
        }

    fun references(desktop: DesktopConfig): List<String> =
        listOfNotNull(desktop.widgetImagePath) + desktop.widgetImages.map { it.path } +
            desktop.pageWidgets.values.flatMap { page ->
                listOfNotNull(page.widgetImagePath) + page.widgetImages.map { it.path }
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
