package com.shuiyu.octoberrain.core.data

import android.content.Context
import android.content.Intent
import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.ChangelogEntry
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.UpdateInfo
import com.shuiyu.octoberrain.core.common.UpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * 在线更新实现：读取更新清单、下载安装包并触发安装。
 *
 * 清单地址来自集成配置；下载文件保存到缓存目录并校验摘要。
 * 安装通过系统安装器完成，需要用户确认。
 */
class UpdateServiceImpl(
    private val context: Context,
) : UpdateService {
    private val downloadDir = File(context.cacheDir, "updates").apply { mkdirs() }

    override suspend fun check(force: Boolean): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val url = AppServices.config.state.value.integrations.updateUrl
            if (url.isBlank()) return@withContext null
            runCatching {
                val text = httpGet(url)
                val remote = AppJson.format.decodeFromString<UpdatePayload>(text)
                val installed = installedVersionCode()
                if (!force && remote.versionCode <= installed) {
                    null
                } else {
                    UpdateInfo(
                        versionName = remote.versionName,
                        versionCode = remote.versionCode,
                        notes = remote.notes,
                        downloadUrl = remote.downloadUrl,
                        sha256 = remote.sha256,
                        sizeBytes = remote.sizeBytes,
                    )
                }
            }.onFailure { InkLog.w("更新", "检查更新失败", it) }.getOrNull()
        }

    override suspend fun download(info: UpdateInfo): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(downloadDir, "update_${info.versionCode}.apk")
                httpGetStream(info.downloadUrl) { input ->
                    java.io.FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                if (info.sha256.isNotBlank() && !sha256(file).equals(info.sha256, ignoreCase = true)) {
                    file.delete()
                    error("安装包校验失败")
                }
                file
            }.onFailure { InkLog.w("更新", "下载更新失败", it) }.getOrNull()
        }

    override fun install(file: File) {
        runCatching {
            val uri =
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.files",
                    file,
                )
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }.onFailure { InkLog.w("更新", "启动安装失败", it) }
    }

    override suspend fun changelog(): List<ChangelogEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("changelog.json").use { input ->
                    val text = input.readBytes().toString(Charsets.UTF_8)
                    AppJson.format.decodeFromString<List<ChangelogEntry>>(text)
                }
            }.onFailure { InkLog.w("更新", "读取更新日志失败", it) }.getOrDefault(emptyList())
        }

    /** 读取本机安装的版本号。 */
    private fun installedVersionCode(): Int =
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        }.getOrDefault(0)

    /** 读取文本内容，限时 15 秒。 */
    private fun httpGet(url: String): String {
        val connection = openConnection(url, 15000)
        try {
            val code = connection.responseCode
            require(code in 200..299) { "请求失败：HTTP $code" }
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    /** 流式下载，避免整包读入内存。 */
    private fun httpGetStream(
        url: String,
        sink: (InputStream) -> Unit,
    ) {
        val connection = openConnection(url, 60000)
        try {
            val code = connection.responseCode
            require(code in 200..299) { "下载失败：HTTP $code" }
            connection.inputStream.use(sink)
        } finally {
            connection.disconnect()
        }
    }

    /** 建立 HTTP 连接。 */
    private fun openConnection(
        url: String,
        readTimeout: Int,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            this.readTimeout = readTimeout
            requestMethod = "GET"
            instanceFollowRedirects = true
        }

    /** 计算文件摘要。 */
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }

    /** 更新清单结构。 */
    @Serializable
    private data class UpdatePayload(
        val versionName: String = "0.0.0",
        val versionCode: Int = 0,
        val notes: String = "",
        val downloadUrl: String = "",
        val sha256: String = "",
        val sizeBytes: Long = 0L,
    )
}
