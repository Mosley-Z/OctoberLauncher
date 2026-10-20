package com.shuiyu.octoberrain.feature.settings

/**
 * 第三方许可条目。
 *
 * 参数 name 为项目名称；license 为许可协议简称；year 为版权年份；author 为版权持有者。
 */
internal data class LicenseEntry(
    val name: String,
    val license: String,
    val year: String,
    val author: String,
) {
    /** 单行摘要，形如「名称 — 许可 — 年份 — 作者」。 */
    val summary: String
        get() = "$name — $license — $year — $author"
}

/**
 * 第三方许可清单。
 *
 * 关于页按清单顺序展示；新增依赖时在此登记一行，许可原文保留在各依赖目录内。
 */
internal object ThirdPartyLicenses {
    /** 微信读书数据能力的来源声明，用于信息行的值列。 */
    const val WEREAD_SOURCE_NOTICE: String = "WereadMoji（MIT 许可）"

    /** 来源声明的完整说明，用于页面底部注释。 */
    const val WEREAD_SOURCE_DETAIL: String =
        "微信读书数据能力参考 WereadMoji（微读墨记，MIT 许可），许可原文见 weread-widget/LICENSE。"

    /** 许可条目，展示顺序即列表顺序。 */
    val entries: List<LicenseEntry> =
        listOf(
            LicenseEntry(name = "WereadMoji", license = "MIT", year = "2026", author = "Mosley-Z"),
        )
}
