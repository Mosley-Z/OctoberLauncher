package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.DEFAULT_HIDDEN_PACKAGES
import com.shuiyu.octoberrain.core.common.DEFAULT_NEGATIVE_MODULES
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.NEGATIVE_MODULE_ID_ALIASES
import com.shuiyu.octoberrain.core.common.V8_DEFAULT_NEGATIVE_MODULES
import com.shuiyu.octoberrain.core.common.rewriteRemovedWidgetIds

/**
 * 配置结构迁移。
 *
 * 负责把历史版本的 `LauncherConfig` 逐级升级到当前版本。迁移函数保持纯函数语义：
 * 不读写 SharedPreferences、不访问文件系统，便于单元测试直接构造输入与断言输出。
 *
 * 当前迁移链：
 * 1. v0/v1 到 v2：桌面长按被拆分为“长按菜单阈值”与“整理进入阈值”两个字段，
 *    旧配置只有 `longPressEditMs` 一个总时长，需要按以下规则拆解后写入新字段。
 * 2. v2 到 v3：桌面隐藏列表并入 S4 固件的文件管理与图墨桌面。
 * 3. v3 到 v4：书架默认只启用微信读书来源。
 * 4. v4 到 v5：桌面隐藏列表并入 KOReader 特殊入口。
 * 5. v5 到 v6：已有配置标记安装顺序已初始化。
 * 6. v6 到 v7：停止内置微信读书密钥自动回填，已保存的密钥保持不变。
 * 7. v7 到 v8：负一屏扩展为五模块，冗余组件标识改写为保留组件。
 * 8. v10 到 v11：书架书源从两个启用开关合并为负一屏的单一书源。
 */
object ConfigMigration {
    /** 第二版配置的版本号。 */
    private const val VERSION_V2 = 2

    /** 第三版配置的版本号：桌面默认隐藏系统文件管理与图墨桌面。 */
    private const val VERSION_V3 = 3

    /** 第四版配置的版本号：书架默认只启用微信读书来源。 */
    private const val VERSION_V4 = 4

    /** 第五版配置的版本号：桌面默认隐藏 KOReader 特殊入口。 */
    private const val VERSION_V5 = 5

    /** 第六版配置的版本号：已有配置跳过首次安装顺序初始化。 */
    private const val VERSION_V6 = 6

    /** 第七版配置的版本号：停止内置微信读书密钥的自动回填。 */
    private const val VERSION_V7 = 7

    /** 第八版配置的版本号：负一屏扩展为五模块并删除冗余组件。 */
    private const val VERSION_V8 = 8

    /** 第九版配置的版本号：负一屏模块标识迁移。 */
    private const val VERSION_V9 = 9

    /** 第十版配置的版本号：微读数据来源标记，默认按导入来源处理。 */
    private const val VERSION_V10 = 10

    /** 第十一版配置的版本号：书架书源合并为负一屏的单一书源。 */
    private const val VERSION_V11 = 11

    private const val VERSION_V13 = 13

    /**
     * 第八版之前的负一屏模块标识。
     *
     * weread_assets 为旧版墨台资产卡片标识，用户曾启用时保留在列表末尾；
     * 其余新增模块由 V8_DEFAULT_NEGATIVE_MODULES 提供默认顺序。
     */
    private val LEGACY_NEGATIVE_MODULES: List<String> = listOf("weread_assets")

    /** 旧总时长不小于该阈值时，采用新版默认的菜单阈值与整理阈值。 */
    private const val LEGACY_THRESHOLD_MS = 800

    /** 新版默认的长按菜单阈值。 */
    private const val DEFAULT_MENU_MS = 400

    /** 新版默认的进入整理阈值。 */
    private const val DEFAULT_EDIT_HOLD_MS = 1200

    /**
     * 把任意历史版本的配置迁移到当前版本。
     *
     * 迁移只改动结构变化涉及的字段，不写入、不删除用户配置的微信读书密钥；
     * v7 起密钥仅由用户在设置页填写，空值保持空值。
     *
     * @param config 从磁盘或备份文件读出的配置。
     * @param shelfSourcePresent 原始 JSON 是否显式保存书源，避免默认值掩盖字段缺失。
     * @return 迁移后的配置；版本已是最新或更高时原样返回。
     */
    fun migrate(
        config: LauncherConfig,
        shelfSourcePresent: Boolean = false,
        desktopLayoutFields: Set<String> = emptySet(),
    ): LauncherConfig {
        if (config.version > LauncherConfig.CURRENT_VERSION) return config
        var migrated = config
        if (migrated.version < VERSION_V2) {
            migrated = migrateV1ToV2(migrated)
        }
        if (migrated.version < VERSION_V3) {
            migrated = migrateV2ToV3(migrated)
        }
        if (migrated.version < VERSION_V4) {
            migrated = migrateV3ToV4(migrated)
        }
        if (migrated.version < VERSION_V5) {
            migrated = migrateV4ToV5(migrated)
        }
        if (migrated.version < VERSION_V6) {
            migrated = migrateV5ToV6(migrated)
        }
        if (migrated.version < VERSION_V7) {
            migrated = migrateV6ToV7(migrated)
        }
        if (migrated.version < VERSION_V8) {
            migrated = migrateV7ToV8(migrated)
        }
        if (migrated.version < VERSION_V9) {
            migrated = migrateV8ToV9(migrated)
        }
        if (migrated.version < VERSION_V10) {
            migrated = migrateV9ToV10(migrated)
        }
        if (migrated.version < VERSION_V11) {
            migrated = migrateShelfSource(migrated, shelfSourcePresent)
        }
        // v13 removes the independent shelf scheduler; unknown legacy fields are dropped on decode/save.
        if (migrated.version < VERSION_V13) {
            migrated = migrated.copy(version = VERSION_V13)
        }
        migrated = DesktopLayoutMigration.migrate(migrated, desktopLayoutFields)
        migrated = FilesEntryMigration.migrate(migrated)
        migrated = RetiredIntegrations.sanitize(migrated).copy(version = LauncherConfig.CURRENT_VERSION)
        return if (migrated == config) config else migrated
    }

    /**
     * 把 v2 升级到 v3：把默认隐藏的系统应用并入用户的隐藏列表。
     *
     * 旧配置的隐藏列表默认为空，升级后桌面不再出现 S4 原生文件管理与图墨桌面；
     * 用户此前手工隐藏的包名保留，恢复显示可在设置中操作。
     *
     * @param config 版本号为 2 的配置。
     * @return 版本号为 3、隐藏列表已补齐的配置。
     */
    private fun migrateV2ToV3(config: LauncherConfig): LauncherConfig =
        config.copy(
            version = VERSION_V3,
            desktop =
                config.desktop.copy(
                    hiddenPackages = config.desktop.hiddenPackages + DEFAULT_HIDDEN_PACKAGES,
                ),
        )

    /**
     * 把 v3 升级到 v4：书架默认只启用微信读书来源。
     *
     * 旧版本两个来源都默认开启，升级后按新的产品默认关闭本地来源；
     * 微信读书来源保持开启，用户仍可在书架设置中重新打开本地目录。
     *
     * @param config 版本号为 3 的配置。
     * @return 版本号为 4、书架来源已按新默认调整的配置。
     */
    private fun migrateV3ToV4(config: LauncherConfig): LauncherConfig =
        config.copy(
            version = VERSION_V4,
            integrations =
                config.integrations.copy(
                    bookshelfLocalEnabled = false,
                    bookshelfWereadEnabled = true,
                ),
        )

    /**
     * 把 v4 升级到 v5：把 KOReader 入口并入默认隐藏列表。
     *
     * 桌面只保留设置等必要入口，KOReader 不再出现在桌面；用户可在设置的隐藏应用中恢复。
     *
     * @param config 版本号为 4 的配置。
     * @return 版本号为 5、特殊入口隐藏列表已补齐的配置。
     */
    private fun migrateV4ToV5(config: LauncherConfig): LauncherConfig =
        config.copy(
            version = VERSION_V5,
            desktop =
                config.desktop.copy(
                    hiddenSpecials = config.desktop.hiddenSpecials + "special:koreader",
                ),
        )

    /**
     * 把 v5 升级到 v6：已有配置跳过首次安装顺序初始化。
     *
     * 首次安装顺序按安装时间升序固定一次，属于新装用户的初始体验；升级用户已有自定义
     * 顺序，迁移时直接标记为已初始化，避免升级后被重新排序。
     *
     * @param config 版本号为 5 的配置。
     * @return 版本号为 6、安装顺序已标记的配置。
     */
    private fun migrateV5ToV6(config: LauncherConfig): LauncherConfig =
        config.copy(
            version = VERSION_V6,
            desktop = config.desktop.copy(installOrderInitialized = true),
        )

    /**
     * 把 v6 升级到 v7：停止内置微信读书密钥的自动回填。
     *
     * v6 及更早版本在密钥为空时自动写入内置密钥并启用集成，导致密钥随安装包与
     * 备份文件外流。v7 起密钥只由用户填写，迁移仅更新版本号：已保存的密钥原样
     * 保留，空密钥保持空值，由设置页提示用户配置。
     *
     * @param config 版本号为 6 的配置。
     * @return 版本号为 7、集成配置保持原值的配置。
     */
    private fun migrateV6ToV7(config: LauncherConfig): LauncherConfig = config.copy(version = VERSION_V7)

    /**
     * 把 v7 升级到 v8：负一屏扩展为五模块并删除冗余组件。
     *
     * 负一屏按新版默认顺序补齐五项模块，用户此前启用且仍在新版范围内的模块沿原顺序保留，
     * 旧版独有模块追加到末尾；一个都没有时回落到默认顺序。桌面当前组件若命中删除清单，
     * 微信读书类改写为整体卡片，其余改写为钟面，避免升级后停在不存在的组件上。
     * 迁移同时标记模块顺序已初始化，后续不再按默认顺序重排。
     *
     * @param config 版本号为 7 的配置。
     * @return 版本号为 8、负一屏模块与桌面组件已按新清单调整的配置。
     */
    private fun migrateV7ToV8(config: LauncherConfig): LauncherConfig {
        val defaults = V8_DEFAULT_NEGATIVE_MODULES
        val known = defaults + LEGACY_NEGATIVE_MODULES
        val kept =
            config.desktop.negativePage.modules
                .filter { it in known }
        val merged =
            (defaults.filter { it in kept } + (kept - defaults.toSet()))
                .ifEmpty { defaults }
        val desktop =
            rewriteRemovedWidgetIds(config.desktop).copy(
                negativePage =
                    config.desktop.negativePage.copy(
                        modules = merged,
                        cardOrderInitialized = true,
                    ),
            )
        return config.copy(
            version = VERSION_V8,
            desktop = desktop,
        )
    }

    /**
     * 把 v8 升级到 v9：负一屏模块标识改用微读墨记墨台标识。
     *
     * 旧标识按 NEGATIVE_MODULE_ID_ALIASES 映射为 bill、rank、fp，映射结果去重后
     * 按新版默认顺序排列，用户自行追加的其他模块保留在末尾。与 v7 到 v8 一致，
     * 迁移只重排已启用模块，不补齐用户未启用的默认项；用户若保持 v8 的五项默认
     * 未做改动，则视为未自定义，回落为 v9 的四项默认（阅读指纹默认关闭）。
     *
     * @param config 版本号为 8 的配置。
     * @return 版本号为 9、负一屏模块标识已改写的配置。
     */
    private fun migrateV8ToV9(config: LauncherConfig): LauncherConfig {
        val page = config.desktop.negativePage
        val mapped = page.modules.map { NEGATIVE_MODULE_ID_ALIASES[it] ?: it }.distinct()
        val defaults = DEFAULT_NEGATIVE_MODULES
        val legacyDefaults =
            V8_DEFAULT_NEGATIVE_MODULES.map { NEGATIVE_MODULE_ID_ALIASES[it] ?: it }.distinct()
        val merged =
            when {
                mapped == legacyDefaults -> defaults
                else ->
                    (defaults.filter { it in mapped } + (mapped - defaults.toSet()))
                        .ifEmpty { defaults }
            }
        return config.copy(
            version = VERSION_V9,
            desktop = config.desktop.copy(negativePage = page.copy(modules = merged)),
        )
    }

    /**
     * 把 v9 升级到 v10：标记微读数据来源。
     *
     * 已有配置文件来自微读墨记共存的导入路径，来源记为 imported；
     * 未装微读墨记的用户在首次取数成功后由同步层改写为 local。
     *
     * @param config 版本号为 9 的配置。
     * @return 版本号为 10、微读数据来源已标记的配置。
     */
    private fun migrateV9ToV10(config: LauncherConfig): LauncherConfig =
        config.copy(
            version = VERSION_V10,
            integrations =
                config.integrations.copy(
                    wereadSource = IntegrationsConfig.WEREAD_SOURCE_DEFAULT,
                ),
        )

    /**
     * 把 v1 的单一长按时长拆分为菜单阈值与整理阈值。
     *
     * 旧值不小于 800 毫秒时使用新版默认值 400 与 1200；旧值更短时说明用户习惯快速
     * 触发，按“菜单取一半、整理取原值”拆分，保留该用户的操作节奏。
     *
     * @param config 版本号小于 2 的配置。
     * @return 版本号为 2、桌面长按字段已拆分的配置。
     */
    private fun migrateV1ToV2(config: LauncherConfig): LauncherConfig {
        val legacyHoldMs = config.desktop.longPressEditMs
        val menuMs: Int
        val editHoldMs: Int
        if (legacyHoldMs < LEGACY_THRESHOLD_MS) {
            menuMs = (legacyHoldMs / 2).coerceAtLeast(1)
            editHoldMs = legacyHoldMs.coerceAtLeast(2)
        } else {
            menuMs = DEFAULT_MENU_MS
            editHoldMs = DEFAULT_EDIT_HOLD_MS
        }
        return config.copy(
            version = VERSION_V2,
            desktop =
                config.desktop.copy(
                    longPressMenuMs = menuMs,
                    editHoldMs = editHoldMs,
                ),
        )
    }
}
