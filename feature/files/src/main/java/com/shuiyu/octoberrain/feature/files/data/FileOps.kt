package com.shuiyu.octoberrain.feature.files.data

import java.io.File

/** 同名副本的最大序号，超过后放弃生成新名，避免无界循环。 */
private const val MAX_SUFFIX = 999

/** 覆盖写入使用的临时文件后缀，替换成功前保留原目标内容。 */
private const val TEMP_SUFFIX = ".inkdisk-tmp"

/**
 * 文件内容比对、同名改名与复制移动工具。
 *
 * 供文件操作卡片（移动、冲突处理）调用，不涉及界面与权限，全部方法可在本地 JVM 运行。
 * 复制与覆盖写入由 [FileStreams] 流式完成，不整体读入内存。
 * 所有方法返回布尔值表示完成与否，失败原因通过返回值语义表达，不抛受检异常。
 */
object FileOps {
    /**
     * 判断两个文件的内容是否完全相同。
     *
     * 先比较长度，长度不同立即返回 false；长度相同时按缓冲逐块比较，
     * 遇到第一处差异立即返回，不整体读入内存。
     *
     * 参数 left 与 right 为待比较文件，允许为 null。
     * 两个文件都存在、可读且内容完全相同时返回 true；文件缺失、不可读、
     * 读取中断或内容存在差异时返回 false。
     */
    fun sameContent(
        left: File?,
        right: File?,
    ): Boolean {
        val source = left?.takeIf { it.isFile }
        val target = right?.takeIf { it.isFile }
        if (source == null || target == null) return false
        return target.length() == source.length() && FileStreams.sameContent(source, target)
    }

    /**
     * 在指定目录内生成不与现有文件冲突的名称。
     *
     * 原名称未被占用时直接返回原名称；已占用时依次尝试「名称_2」「名称_3」直到找到
     * 未被占用的名称，扩展名保留在序号之后。本方法只探测可用名称，不创建任何文件。
     *
     * 参数 dir 为目标目录，允许为 null；name 为原始文件名，允许为 null 或空串。
     * 返回目录内最终可用的文件名；序号耗尽时返回原名称，由调用方决定是否覆盖。
     */
    fun uniqueName(
        dir: File?,
        name: String?,
    ): String? {
        if (dir == null || name == null || name.isEmpty()) return name
        return freeName(dir, name)
    }

    /**
     * 复制一个文件到目标位置。
     *
     * 目标是已存在的目录时自动拼接源文件名。目标已存在且与源内容相同时直接返回 true；
     * 目标已存在但内容不同时先写同目录临时文件，成功后再替换，避免写入中断损坏原文件。
     *
     * 参数 src 为源文件；dst 为目标路径，可以是文件路径或已存在的目录。
     * 复制完成或目标已存在同内容文件时返回 true；失败时返回 false。
     */
    fun copy(
        src: File?,
        dst: File?,
    ): Boolean {
        if (src == null || dst == null || !src.isFile) return false
        val target = resolveTarget(src, dst)
        return ensureParent(target) && copyToTarget(src, target)
    }

    /**
     * 移动一个文件到目标位置。
     *
     * 目标是已存在的目录时自动拼接源文件名。目标已存在同内容文件时删除源文件完成移动；
     * 目标已存在但内容不同时拒绝覆盖并返回 false，调用方应先用 [uniqueName] 生成新名称。
     * 目标不存在时先尝试同文件系统重命名，失败则回退为流式复制后删除源文件。
     *
     * 参数 src 为源文件；dst 为目标路径，可以是文件路径或已存在的目录。
     * 移动完成（源文件已不存在）时返回 true；其余情况返回 false。
     */
    fun move(
        src: File?,
        dst: File?,
    ): Boolean {
        if (src == null || dst == null || !src.isFile) return false
        val target = resolveTarget(src, dst)
        return moveToTarget(src, target)
    }

    /**
     * 复制一个条目到目标目录，目录按层递归处理。
     *
     * 目标目录内已存在同名条目时按 [uniqueName] 自动追加序号，不询问也不覆盖。
     * 文件沿用 [copy] 的单文件流程；目录先创建不冲突的目标目录，再逐项复制子条目。
     * 目标目录位于源目录内部时拒绝执行，避免递归复制自身。
     * 中途失败时已完成的部分保留在目标目录，由调用方决定提示与后续处理。
     *
     * 参数 src 为源文件或目录；dstDir 为已存在的目标目录。
     * 成功返回目标条目，失败返回 null。
     */
    fun copyEntry(
        src: File?,
        dstDir: File?,
    ): File? {
        val source = src?.takeIf { it.exists() }
        val targetDir = dstDir?.takeIf { it.isDirectory }
        if (source == null || targetDir == null || isInside(source, targetDir)) return null
        val destination = uniqueName(targetDir, source.name)?.let { File(targetDir, it) }
        return destination?.let { target ->
            if (source.isDirectory) copyDirectory(source, target) else target.takeIf { copy(source, it) }
        }
    }

    /**
     * 移动一个条目到目标目录，目录按层递归处理。
     *
     * 目标目录内已存在同名条目时按 [uniqueName] 自动追加序号，不询问也不覆盖。
     * 目标目录就是源条目所在目录时视为无需移动，直接返回源条目且不改动任何内容。
     * 目录优先整目录重命名；重命名失败时退回递归复制，复制全部成功后再删除源目录。
     * 目标目录位于源目录内部或与源目录相同时拒绝执行。
     * 中途失败时已完成的部分保留在目标目录，源目录不删除。
     *
     * 参数 src 为源文件或目录；dstDir 为已存在的目标目录。
     * 成功返回目标条目或原条目，失败返回 null。
     */
    fun moveEntry(
        src: File?,
        dstDir: File?,
    ): File? {
        val source = src?.takeIf { it.exists() }
        val targetDir = dstDir?.takeIf { it.isDirectory }
        if (source == null || targetDir == null || isInside(source, targetDir)) return null
        // 目标即源所在目录时属于无操作，直接返回源条目，避免生成多余序号副本。
        val parent = source.parentFile
        val sameDirectory = parent != null && canonicalOf(parent) == canonicalOf(targetDir)
        return if (sameDirectory) {
            source
        } else {
            uniqueName(targetDir, source.name)?.let { name ->
                val destination = File(targetDir, name)
                // 同一文件系统内整目录重命名代价最低，失败后再按跨卷方式复制。
                when {
                    source.isFile -> destination.takeIf { move(source, it) }
                    source.renameTo(destination) -> destination
                    copyDirectory(source, destination) == null -> null
                    source.deleteRecursively() -> destination
                    else -> null
                }
            }
        }
    }
}

/**
 * 递归复制目录内容。
 *
 * 目标目录不存在时逐层创建；子条目按 [FileOps.uniqueName] 取得不冲突名称。
 * 任一层失败时继续处理其余子条目以保留已完成部分，最终返回 null 表示整体失败。
 */
private fun copyDirectory(
    src: File,
    dst: File,
): File? {
    val children = if (dst.isDirectory || dst.mkdirs()) src.listFiles() else null
    var completed = children != null
    children?.forEach { child ->
        val name = FileOps.uniqueName(dst, child.name)
        val copied =
            name?.let { unique ->
                val target = File(dst, unique)
                if (child.isDirectory) {
                    copyDirectory(child, target)
                } else {
                    target.takeIf { FileOps.copy(child, it) }
                }
            }
        if (copied == null) completed = false
    }
    return dst.takeIf { completed }
}

/** 判断目标目录是否等于源目录或位于源目录内部，用于拒绝自包含的复制与移动。 */
private fun isInside(
    src: File,
    dstDir: File,
): Boolean =
    when (val source = canonicalOf(src)) {
        null -> true
        else ->
            canonicalOf(dstDir)?.let { target ->
                target == source || target.path.startsWith(source.path + File.separator)
            } ?: true
    }

/** 取规范化路径，失败时退回绝对路径；均不可用时返回 null。 */
private fun canonicalOf(file: File): File? =
    runCatching { file.canonicalFile }.getOrElse {
        runCatching { file.absoluteFile }.getOrNull()
    }

/** 在目录内寻找未被占用的名称，序号从 2 开始直到 [MAX_SUFFIX]。 */
private fun freeName(
    dir: File,
    name: String,
): String {
    if (!File(dir, name).exists()) return name
    val dot = name.lastIndexOf('.')
    // 扩展名分隔点位于首位时属于隐藏文件风格名称（如 .nomedia），不拆分为基名与扩展名。
    val base = if (dot > 0) name.substring(0, dot) else name
    val extension = if (dot > 0) name.substring(dot) else ""
    val candidate =
        (2..MAX_SUFFIX)
            .asSequence()
            .map { index -> File(dir, base + "_" + index + extension) }
            .firstOrNull { !it.exists() }
    return candidate?.name ?: name
}

/** 目标不存在时直接写入；已存在时按内容是否相同分别处理。 */
private fun copyToTarget(
    src: File,
    target: File,
): Boolean =
    when {
        !target.exists() -> FileStreams.write(src, target)
        !target.isFile -> false
        FileOps.sameContent(src, target) -> true
        else -> replaceExisting(src, target)
    }

/**
 * 覆盖已存在的不同内容文件。
 *
 * 先把源内容写入同目录临时文件，成功后再删除目标并重命名；重命名失败时按源内容
 * 重建目标，最后清理临时文件，避免中断导致目标内容丢失。
 */
private fun replaceExisting(
    src: File,
    target: File,
): Boolean {
    val directory = target.parentFile ?: target.absoluteFile.parentFile
    return directory?.let {
        val temporary = File(it, target.name + TEMP_SUFFIX)
        val written = FileStreams.write(src, temporary)
        if (written && target.delete()) {
            val renamed = temporary.renameTo(target)
            val restored = if (renamed) true else FileStreams.write(src, target)
            temporary.delete()
            restored
        } else {
            temporary.delete()
            false
        }
    } == true
}

/** 按目标状态完成移动：同路径、已存在、新建与跨文件系统回退。 */
private fun moveToTarget(
    src: File,
    target: File,
): Boolean =
    when {
        canonicalOf(src) == canonicalOf(target) -> true
        target.exists() -> target.isFile && FileOps.sameContent(src, target) && src.delete()
        !ensureParent(target) -> false
        else -> moveByRenameOrCopy(src, target)
    }

/** 优先同文件系统重命名，失败时复制后删除源文件。 */
private fun moveByRenameOrCopy(
    src: File,
    target: File,
): Boolean = if (src.renameTo(target)) true else FileOps.copy(src, target) && src.delete()

/** 确保目标文件的父目录存在；父目录为空或创建成功、已存在时返回 true。 */
private fun ensureParent(target: File): Boolean {
    val parent = target.parentFile ?: return true
    return parent.exists() || parent.mkdirs() || parent.exists()
}

/** 计算复制或移动的最终目标路径：目标为目录时返回目录下与源同名的路径。 */
private fun resolveTarget(
    src: File,
    dst: File,
): File = if (dst.isDirectory) File(dst, src.name) else dst
