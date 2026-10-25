package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.UpdateInfo
import java.io.File
import java.security.MessageDigest

/** 镜像必须提供同一APK；每次完整校验后才把文件交给安装器。 */
internal fun downloadUpdate(
    info: UpdateInfo,
    directory: File,
    fetch: (String, File) -> Unit,
): File? {
    val file = File(directory, "update_${info.versionCode}.apk")
    val partial = File(directory, "update_${info.versionCode}.part")
    val result =
        (listOf(info.downloadUrl) + info.downloadUrls)
            .distinct()
            .asSequence()
            .map { url ->
                runCatching {
                    requireUpdateHttps(url)
                    fetch(url, partial)
                    require(partial.length() == info.sizeBytes && fileSha256(partial).equals(info.sha256, true)) {
                        "安装包大小或摘要校验失败"
                    }
                    partial.copyTo(file, overwrite = true)
                    file
                }.onFailure { InkLog.w("更新", "下载源不可用", it) }
            }.firstOrNull { it.isSuccess }
    partial.delete()
    return result?.getOrNull()
}

internal fun fileSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var count = input.read(buffer)
        while (count > 0) {
            digest.update(buffer, 0, count)
            count = input.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
