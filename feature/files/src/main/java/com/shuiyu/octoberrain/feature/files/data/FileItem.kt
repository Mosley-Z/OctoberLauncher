package com.shuiyu.octoberrain.feature.files.data

import java.util.Locale

/**
 * 文件与目录的统一条目模型。
 *
 * 浏览页与选择器只读取本对象，不直接读文件系统；实例由 [FileListing] 创建。
 * 目录的 size 恒为 0，mime 为空串；宽高由缩略图解码侧回填，未知时为 0。
 *
 * 参数 name 为不含路径的名称；path 为绝对路径；dir 表示是否为目录；
 * size 为字节数；mtime 为最后修改时间毫秒；mime 为 MIME 类型；
 * width 与 height 为图片像素尺寸。
 */
data class FileItem(
    val name: String,
    val path: String,
    val dir: Boolean,
    val size: Long,
    val mtime: Long,
    val mime: String,
    val width: Int = 0,
    val height: Int = 0,
) {
    /** 是否为图片，按 MIME 前缀判断。 */
    val isImage: Boolean get() = !dir && mime.startsWith("image/")

    /** 安装包在进入通用阅读器前由用户选择安装或查看结构。 */
    val isApk: Boolean
        get() = !dir && (extension().equals(".apk", true) || mime.substringBefore(';').trim().equals(APK_MIME, true))

    /** EPUB remains a document, even when its cover is available. */
    val isEpub: Boolean get() = !dir && (mime == "application/epub+zip" || extension().equals(".epub", true))

    /** Whether a grid may request a visual preview. */
    val hasThumbnail: Boolean get() = isImage || isEpub

    /** 是否为可编辑图片，动图与 HEIF 只做查看。 */
    val isEditableImage: Boolean
        get() = isImage && !mime.contains("gif") && !mime.contains("heif")

    /** 文件字节数，目录与异常负值统一为 0。 */
    val sizeOrZero: Long get() = if (dir) 0L else size.coerceAtLeast(0L)

    /** 上级目录路径，根目录与无路径分隔符时返回空串。 */
    val parentPath: String
        get() {
            val slash = path.lastIndexOf('/')
            return if (slash <= 0) "" else path.substring(0, slash)
        }

    /**
     * 取扩展名，包含前导点。
     *
     * 隐藏文件风格名称（如 .nomedia）与无扩展名的名称返回空串。
     */
    fun extension(): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) name.substring(dot) else ""
    }

    /** 取不含扩展名的名称。 */
    fun baseName(): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) name.substring(0, dot) else name
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"

        /** 创建目录条目，目录不记录字节数与 MIME。 */
        fun dir(
            name: String,
            path: String,
            mtime: Long,
        ): FileItem =
            FileItem(
                name = name,
                path = path,
                dir = true,
                size = 0L,
                mtime = mtime,
                mime = "",
            )

        /** 创建文件条目，MIME 为空时按未知类型保存。 */
        fun file(
            name: String,
            path: String,
            size: Long,
            mtime: Long,
            mime: String?,
            width: Int = 0,
            height: Int = 0,
        ): FileItem =
            FileItem(
                name = name,
                path = path,
                dir = false,
                size = size,
                mtime = mtime,
                mime = mime.orEmpty(),
                width = width,
                height = height,
            )

        /** 取小写扩展名，不含前导点，供 MIME 推断使用。 */
        internal fun extensionLower(name: String?): String {
            val value = name ?: return ""
            val dot = value.lastIndexOf('.')
            return if (dot > 0 && dot < value.length - 1) value.substring(dot + 1).lowercase(Locale.US) else ""
        }
    }
}
