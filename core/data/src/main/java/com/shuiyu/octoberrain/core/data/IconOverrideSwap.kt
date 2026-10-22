package com.shuiyu.octoberrain.core.data

import java.io.File
import java.io.InputStream

/** 图标先写独立文件，校验和配置确认成功后才清理旧图。 */
internal class IconOverrideSwap(
    private val directory: File,
    private val openSource: () -> InputStream?,
    private val isValidImage: (File) -> Boolean,
    private val updateConfig: (String) -> Boolean,
) {
    fun execute(
        fileName: String,
        previousPath: String?,
    ): Boolean {
        val staged =
            runCatching {
                File.createTempFile(fileName.removeSuffix(".png") + "_", ".png", directory)
            }.getOrNull() ?: return false
        var published = false
        return try {
            runCatching {
                val source = openSource() ?: error("无法读取所选图片")
                source.use { input -> staged.outputStream().use { output -> input.copyTo(output) } }
                check(isValidImage(staged)) { "所选文件不是有效图片" }
                check(updateConfig(staged.absolutePath)) { "图标配置未保存" }
                published = true
                previousPath?.takeIf { it != staged.absolutePath }?.let { path -> runCatching { File(path).delete() } }
            }.isSuccess
        } finally {
            if (!published) staged.delete()
        }
    }
}
