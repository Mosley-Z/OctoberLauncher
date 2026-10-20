package com.shuiyu.octoberrain.core.data.wereadimport

import java.io.File
import java.io.IOException

/** 导入落位结果。 */
sealed interface WereadImportInstallResult {
    /**
     * 落位成功。
     *
     * @property target 数据根目录
     * @property backup 旧数据备份目录，首次导入时为 null
     * @property files 写入的文件名列表
     */
    data class Success(
        val target: File,
        val backup: File?,
        val files: List<String>,
    ) : WereadImportInstallResult

    /**
     * 落位失败，旧数据保持不变。
     *
     * @property message 失败说明
     */
    data class Failure(
        val message: String,
    ) : WereadImportInstallResult
}

/**
 * 迁移数据落位器。
 *
 * 把一个迁移包的条目写入内核数据根目录，写入前把旧数据整体备份，写入时先写临时目录，
 * 全部内容校验通过后再替换根目录，保证任何一步失败都保留上一份可用数据。
 *
 * 条目名沿用内核相对根的文件名，只允许字母、数字、下划线、连字符、点与中文，
 * 拒绝路径分隔符与父级跳转，避免迁移包成为任意路径写入口。
 *
 * @param root 内核数据根目录，十雨内置版为 filesDir/weread
 * @param backupRoot 备份根目录，十雨内置版为 filesDir/weread-backup
 */
class WereadImportStore(
    private val root: File,
    private val backupRoot: File,
) {
    /**
     * 安装迁移包条目。
     *
     * @param entries 名称到明文字节的映射
     * @param timestamp 备份目录后缀，取导入时刻毫秒
     * @return 成功时返回目标目录、备份目录与写入的文件名；失败时返回说明
     */
    fun install(
        entries: Map<String, ByteArray>,
        timestamp: Long = System.currentTimeMillis(),
    ): WereadImportInstallResult {
        val invalid = validate(entries)
        if (invalid != null) return invalid
        return try {
            commit(entries, timestamp)
        } catch (e: IOException) {
            WereadImportInstallResult.Failure("写入失败：${e.message}")
        }
    }

    /**
     * 校验条目集合是否可落位。
     *
     * @param entries 名称到明文字节的映射
     * @return 不合法时返回失败结果，合法时返回 null
     */
    private fun validate(entries: Map<String, ByteArray>): WereadImportInstallResult.Failure? {
        if (entries.isEmpty()) return WereadImportInstallResult.Failure("迁移包没有数据")
        val illegal = entries.keys.firstOrNull { !isSafeName(it) }
        return illegal?.let { WereadImportInstallResult.Failure("条目名不合法：$it") }
    }

    /**
     * 备份旧数据并准备临时目录。
     *
     * @param entries 名称到明文字节的映射
     * @param timestamp 备份目录后缀
     */
    private fun commit(
        entries: Map<String, ByteArray>,
        timestamp: Long,
    ): WereadImportInstallResult {
        val staging = File(root.parentFile, "${root.name}.staging")
        val hadOldData = root.exists() && root.listFiles()?.isNotEmpty() == true
        val backupTarget = if (hadOldData) nextBackup(timestamp) else null
        // 旧数据先整体备份，备份目录已存在时追加序号避免覆盖
        if (backupTarget != null) {
            backupRoot.mkdirs()
            root.copyRecursively(backupTarget, overwrite = true)
        }
        // 临时目录先接收全部条目，写完后再一次替换根目录
        staging.deleteRecursively()
        if (!staging.mkdirs()) {
            return WereadImportInstallResult.Failure("无法创建临时目录")
        }
        return finish(entries, staging, backupTarget)
    }

    /**
     * 写入临时目录并替换根目录。
     *
     * @param entries 名称到明文字节的映射
     * @param staging 临时目录
     * @param backupTarget 备份目录，没有旧数据时为 null
     */
    private fun finish(
        entries: Map<String, ByteArray>,
        staging: File,
        backupTarget: File?,
    ): WereadImportInstallResult {
        for ((name, data) in entries) {
            File(staging, name).writeBytes(data)
        }
        if (root.exists()) {
            root.deleteRecursively()
        }
        return if (staging.renameTo(root)) {
            WereadImportInstallResult.Success(
                target = root,
                backup = backupTarget,
                files = entries.keys.toList(),
            )
        } else {
            WereadImportInstallResult.Failure("数据目录替换失败")
        }
    }

    /**
     * 计算可用的备份目录，重名时追加序号。
     *
     * @param timestamp 备份目录后缀
     */
    private fun nextBackup(timestamp: Long): File {
        var target = File(backupRoot, "backup-$timestamp")
        var index = 1
        while (target.exists()) {
            target = File(backupRoot, "backup-$timestamp-$index")
            index += 1
        }
        return target
    }

    /**
     * 判断条目名是否可用于文件落位。
     *
     * 允许中文、字母、数字、下划线、连字符与点，拒绝路径分隔符、父级跳转与空名。
     *
     * @param name 条目名
     * @return 合法时返回 true
     */
    private fun isSafeName(name: String): Boolean =
        when {
            name.isBlank() || name.length > 128 -> false
            name == "." || name == ".." -> false
            name.contains('/') || name.contains('\\') || name.contains('\u0000') -> false
            name.startsWith(".") -> false
            else -> name.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' || it.code > 0x2E7F }
        }
}
