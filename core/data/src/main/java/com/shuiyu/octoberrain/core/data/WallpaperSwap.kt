package com.shuiyu.octoberrain.core.data

import java.io.File
import java.io.InputStream

/**
 * 壁纸文件替换事务：先写入新文件并校验，再更新配置，最后删除旧文件。
 *
 * 该顺序保证任一环节失败时原有壁纸仍然可用：写入或校验失败只清理新文件，
 * 配置更新失败则回滚新文件，两次失败都不触碰旧路径。依赖以函数形式注入，
 * 便于在 JVM 上以临时目录验证替换顺序与异常分支。
 *
 * @param directory 壁纸私有目录。
 * @param openSource 打开所选图片输入流的函数，无法打开时返回 null。
 * @param isValidImage 校验文件是否为合法图片的函数。
 * @param updateConfig 写入新壁纸路径的函数，返回配置是否确认更新。
 */
internal class WallpaperSwap(
    private val directory: File,
    private val openSource: () -> InputStream?,
    private val isValidImage: (File) -> Boolean,
    private val updateConfig: (String) -> Boolean,
) {
    /**
     * 执行一次壁纸替换。
     *
     * @param prefix 文件名前缀，用于区分桌面与锁屏壁纸。
     * @param timestamp 生成文件名所用的时间戳，单位毫秒。
     * @param previousPath 当前配置中的旧壁纸路径，可为 null 或空字符串。
     * @return 配置确认更新且旧文件已清理时返回 true；否则返回 false 且保留旧文件。
     */
    fun execute(
        prefix: String,
        timestamp: Long,
        previousPath: String?,
    ): Boolean {
        val target = createTarget(prefix, timestamp)
        val staged = stage(target)
        val updated = staged && updateConfig(target.absolutePath)
        if (!updated) target.delete()
        if (updated) deletePrevious(previousPath, target)
        return updated
    }

    /** 把来源流写入目标文件并校验，失败时清理残缺文件。 */
    private fun stage(target: File): Boolean =
        runCatching {
            val input = openSource() ?: error("无法读取所选图片")
            input.use { source ->
                target.outputStream().use { output -> source.copyTo(output) }
            }
            check(isValidImage(target)) { "所选文件不是有效图片" }
        }.onFailure {
            target.delete()
        }.isSuccess

    /** 生成不重名的目标文件，冲突时追加序号。 */
    private fun createTarget(
        prefix: String,
        timestamp: Long,
    ): File {
        var index = 1
        var candidate = File(directory, targetName(prefix, timestamp, index))
        while (candidate.exists() && index < MAX_SUFFIX) {
            index += 1
            candidate = File(directory, targetName(prefix, timestamp, index))
        }
        return candidate
    }

    /** 拼出指定序号的壁纸文件名，序号为 1 时不带后缀。 */
    private fun targetName(
        prefix: String,
        timestamp: Long,
        index: Int,
    ): String = if (index <= 1) "${prefix}_$timestamp.png" else "${prefix}_${timestamp}_$index.png"

    /** 删除配置中的旧壁纸文件，路径为空、非文件或与新文件相同时跳过。 */
    private fun deletePrevious(
        previousPath: String?,
        target: File,
    ) {
        val previous =
            previousPath
                ?.takeIf { path -> path.isNotBlank() }
                ?.let(::File)
                ?.takeIf { file -> file.isFile && file.absolutePath != target.absolutePath }
        previous?.delete()
    }

    private companion object {
        /** 同名冲突时允许的最大序号，与文件操作模块保持一致。 */
        const val MAX_SUFFIX = 999
    }
}
