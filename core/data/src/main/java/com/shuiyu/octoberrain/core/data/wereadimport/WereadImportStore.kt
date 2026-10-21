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
     * onInstalled 可以来自任意配置存储，运行时异常也必须转换成迁移失败。
     *
     * @param entries 名称到明文字节的映射
     * @param timestamp 备份目录后缀，取导入时刻毫秒
     * @param onInstalled 目录替换后提交配置；抛出异常时恢复旧目录
     * @return 成功时返回目标目录、备份目录与写入的文件名；失败时返回说明
     */
    @Suppress("TooGenericExceptionCaught")
    fun install(
        entries: Map<String, ByteArray>,
        timestamp: Long = System.currentTimeMillis(),
        onInstalled: (File) -> Unit = {},
    ): WereadImportInstallResult {
        val invalid = validate(entries)
        if (invalid != null) return invalid
        return try {
            commit(entries, timestamp, onInstalled)
        } catch (e: Exception) {
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
        onInstalled: (File) -> Unit,
    ): WereadImportInstallResult {
        val staging = File(root.parentFile, "${root.name}.staging")
        val rollback = File(root.parentFile, "${root.name}.rollback")
        if (rollback.exists()) return WereadImportInstallResult.Failure("存在未恢复的数据目录，请先恢复备份")
        val hadRoot = root.exists()
        val backupTarget = if (root.listFiles()?.isNotEmpty() == true) nextBackup(timestamp) else null
        prepareStaging(staging)
        try {
            for ((name, data) in entries) File(staging, name).writeBytes(data)
            if (backupTarget != null) backupCurrent(backupTarget)
            if (hadRoot && !root.renameTo(rollback)) throw IOException("无法保留旧数据目录")
            replaceAndCommit(staging, rollback, hadRoot, onInstalled)
            rollback.deleteRecursively()
            return WereadImportInstallResult.Success(root, backupTarget, entries.keys.toList())
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun prepareStaging(staging: File) {
        if (staging.exists() && !staging.deleteRecursively()) throw IOException("无法清理临时目录")
        if (!staging.mkdirs()) throw IOException("无法创建临时目录")
    }

    private fun backupCurrent(backupTarget: File) {
        if (!backupRoot.isDirectory && !backupRoot.mkdirs()) throw IOException("无法创建备份目录")
        root.copyRecursively(backupTarget, overwrite = false)
    }

    // 配置回调的运行时异常同样需要回滚，不能只处理 IOException。
    @Suppress("TooGenericExceptionCaught")
    private fun replaceAndCommit(
        staging: File,
        rollback: File,
        hadRoot: Boolean,
        onInstalled: (File) -> Unit,
    ) {
        try {
            if (!staging.renameTo(root)) throw IOException("数据目录替换失败")
            // 配置也属于迁移事务；确认其持久化后才释放旧目录。
            onInstalled(root)
        } catch (error: Exception) {
            restorePrevious(rollback, hadRoot, error)
            throw error
        }
    }

    private fun restorePrevious(
        rollback: File,
        hadRoot: Boolean,
        error: Exception,
    ) {
        if (root.exists() && !root.deleteRecursively()) throw IOException("迁移失败，无法清理新数据，旧数据仍在回滚目录", error)
        if (hadRoot && !rollback.renameTo(root)) throw IOException("迁移失败，旧数据仍在回滚目录", error)
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
