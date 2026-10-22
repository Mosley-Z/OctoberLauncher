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
    val asset: String? = null,
) {
    /** 单行摘要，形如「名称 — 许可 — 年份 — 作者」。 */
    val summary: String
        get() = listOf(name, license, year, author).filter { it.isNotBlank() }.joinToString(" — ")
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
            LicenseEntry(
                "AndroidX WebKit 1.12.1",
                "Apache-2.0",
                "",
                "Android Open Source Project",
                "reader/licenses/androidx-webkit.txt",
            ),
            LicenseEntry(
                "commonmark-java 0.21.0 / GFM tables",
                "BSD-2-Clause",
                "2015",
                "Atlassian Pty Ltd",
                "reader/licenses/commonmark.txt",
            ),
            LicenseEntry("jsoup 1.23.2", "MIT", "2009–2026", "Jonathan Hedley", "reader/licenses/jsoup.txt"),
            LicenseEntry(
                "docx-preview 0.4.0 / JSZip 3.10.1",
                "Apache-2.0 / MIT",
                "",
                "Volodymyr Baydalka / Stuart Knightley / pako contributors",
                "reader/licenses/docx.txt",
            ),
            LicenseEntry(
                "SheetJS CE 0.20.3",
                "Apache-2.0",
                "",
                "SheetJS LLC",
                "reader/web/sheets/LICENSES.txt",
            ),
            LicenseEntry(
                "PPTXjs 1.21.1 / jQuery / JSZip / D3 / NVD3",
                "MIT / BSD-3-Clause / Apache-2.0",
                "",
                "PPTXjs and dependency contributors",
                "reader/web/slides/LICENSES.txt",
            ),
            LicenseEntry(
                "desugar_jdk_libs 2.1.3",
                "GPL-2.0 + Classpath Exception",
                "",
                "OpenJDK / Google",
                "reader/licenses/desugar-jdk.txt",
            ),
        )
}
