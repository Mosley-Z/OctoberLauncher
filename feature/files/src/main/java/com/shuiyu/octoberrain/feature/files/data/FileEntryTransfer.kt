package com.shuiyu.octoberrain.feature.files.data

import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/** 优先同卷移动，避免全量复制；不传覆盖或原子移动选项。 */
internal fun moveEntryToNewTarget(
    source: File,
    destination: File,
): File? =
    try {
        Files.move(source.toPath(), destination.toPath())
        destination
    } catch (_: FileAlreadyExistsException) {
        null
    } catch (_: Exception) {
        copyEntryToNewTarget(source, destination)?.takeIf { source.deleteRecursively() }
    }

/**
 * 递归复制目录内容。
 *
 * 目标目录必须由本次新建；子条目按 [FileOps.copyEntry] 独占新建，不合并已有目录。
 * 任一层失败时继续处理其余子条目以保留已完成部分，最终返回 null 表示整体失败。
 */
private fun copyDirectory(
    src: File,
    dst: File,
): File? {
    val children = if (dst.mkdir()) src.listFiles() else null
    var completed = children != null
    children?.forEach { child ->
        val copied = FileOps.copyEntry(child, dst)
        if (copied == null) completed = false
    }
    return dst.takeIf { completed }
}

/** 自动命名后的提交边界：目标被抢占时失败，不替换文件或合并目录。 */
internal fun copyEntryToNewTarget(
    src: File,
    target: File,
): File? =
    runCatching {
        if (src.isDirectory) {
            copyDirectory(src, target)
        } else {
            Files
                .newOutputStream(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                .use { output -> src.inputStream().use { input -> input.copyTo(output) } }
            target
        }
    }.getOrNull()
