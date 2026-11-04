package com.shuiyu.octoberrain.feature.files.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 覆盖写入时临时文件名的前缀，以点号开头避免被媒体扫描识别为图片。 */
internal const val OVERWRITE_TEMP_PREFIX = ".overwrite-"

/**
 * 用临时文件原子替换目标文件。
 *
 * 参数 temp 为本次独占的同目录临时文件，target 为源文件；失败返回假，清理由拥有者负责。
 */
internal fun replaceAtomically(
    temp: File,
    target: File,
): Boolean =
    runCatching {
        Files.move(
            temp.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }.isSuccess

/** 独占创建同目录唯一临时文件，并在替换结束或失败后只清理本次文件。 */
internal fun writeOverwrite(
    plan: OverwritePlan,
    write: (File) -> Boolean,
    replace: (File, File) -> Boolean = ::replaceAtomically,
): ExportResult {
    val temp =
        runCatching { File.createTempFile(OVERWRITE_TEMP_PREFIX, ".tmp", plan.target.absoluteFile.parentFile) }
            .getOrNull() ?: return ExportResult.Failure("无法创建临时文件")
    return try {
        when {
            !runCatching { write(temp) }.getOrDefault(false) -> ExportResult.Failure("写入图片失败")
            !runCatching { replace(temp, plan.target) }.getOrDefault(false) -> ExportResult.Failure("替换源文件失败")
            else -> ExportResult.Success(plan.target.name, plan.target.absolutePath)
        }
    } finally {
        temp.delete()
    }
}
