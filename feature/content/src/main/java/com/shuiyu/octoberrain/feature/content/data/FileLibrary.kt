package com.shuiyu.octoberrain.feature.content.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.shuiyu.octoberrain.core.common.InkLog
import java.io.File

/** 文件列表条目。 */
data class LibraryEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

/** 文件管理总览，含三张入口卡的数量与存储占用。 */
data class LibraryOverview(
    val booksCount: Int,
    val picturesCount: Int,
    val fontsCount: Int,
    val totalBytes: Long,
    val usedBytes: Long,
    val capacityBytes: Long,
)

/** 文件操作结果。 */
data class FileResult(
    val success: Boolean,
    val message: String,
)

/**
 * 文件管理能力。
 *
 * 提供书籍、图片、字体与全部文件四个入口的统计、目录浏览、最近文件，以及
 * 重命名、复制、移动、新建目录与删除操作。所有写操作在执行前校验路径关系，
 * 避免把目录复制到自身内部；打开文件时先把目标复制到缓存目录，再经
 * FileProvider 生成 content:// 地址，绕过外部存储路径未声明的限制。
 */
class FileLibrary(
    private val context: Context,
) {
    companion object {
        private const val TAG = "文件管理"
        const val PAGE_SIZE = 9
        private const val RECENT_LIMIT = 5
        private const val MAX_COUNT_DEPTH = 5
        private const val MAX_OPEN_CACHE = 6

        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
        private val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
    }

    /** 书籍目录。 */
    fun booksDirectory(): File = ContentPaths.booksRoot()

    /** 图片目录。 */
    fun picturesDirectory(): File = File(ContentPaths.externalRoot(), "Pictures")

    /** 字体目录。 */
    fun fontsDirectory(): File = File(ContentPaths.externalRoot(), "Fonts")

    /** 外部存储根目录。 */
    fun externalDirectory(): File = ContentPaths.externalRoot()

    /** 四个入口对应的根目录，顺序与页面卡片一致。 */
    fun roots(): List<File> =
        listOf(
            booksDirectory(),
            picturesDirectory(),
            fontsDirectory(),
            externalDirectory(),
        )

    /** 统计入口目录的文件数量与占用，自动创建书籍目录。 */
    fun overview(): LibraryOverview {
        val books = booksDirectory().apply { if (!exists()) mkdirs() }
        val pictures = picturesDirectory()
        val fonts = fontsDirectory()
        val booksCount = countFiles(books, BookshelfRepository.BOOK_EXTENSIONS)
        val picturesCount = countFiles(pictures, IMAGE_EXTENSIONS)
        val fontsCount = countFiles(fonts, FONT_EXTENSIONS)
        val totalBytes = directorySize(books) + directorySize(pictures) + directorySize(fonts)
        val capacity = capacityBytes()
        val used = usedBytes()
        return LibraryOverview(
            booksCount = booksCount,
            picturesCount = picturesCount,
            fontsCount = fontsCount,
            totalBytes = totalBytes,
            usedBytes = used,
            capacityBytes = capacity,
        )
    }

    /** 列出目录内容，目录在前，同类按名称升序。 */
    fun list(directory: File): List<LibraryEntry> {
        val children = runCatching { directory.listFiles() }.getOrNull() ?: return emptyList()
        return children
            .filter { it.isDirectory || it.isFile }
            .map { file ->
                LibraryEntry(
                    file = file,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                    modifiedAt = file.lastModified(),
                )
            }.sortedWith(compareByDescending<LibraryEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /** 三个内容目录中最近修改的若干文件。 */
    fun recentFiles(): List<LibraryEntry> {
        val directories = listOf(booksDirectory(), picturesDirectory(), fontsDirectory())
        val files = ArrayList<LibraryEntry>()
        for (directory in directories) {
            if (!directory.isDirectory) continue
            runCatching {
                for (file in directory.walkTopDown().maxDepth(MAX_COUNT_DEPTH)) {
                    if (file.isFile && isContentFile(file)) {
                        files.add(
                            LibraryEntry(
                                file = file,
                                name = file.name,
                                isDirectory = false,
                                sizeBytes = file.length(),
                                modifiedAt = file.lastModified(),
                            ),
                        )
                    }
                    if (files.size >= 80) break
                }
            }.onFailure { InkLog.w(TAG, "扫描最近文件失败：${it.message}") }
        }
        return files.sortedByDescending { it.modifiedAt }.take(RECENT_LIMIT)
    }

    /** 从根目录到当前目录的路径层级，用于面包屑与上级跳转。 */
    fun ancestors(directory: File): List<File> {
        val chain = ArrayList<File>()
        var current: File? = directory
        val root = ContentPaths.externalRoot()
        while (current != null && chain.size < 16) {
            chain.add(0, current)
            if (current.absolutePath == root.absolutePath) break
            current = current.parentFile
        }
        return chain
    }

    /** 目录显示名称，根目录显示为“全部文件”。 */
    fun displayName(directory: File): String =
        if (directory.absolutePath == ContentPaths.externalRoot().absolutePath) "全部文件" else directory.name

    /** 读取文本文件内容，超过上限或读取失败返回 null。 */
    fun readText(
        file: File,
        maxBytes: Long = 256L * 1024L,
    ): String? {
        if (!file.isFile || file.length() > maxBytes) return null
        return runCatching { file.readText() }.getOrElse {
            InkLog.w(TAG, "读取 ${file.name} 失败：${it.message}")
            null
        }
    }

    /** 重命名文件或目录，书籍同名侧边车目录一并改名。 */
    fun rename(
        target: File,
        newName: String,
    ): FileResult {
        if (!target.exists()) return FileResult(false, "目标不存在")
        val cleaned = normalizeName(target, newName)
        if (cleaned == null) return FileResult(false, "名称不合法")
        val renamed = File(target.parentFile, cleaned)
        if (renamed.absolutePath == target.absolutePath) return FileResult(false, "名称未变化")
        if (renamed.exists()) return FileResult(false, "同名文件已存在")
        val sidecar = File(target.absolutePath + ".sdr")
        val success = runCatching { target.renameTo(renamed) }.getOrElse { false }
        if (!success) return FileResult(false, "重命名失败")
        if (sidecar.isDirectory) {
            val renamedSidecar = File(renamed.absolutePath + ".sdr")
            if (!renamedSidecar.exists()) {
                runCatching { sidecar.renameTo(renamedSidecar) }
            }
        }
        return FileResult(true, "已重命名为 $cleaned")
    }

    /** 复制或移动文件与目录，目标重名时自动追加序号。 */
    fun copyOrMove(
        source: File,
        destinationDirectory: File,
        move: Boolean,
    ): FileResult {
        if (!source.exists()) return FileResult(false, "来源不存在")
        if (!destinationDirectory.isDirectory) return FileResult(false, "目标目录不可用")
        if (source.isDirectory && contains(source, destinationDirectory)) {
            return FileResult(false, "不能把目录复制到自身内部")
        }
        val destination = uniqueDestination(destinationDirectory, source.name)
        if (source.isDirectory && contains(source, destination)) {
            return FileResult(false, "不能把目录复制到自身内部")
        }
        val success =
            runCatching {
                if (source.isDirectory) copyDirectory(source, destination) else copyFile(source, destination)
            }.getOrElse {
                InkLog.w(TAG, "复制 ${source.name} 失败：${it.message}")
                false
            }
        if (!success) return FileResult(false, "复制失败")
        if (move) {
            val removed = if (source.isDirectory) source.deleteRecursively() else source.delete()
            if (!removed) return FileResult(false, "已复制但来源删除失败")
        }
        return FileResult(true, "已复制到 ${destinationDirectory.name}/${destination.name}")
    }

    /** 在目录内新建子目录。 */
    fun createDirectory(
        parent: File,
        name: String,
    ): FileResult {
        if (!parent.isDirectory) return FileResult(false, "父目录不可用")
        val cleaned = name.trim()
        if (cleaned.isEmpty() || cleaned.contains('/') || cleaned.contains('\u0000')) {
            return FileResult(false, "名称不合法")
        }
        val target = File(parent, cleaned)
        if (target.exists()) return FileResult(false, "同名条目已存在")
        if (!target.mkdirs()) return FileResult(false, "创建目录失败")
        return FileResult(true, "已创建目录 $cleaned")
    }

    /** 递归删除文件或目录。 */
    fun delete(target: File): FileResult {
        if (!target.exists()) return FileResult(false, "目标不存在")
        val root = ContentPaths.externalRoot()
        if (target.absolutePath == root.absolutePath) return FileResult(false, "不能删除存储根目录")
        val success = runCatching { target.deleteRecursively() }.getOrElse { false }
        return if (success) FileResult(true, "已删除") else FileResult(false, "删除失败")
    }

    /** 是否为图片文件。 */
    fun isImage(file: File): Boolean = file.extension.lowercase() in IMAGE_EXTENSIONS

    /** 是否为可预览的文本文件。 */
    fun isTextFile(file: File): Boolean =
        file.extension.lowercase() in setOf("txt", "lua", "md", "json", "xml", "log", "csv")

    /**
     * 生成打开文件的意图。
     *
     * 先把文件复制到 cacheDir/updates/open，再通过已声明的 FileProvider 授权，
     * 未安装可处理应用时返回 null。
     */
    fun openIntent(file: File): Intent? {
        if (!file.isFile) return null
        val shared = copyToOpenCache(file) ?: return null
        val uri: Uri =
            runCatching {
                FileProvider.getUriForFile(context, context.packageName + ".files", shared)
            }.getOrElse {
                InkLog.w(TAG, "生成文件地址失败：${it.message}")
                return null
            }
        val mimeType = mimeTypeOf(file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 把待打开文件复制到缓存目录，并清理较早的缓存副本。 */
    private fun copyToOpenCache(file: File): File? {
        val directory = File(File(context.cacheDir, "updates"), "open").apply { if (!exists()) mkdirs() }
        if (!directory.isDirectory) return null
        val target = File(directory, "${file.nameWithoutExtension.take(60)}.${file.extension}")
        val success =
            runCatching {
                file.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }.getOrElse {
                InkLog.w(TAG, "复制到缓存失败：${it.message}")
                false
            }
        if (!success) return null
        runCatching {
            directory
                .listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_OPEN_CACHE)
                ?.forEach { it.delete() }
        }
        return target.takeIf { it.length() > 0L }
    }

    /** 根据扩展名推断 MIME 类型。 */
    private fun mimeTypeOf(file: File): String {
        val extension = file.extension.lowercase()
        val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (!mapped.isNullOrBlank()) return mapped
        return when (extension) {
            "epub" -> "application/epub+zip"
            "mobi" -> "application/x-mobipocket-ebook"
            "azw", "azw3" -> "application/vnd.amazon.ebook"
            "fb2" -> "application/x-fictionbook+xml"
            "djvu" -> "image/vnd.djvu"
            "cbz" -> "application/vnd.comicbook+zip"
            "cbr" -> "application/vnd.comicbook-rar"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "ttc" -> "font/collection"
            else -> "*/*"
        }
    }

    /** 参数补全扩展名并校验非法字符，非法返回 null。 */
    private fun normalizeName(
        target: File,
        raw: String,
    ): String? {
        var name = raw.trim()
        if (name.isEmpty() || name == "." || name == "..") return null
        if (name.contains('/') || name.contains('\\') || name.contains('\u0000')) return null
        if (!target.isDirectory && !name.contains('.') && target.extension.isNotEmpty()) {
            name = "$name.${target.extension}"
        }
        return name
    }

    /** 目标是否位于目录内部。 */
    private fun contains(
        directory: File,
        target: File,
    ): Boolean {
        val directoryPath = directory.absolutePath.trimEnd(File.separatorChar) + File.separator
        return target.absolutePath.startsWith(directoryPath)
    }

    /** 生成不重名的目标文件，重名时追加 -2、-3 序号。 */
    fun uniqueDestination(
        directory: File,
        name: String,
    ): File {
        var candidate = File(directory, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var index = 2
        while (candidate.exists() && index < 1000) {
            val candidateName = if (extension.isEmpty()) "$base-$index" else "$base-$index.$extension"
            candidate = File(directory, candidateName)
            index += 1
        }
        return candidate
    }

    /** 单文件复制。 */
    private fun copyFile(
        source: File,
        destination: File,
    ): Boolean {
        source.inputStream().use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination.isFile && destination.length() == source.length()
    }

    /** 目录递归复制。 */
    private fun copyDirectory(
        source: File,
        destination: File,
    ): Boolean {
        if (!destination.mkdirs() && !destination.isDirectory) return false
        val children = source.listFiles() ?: return true
        for (child in children) {
            val childDestination = File(destination, child.name)
            val success =
                if (child.isDirectory) {
                    copyDirectory(child, childDestination)
                } else {
                    runCatching { copyFile(child, childDestination) }.getOrElse {
                        InkLog.w(TAG, "复制子项 ${child.name} 失败：${it.message}")
                        false
                    }
                }
            if (!success) return false
        }
        return true
    }

    /** 统计目录内指定扩展名的文件数量。 */
    private fun countFiles(
        directory: File,
        extensions: Set<String>,
    ): Int {
        if (!directory.isDirectory) return 0
        var count = 0
        runCatching {
            for (file in directory.walkTopDown().maxDepth(MAX_COUNT_DEPTH)) {
                if (file.isFile && file.extension.lowercase() in extensions) count += 1
                if (count >= 9999) break
            }
        }.onFailure { InkLog.w(TAG, "统计 ${directory.name} 失败：${it.message}") }
        return count
    }

    /** 统计目录占用字节数，限制遍历规模避免卡顿。 */
    private fun directorySize(directory: File): Long {
        if (!directory.isDirectory) return 0L
        var total = 0L
        var visited = 0
        runCatching {
            for (file in directory.walkTopDown().maxDepth(MAX_COUNT_DEPTH)) {
                visited += 1
                if (visited > 4000) break
                if (file.isFile) total += file.length()
            }
        }.onFailure { InkLog.w(TAG, "统计目录大小失败：${it.message}") }
        return total
    }

    /** 是否属于内容目录中需要展示的文件类型。 */
    private fun isContentFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in BookshelfRepository.BOOK_EXTENSIONS ||
            extension in IMAGE_EXTENSIONS ||
            extension in FONT_EXTENSIONS
    }

    /** 存储容量总字节数，读取失败返回零。 */
    private fun capacityBytes(): Long =
        runCatching {
            StatFs(ContentPaths.externalRoot().absolutePath).totalBytes
        }.getOrElse {
            InkLog.w(TAG, "读取存储容量失败：${it.message}")
            0L
        }

    /** 存储已用字节数，读取失败返回零。 */
    private fun usedBytes(): Long =
        runCatching {
            val stat = StatFs(ContentPaths.externalRoot().absolutePath)
            (stat.totalBytes - stat.availableBytes).coerceAtLeast(0L)
        }.getOrElse {
            InkLog.w(TAG, "读取已用空间失败：${it.message}")
            0L
        }
}
