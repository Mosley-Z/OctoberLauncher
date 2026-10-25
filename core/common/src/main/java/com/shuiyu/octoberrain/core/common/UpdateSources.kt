package com.shuiyu.octoberrain.core.common

/** 正式更新源；用户指定地址和国内镜像优先，GitHub保留为默认及兜底。 */
object UpdateSources {
    const val GITHUB_MANIFEST_URL =
        "https://github.com/Mosley-Z/OctoberLauncher/releases/latest/download/update.json"

    fun manifests(
        primary: String,
        mirrors: List<String> = emptyList(),
    ): List<String> =
        (listOf(primary) + mirrors + GITHUB_MANIFEST_URL).map(String::trim).filter(String::isNotBlank).distinct()
}
