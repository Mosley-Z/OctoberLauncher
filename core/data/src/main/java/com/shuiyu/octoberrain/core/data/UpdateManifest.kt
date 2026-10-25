package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.UpdateInfo
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.URI

/** GitHub与国内镜像共用同一份清单格式，APK地址可独立回退。 */
@Serializable
internal data class UpdateManifest(
    val versionName: String,
    val versionCode: Int,
    val notes: String = "",
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val downloadUrls: List<String> = emptyList(),
) {
    fun info(): UpdateInfo {
        require(versionName.isNotBlank() && versionCode > 0) { "更新版本无效" }
        require(sizeBytes > 0 && sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "更新包校验信息无效" }
        (listOf(downloadUrl) + downloadUrls).forEach(::requireUpdateHttps)
        return UpdateInfo(versionName, versionCode, notes, downloadUrl, sha256, sizeBytes, downloadUrls)
    }
}

internal fun requireUpdateHttps(url: String) {
    val uri = URI(url)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "更新地址必须使用HTTPS" }
}

/** 首个成功清单决定版本；所有源均失败时抛出错误，避免把断网显示为最新版。 */
internal fun readUpdate(
    sources: List<String>,
    installed: Int,
    fetch: (String) -> String,
): UpdateInfo? {
    var lastFailure: Throwable? = null
    val result =
        sources
            .asSequence()
            .map { source ->
                runCatching {
                    requireUpdateHttps(source)
                    AppJson.format.decodeFromString<UpdateManifest>(fetch(source)).info().takeIf {
                        it.versionCode >
                            installed
                    }
                }.onFailure { lastFailure = it }
            }.firstOrNull { it.isSuccess }
    return if (result != null) result.getOrThrow() else throw IOException("所有更新源均不可用", lastFailure)
}
