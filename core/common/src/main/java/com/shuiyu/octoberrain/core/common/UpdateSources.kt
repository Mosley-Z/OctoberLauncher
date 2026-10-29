package com.shuiyu.octoberrain.core.common

/** 正式更新源；保留用户配置优先级，默认先GitHub，失败再产品站点。 */
object UpdateSources {
    const val GITHUB_MANIFEST_URL =
        "https://github.com/Mosley-Z/OctoberLauncher/releases/latest/download/update.json"

    const val PRODUCT_MANIFEST_URL = "https://wereadmoji.app.workbuddy.host/update-october.json"

    fun manifests(
        primary: String,
        mirrors: List<String> = emptyList(),
    ): List<String> =
        (
            listOf(
                primary,
            ) + mirrors +
                listOf(
                    GITHUB_MANIFEST_URL,
                    PRODUCT_MANIFEST_URL,
                )
        ).map(String::trim).filter(String::isNotBlank).distinct()
}
