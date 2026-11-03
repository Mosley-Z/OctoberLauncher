package com.shuiyu.octoberrain.feature.files.data

import java.io.File

/** 后台读取实时文件属性；目录不递归统计，大小不伪称为全部内容大小。 */
internal data class FileEntryMetadata(
    val name: String,
    val path: String,
    val directory: Boolean,
    val bytes: Long,
    val modified: Long,
    val readable: Boolean,
    val writable: Boolean,
) {
    companion object {
        fun read(file: File): FileEntryMetadata? =
            if (file.exists()) {
                FileEntryMetadata(
                    file.name,
                    file.absolutePath,
                    file.isDirectory,
                    file.length(),
                    file.lastModified(),
                    file.canRead(),
                    file.canWrite(),
                )
            } else {
                null
            }
    }
}

/** 仅删除选中的普通文件，不递归处理目录。失败或权限异常由调用方呈现。 */
internal fun deleteFileEntry(file: File): Boolean = file.isFile && file.delete()

/**
 * 删除选中的条目，目录连同内容一并删除。
 *
 * 文件沿用 [deleteFileEntry] 的单文件流程；目录交给 deleteRecursively 逐层删除。
 * 条目不存在时返回 false，供调用方按失败计数。
 *
 * 参数 file 为待删除文件或目录。
 */
internal fun deleteFileEntryDeep(file: File): Boolean {
    if (!file.exists()) return false
    return if (file.isDirectory) file.deleteRecursively() else deleteFileEntry(file)
}
