package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_BOOKSHELF_ENTRY
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_BOOKSHELF_LOCAL
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_BOOKSHELF_ROWS
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_BOOKSHELF_SOURCE
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_BOOKSHELF_WEREAD

/**
 * 书架设置页的设置项声明。
 *
 * 微信读书密钥校验、缓存说明与前往书架由页面以动作和自定义行提供。
 */
internal object BookshelfSpecs {
    /** 书架每页行数候选值，与内容层读取时的 1–6 钳制一致。 */
    private val ROWS_VALUES: List<Int> = listOf(1, 2, 3, 4, 5, 6)

    /** 书架每页行数的显示文本。 */
    private val ROWS_OPTIONS: List<String> = ROWS_VALUES.map { "$it 行" }

    /** 微信读书刷新间隔的显示文本，顺序与取值表一致。 */
    private val REFRESH_LABELS: List<String> = listOf("30 分钟", "1 小时", "3 小时", "6 小时")

    /** 微信读书统计范围的显示文本，顺序与取值表一致。 */
    private val STATS_LABELS: List<String> = listOf("月统计", "年统计", "全部")

    /** 数据来源的显示文本，顺序与取值表一致。 */
    private val WEREAD_SOURCE_LABELS: List<String> = listOf("独立同步", "仅本地缓存")

    private val WEREAD_SOURCE_VALUES: List<String> = listOf("synced", "local")

    val groups: List<SettingsGroupSpec> =
        listOf(
            SettingsGroupSpec(id = GROUP_BOOKSHELF_SOURCE, title = "书籍来源", subtitle = "书架同时支持本地文件与微信读书"),
            SettingsGroupSpec(id = GROUP_BOOKSHELF_WEREAD, title = "微信读书", subtitle = "接口密钥与数据缓存"),
            SettingsGroupSpec(id = GROUP_BOOKSHELF_ROWS, title = "书架行数", subtitle = "书架与负一屏每页显示的书籍行数"),
            SettingsGroupSpec(id = GROUP_BOOKSHELF_LOCAL, title = "本地目录", subtitle = "设备存储根目录下的书籍文件夹"),
            SettingsGroupSpec(
                id = GROUP_BOOKSHELF_ENTRY,
                title = "书架入口",
                subtitle = "书架位于桌面负一屏",
                notes = listOf("书籍来源只影响书架与负一屏的收录范围，阅读进度仍按各自来源独立保存。"),
            ),
        )

    val specs: List<SettingSpec> =
        listOf(
            SettingSpec(
                id = "bookshelf.localEnabled",
                group = GROUP_BOOKSHELF_SOURCE,
                title = "本地目录",
                hint = "扫描指定目录中的 EPUB 与 PDF 文件",
                control =
                    toggleIntegrations(
                        read = { it.bookshelfLocalEnabled },
                        write = { integrations, value -> integrations.copy(bookshelfLocalEnabled = value) },
                    ),
            ),
            SettingSpec(
                id = "bookshelf.wereadEnabled",
                group = GROUP_BOOKSHELF_SOURCE,
                title = "微信读书",
                hint = "展示微信读书书架、月统计与最新摘录",
                control =
                    toggleIntegrations(
                        read = { it.bookshelfWereadEnabled },
                        write = { integrations, value -> integrations.copy(bookshelfWereadEnabled = value) },
                    ),
            ),
            SettingSpec(
                id = "bookshelf.wereadSource",
                group = GROUP_BOOKSHELF_WEREAD,
                title = "数据来源",
                hint = "独立同步通过微信读书官方接口更新数据；仅本地缓存暂停联网刷新",
                control =
                    choiceIntegrations(
                        options = WEREAD_SOURCE_LABELS,
                        read = { integrations ->
                            WEREAD_SOURCE_VALUES
                                .indexOf(integrations.effectiveWereadSource)
                                .coerceAtLeast(0)
                        },
                        write = { integrations, index ->
                            integrations.copy(
                                wereadSource = WEREAD_SOURCE_VALUES[index],
                            )
                        },
                    ),
                effect = SettingEffect.CONTENT_REFRESH,
            ),
            SettingSpec(
                id = "bookshelf.wereadRefresh",
                group = GROUP_BOOKSHELF_WEREAD,
                title = "刷新微信读书缓存",
                control = SettingControl.Button(),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "bookshelf.wereadCacheNote",
                group = GROUP_BOOKSHELF_WEREAD,
                title = "缓存说明",
                control = SettingControl.Custom(key = "bookshelf.wereadCacheNote"),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "bookshelf.wereadRefreshMinutes",
                group = GROUP_BOOKSHELF_WEREAD,
                title = "刷新间隔",
                hint = "微信读书书架与统计的缓存有效期",
                control =
                    choiceIntegrations(
                        options = REFRESH_LABELS,
                        read = { integrations ->
                            IntegrationsConfig.WEREAD_REFRESH_OPTIONS
                                .indexOf(integrations.effectiveWereadRefreshMinutes)
                                .coerceAtLeast(0)
                        },
                        write = { integrations, index ->
                            integrations.copy(
                                wereadRefreshMinutes = IntegrationsConfig.WEREAD_REFRESH_OPTIONS[index],
                            )
                        },
                    ),
                effect = SettingEffect.CONTENT_REFRESH,
            ),
            SettingSpec(
                id = "bookshelf.wereadStatsRange",
                group = GROUP_BOOKSHELF_WEREAD,
                title = "统计范围",
                hint = "微信读书统计页面打开时的默认周期",
                control =
                    choiceIntegrations(
                        options = STATS_LABELS,
                        read = { integrations ->
                            IntegrationsConfig.WEREAD_STATS_OPTIONS
                                .indexOf(integrations.effectiveWereadStatsRange)
                                .coerceAtLeast(0)
                        },
                        write = { integrations, index ->
                            integrations.copy(
                                wereadStatsRange = IntegrationsConfig.WEREAD_STATS_OPTIONS[index],
                            )
                        },
                    ),
                effect = SettingEffect.PARTIAL,
            ),
            SettingSpec(
                id = "bookshelf.wereadSyncNow",
                group = GROUP_BOOKSHELF_WEREAD,
                title = "立即同步数据",
                hint = "用已配置密钥直连接口，把书架、统计、账单与资产写入本机数据目录",
                control = SettingControl.Button(),
                effect = SettingEffect.CONTENT_REFRESH,
            ),
            SettingSpec(
                id = "bookshelf.rows.count",
                group = GROUP_BOOKSHELF_ROWS,
                title = "每页行数",
                control =
                    stepperIntegrations(
                        options = ROWS_OPTIONS,
                        read = { integrations ->
                            ROWS_VALUES.indexOf(integrations.bookshelfRows).takeIf { it >= 0 } ?: 0
                        },
                        write = { integrations, index -> integrations.copy(bookshelfRows = ROWS_VALUES[index]) },
                    ),
                effect = SettingEffect.FULL,
            ),
            SettingSpec(
                id = "bookshelf.booksDir",
                group = GROUP_BOOKSHELF_LOCAL,
                title = "目录名称",
                hint = "修改后书架按新目录重新扫描",
                control = SettingControl.Action(value = { config -> config.integrations.booksDir }),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "bookshelf.open",
                group = GROUP_BOOKSHELF_ENTRY,
                title = "前往书架",
                hint = "也可在桌面首页继续右滑进入",
                control = SettingControl.Action(value = { "打开展示" }),
                effect = SettingEffect.NONE,
            ),
        )
}
