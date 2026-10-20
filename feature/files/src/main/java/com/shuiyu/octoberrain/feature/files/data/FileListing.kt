package com.shuiyu.octoberrain.feature.files.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 目录列举与路径换算。
 *
 * 浏览页只通过本类访问文件系统：目录内容在此一次性读入内存中的 [FileItem] 列表，
 * 视图绘制阶段不再触碰磁盘。隐藏文件过滤与排序偏好同样在此统一处理，
 * 供列表页、网格页与选择器共用。
 *
 * 参数 storage 提供存储卷与可读判断；prefs 决定隐藏文件与排序方式。
 */
class FileListing(
    private val storage: StorageAccess,
    private val prefs: FilePrefs,
) {
    /** 最近一次 [list] 的失败说明，成功或未列举过时为空串。 */
    var lastError: String = ""
        private set

    /**
     * 列举目录内容。
     *
     * 读取失败（无权限、目录不存在、不可读、被系统拒绝）时返回空列表，并把说明写入
     * [lastError]；可读但没有任何可见条目时返回空列表且 lastError 为空串，
     * 两种情况在界面上分别对应错误态与空态。
     *
     * 参数 dirPath 为目录绝对路径。
     */
    fun list(dirPath: String): List<FileItem> {
        lastError = ""
        val path = trimTrailingSlash(dirPath)
        val children = readableChildren(path, File(path))
        if (children == null) {
            lastError = ERROR_READ
            return emptyList()
        }
        val showHidden = prefs.showHidden
        val items =
            children
                .asSequence()
                .filter { it.name.isNotEmpty() }
                .filter { showHidden || !it.name.startsWith(".") }
                .map { toItem(it) }
                .toMutableList()
        items.sortWith(prefs.sortKey.comparator(prefs.sortAscending, true))
        return items
    }

    /**
     * 列出自选文件夹候选目录。
     *
     * 返回外置存储根下的全部一级非隐藏目录（含空目录），只读一级，不递归；
     * 读取失败时返回空列表，不写 lastError，勾选页据此显示空态。
     */
    fun listImageDirs(): List<String> {
        val rootPath = trimTrailingSlash(storage.externalRoot())
        // 根路径为空或不可读时 readableChildren 返回 null，orEmpty 归一为空列表。
        val children = readableChildren(rootPath, File(rootPath)).orEmpty()
        return children
            .asSequence()
            .filter { it.name.isNotEmpty() && !it.name.startsWith(".") }
            .filter { it.isDirectory }
            .map { it.absolutePath }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()
    }

    /**
     * 取上级目录路径。
     *
     * 参数 path 为当前目录绝对路径；当前已是卷根、根目录或系统挂载点时返回 null。
     */
    fun parentOf(path: String?): String? {
        val current = trimTrailingSlash(path)
        val slash = current.lastIndexOf('/')
        val parent = if (slash > 0) current.substring(0, slash) else ""
        return parent.takeIf { isBrowsableParent(current, it) }
    }

    /**
     * 取目录的显示名称，用于顶栏标题。
     *
     * 参数 path 为目录绝对路径；主存储根返回「内部存储」，其余返回路径最后一段。
     */
    fun nameOf(path: String?): String {
        val current = trimTrailingSlash(path)
        val slash = current.lastIndexOf('/')
        return when {
            current.isEmpty() -> ""
            current == trimTrailingSlash(storage.externalRoot()) -> "内部存储"
            slash >= 0 -> current.substring(slash + 1)
            else -> current
        }
    }

    /** 判断路径是否为某个存储卷的根目录。 */
    private fun isVolumeRoot(path: String): Boolean = storage.volumes().any { trimTrailingSlash(it) == path }

    /** 判断候选上级目录是否属于可浏览范围，卷根之上的系统挂载点返回 false。 */
    private fun isBrowsableParent(
        current: String,
        parent: String,
    ): Boolean = parent.isNotEmpty() && !isMountPoint(parent) && !isVolumeRoot(current)

    /** 判断路径是否为系统挂载点，挂载点之上不属于应用可浏览范围。 */
    private fun isMountPoint(path: String): Boolean = path == "/storage" || path == "/mnt"

    /** 列出目录内容；路径为空、不是目录、不可读或被系统拒绝时返回 null。 */
    private fun readableChildren(
        path: String,
        directory: File,
    ): Array<File>? {
        if (path.isEmpty() || !directory.isDirectory || !directory.canRead()) return null
        return listChildren(path)
    }

    /** 调用系统列举目录，受限目录抛出安全异常时按读取失败处理。 */
    private fun listChildren(path: String): Array<File>? =
        try {
            File(path).listFiles()
        } catch (_: SecurityException) {
            // Android 11 上访问 Android/data 等受限目录会抛出此异常，按不可读处理。
            null
        }

    /** 把系统文件对象转换为条目，目录与文件分别记录可用字段。 */
    private fun toItem(child: File): FileItem {
        val name = child.name
        val absolute = child.absolutePath
        val mtime = child.lastModified()
        return if (child.isDirectory) {
            FileItem.dir(name, absolute, mtime)
        } else {
            // 宽高留给缩略图解码侧使用，此处不额外打开文件。
            FileItem.file(name, absolute, child.length(), mtime, mimeOf(name))
        }
    }

    companion object {
        /** 目录读取失败的提示文案。 */
        const val ERROR_READ = "无法读取该目录，请检查存储权限"

        /**
         * 按扩展名推断 MIME 类型。
         *
         * 图片按具体格式返回，文本类返回各自的类型，其余统一为二进制流；
         * 浏览页据此决定是否绘制缩略图。
         */
        fun mimeOf(name: String?): String =
            when (FileItem.extensionLower(name)) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                "gif" -> "image/gif"
                "heic" -> "image/heic"
                "heif" -> "image/heif"
                "txt" -> "text/plain"
                "md" -> "text/markdown"
                "json" -> "application/json"
                "xml" -> "text/xml"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }

        /**
         * 把字节数格式化为可读大小。
         *
         * 参数 bytes 为字节数，负数按 0 处理；结果保留一位小数，单位依次为 B、KB、MB、GB。
         */
        fun formatSize(bytes: Long): String {
            val value = bytes.coerceAtLeast(0L)
            return when {
                value < 1024L -> String.format(Locale.US, "%.1f B", value.toDouble())
                value < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", value / 1024.0)
                value < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
                else -> String.format(Locale.US, "%.1f GB", value / (1024.0 * 1024.0 * 1024.0))
            }
        }

        /**
         * 把毫秒时间戳格式化为分钟级时间。
         *
         * 参数 millis 为毫秒时间戳，非正数返回空串；结果形如 2026-10-19 03:36。
         */
        fun formatTime(millis: Long): String {
            if (millis <= 0L) return ""
            return TIME_FORMAT.get()?.format(Date(millis)) ?: ""
        }

        /**
         * 去掉路径末尾多余的斜杠，便于父子比较。
         *
         * 参数 path 为原始路径，允许为 null；输入为空时返回空串，单独一个斜杠保持原样。
         */
        fun trimTrailingSlash(path: String?): String {
            if (path == null) return ""
            var value = path.trim()
            while (value.length > 1 && value.endsWith("/")) {
                value = value.substring(0, value.length - 1)
            }
            return value
        }

        // 格式器非线程安全，按线程各持一份，避免绘制与后台任务互相干扰。
        private val TIME_FORMAT: ThreadLocal<SimpleDateFormat> =
            ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    }
}
