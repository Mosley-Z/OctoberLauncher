package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.DEFAULT_HIDDEN_PACKAGES
import com.shuiyu.octoberrain.core.common.DEFAULT_NEGATIVE_MODULES
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.NegativePageConfig
import com.shuiyu.octoberrain.core.common.WEREAD_BOARD_WIDGET_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ConfigMigration 的配置升级单元测试。
 *
 * 迁移函数不读写存储，测试直接构造历史版本配置并断言拆分后的长按阈值。
 */
class ConfigMigrationTest {
    /** 构造指定版本与旧长按时长的配置。 */
    private fun legacy(
        version: Int,
        longPressEditMs: Int,
    ): LauncherConfig =
        LauncherConfig(
            version = version,
            desktop = DesktopConfig(longPressEditMs = longPressEditMs),
        )

    /** 旧阈值不小于 800 毫秒时，按新版默认值拆分为 400 与 1200。 */
    @Test
    fun `旧阈值不小于阈值时采用新版默认`() {
        val migrated = ConfigMigration.migrate(legacy(version = 1, longPressEditMs = 2000))
        assertEquals(LauncherConfig.CURRENT_VERSION, migrated.version)
        assertEquals(400, migrated.desktop.longPressMenuMs)
        assertEquals(1200, migrated.desktop.editHoldMs)
    }

    /** 旧阈值小于 800 毫秒时，菜单阈值取一半、整理阈值取原值。 */
    @Test
    fun `旧阈值较小时按比例拆分`() {
        val migrated = ConfigMigration.migrate(legacy(version = 1, longPressEditMs = 500))
        assertEquals(LauncherConfig.CURRENT_VERSION, migrated.version)
        assertEquals(250, migrated.desktop.longPressMenuMs)
        assertEquals(500, migrated.desktop.editHoldMs)
    }

    /** 阈值边界为 800：取 800 走默认值，取 799 走比例拆分。 */
    @Test
    fun `阈值边界两侧规则不同`() {
        val atBoundary = ConfigMigration.migrate(legacy(version = 1, longPressEditMs = 800))
        assertEquals(400, atBoundary.desktop.longPressMenuMs)
        assertEquals(1200, atBoundary.desktop.editHoldMs)

        val belowBoundary = ConfigMigration.migrate(legacy(version = 1, longPressEditMs = 799))
        assertEquals(399, belowBoundary.desktop.longPressMenuMs)
        assertEquals(799, belowBoundary.desktop.editHoldMs)
    }

    /** 极小的旧阈值仍保证菜单阈值与整理阈值不小于下限。 */
    @Test
    fun `极小旧阈值不低于下限`() {
        val zero = ConfigMigration.migrate(legacy(version = 1, longPressEditMs = 0))
        assertEquals(1, zero.desktop.longPressMenuMs)
        assertEquals(2, zero.desktop.editHoldMs)

        val one = ConfigMigration.migrate(legacy(version = 1, longPressEditMs = 1))
        assertEquals(1, one.desktop.longPressMenuMs)
        assertEquals(2, one.desktop.editHoldMs)
    }

    /** 版本号为 0 的历史配置同样执行同一套拆分规则。 */
    @Test
    fun `零版本执行迁移`() {
        val migrated = ConfigMigration.migrate(legacy(version = 0, longPressEditMs = 1000))
        assertEquals(LauncherConfig.CURRENT_VERSION, migrated.version)
        assertEquals(400, migrated.desktop.longPressMenuMs)
        assertEquals(1200, migrated.desktop.editHoldMs)
    }

    /** 已是最新版本时原样返回同一个对象。 */
    @Test
    fun `最新版本配置原样返回`() {
        val config = legacy(version = LauncherConfig.CURRENT_VERSION, longPressEditMs = 2000)
        assertSame(config, ConfigMigration.migrate(config))
    }

    /** 高于当前版本的配置原样返回，版本号与可读字段均不被改写。 */
    @Test
    fun `高于当前版本配置原样返回`() {
        val config =
            LauncherConfig(
                version = LauncherConfig.CURRENT_VERSION + 1,
                desktop = DesktopConfig(columns = 5),
            )
        val migrated = ConfigMigration.migrate(config)
        assertSame(config, migrated)
        assertEquals(LauncherConfig.CURRENT_VERSION + 1, migrated.version)
        assertEquals(5, migrated.desktop.columns)
    }

    /** 迁移只改动长按字段与版本号，其余配置保持原值。 */
    @Test
    fun `迁移保留其余配置字段`() {
        val config =
            LauncherConfig(
                version = 1,
                desktop =
                    DesktopConfig(
                        longPressEditMs = 500,
                        columns = 4,
                        rows = 6,
                        pageLimit = 8,
                        hiddenPackages = setOf("com.example.hidden"),
                    ),
                appearance = AppearanceConfig(themeId = "night", textScale = 1.3f),
            )
        val migrated = ConfigMigration.migrate(config)
        assertEquals(4, migrated.desktop.columns)
        assertEquals(6, migrated.desktop.rows)
        assertEquals(8, migrated.desktop.pageLimit)
        assertEquals(setOf("com.example.hidden") + DEFAULT_HIDDEN_PACKAGES, migrated.desktop.hiddenPackages)
        assertEquals("night", migrated.appearance.themeId)
        assertEquals(1.3f, migrated.appearance.textScale, 0.0001f)
        assertEquals(250, migrated.desktop.longPressMenuMs)
        assertEquals(500, migrated.desktop.editHoldMs)
    }

    /** 构造指定版本与微信读书配置的配置。 */
    private fun weread(
        version: Int,
        key: String,
        enabled: Boolean,
    ): LauncherConfig =
        LauncherConfig(
            version = version,
            integrations = IntegrationsConfig(wereadApiKey = key, wereadEnabled = enabled),
        )

    /** v6 空密钥升级到 v7 时只更新版本号，不写入密钥、不改动启用状态。 */
    @Test
    fun `v6 空密钥升级不写入密钥`() {
        val migrated = ConfigMigration.migrate(weread(version = 6, key = "", enabled = true))
        assertEquals(LauncherConfig.CURRENT_VERSION, migrated.version)
        assertEquals("", migrated.integrations.wereadApiKey)
        assertTrue(migrated.integrations.wereadEnabled)
    }

    /** v6 已保存的密钥升级到 v7 后保持原值，往返迁移同样保持。 */
    @Test
    fun `v6 已保存密钥往返迁移保持原值`() {
        val key = "wrk-user-configured-key"
        val migrated = ConfigMigration.migrate(weread(version = 6, key = key, enabled = true))
        assertEquals(LauncherConfig.CURRENT_VERSION, migrated.version)
        assertEquals(key, migrated.integrations.wereadApiKey)
        val roundTrip = ConfigMigration.migrate(migrated.copy(version = 6))
        assertEquals(LauncherConfig.CURRENT_VERSION, roundTrip.version)
        assertEquals(migrated.integrations, roundTrip.integrations)
    }

    /** v6 关闭集成且密钥为空时，升级不自动启用集成。 */
    @Test
    fun `v6 空密钥升级不自动启用`() {
        val migrated = ConfigMigration.migrate(weread(version = 6, key = "", enabled = false))
        assertFalse(migrated.integrations.wereadEnabled)
    }

    /** 更早版本的空密钥同样保持空值，不再由迁移回填。 */
    @Test
    fun `旧版本空密钥升级不写入密钥`() {
        val migrated = ConfigMigration.migrate(weread(version = 1, key = "", enabled = false))
        assertEquals(LauncherConfig.CURRENT_VERSION, migrated.version)
        assertEquals("", migrated.integrations.wereadApiKey)
        assertFalse(migrated.integrations.wereadEnabled)
    }

    /** 构造指定版本与负一屏模块列表的配置。 */
    private fun negative(
        version: Int,
        modules: List<String>,
        widgetId: String = "clock",
    ): LauncherConfig =
        LauncherConfig(
            version = version,
            desktop =
                DesktopConfig(
                    widgetId = widgetId,
                    negativePage = NegativePageConfig(modules = modules),
                ),
        )

    /** v7 只有书架一张卡片时，升级到 v8 保留原启用集合并标记已初始化。 */
    @Test
    fun `v7 仅书架升级保留原模块`() {
        val migrated = ConfigMigration.migrate(negative(7, listOf("bookshelf")))
        assertEquals(LauncherConfig.CURRENT_VERSION, migrated.version)
        assertEquals(listOf("bookshelf"), migrated.desktop.negativePage.modules)
        assertTrue(migrated.desktop.negativePage.cardOrderInitialized)
    }

    /** 用户保留的模块按新版默认顺序排列，未知模块被丢弃。 */
    @Test
    fun `v7 模块按新版默认顺序重排`() {
        val migrated =
            ConfigMigration.migrate(negative(7, listOf("negative_files", "bookshelf", "unknown_card")))
        assertEquals(
            listOf("bookshelf", "negative_files"),
            migrated.desktop.negativePage.modules,
        )
    }

    /** 旧版分项资产模块在 v9 并入阅读资产，位置按新默认顺序排列。 */
    @Test
    fun `v7 旧版模块追加到末尾`() {
        val migrated = ConfigMigration.migrate(negative(7, listOf("bookshelf", "weread_assets")))
        assertEquals(listOf("bookshelf", "rank"), migrated.desktop.negativePage.modules)
    }

    /** 负一屏模块全部不在新版范围内时回落到默认顺序，避免空白负一屏。 */
    @Test
    fun `v7 模块全部失效回落默认`() {
        val migrated = ConfigMigration.migrate(negative(7, listOf("removed_card")))
        assertEquals(DEFAULT_NEGATIVE_MODULES, migrated.desktop.negativePage.modules)
    }

    /** 微信读书类组件升级时改写为整体卡片。 */
    @Test
    fun `v7 微信读书类组件改写为整体卡片`() {
        val migrated = ConfigMigration.migrate(negative(7, listOf("bookshelf"), widgetId = "weread_bill"))
        assertEquals(WEREAD_BOARD_WIDGET_ID, migrated.desktop.widgetId)
    }

    /** 其余删除组件升级时改写为钟面，保留组件保持原值。 */
    @Test
    fun `v7 冗余组件改写为钟面`() {
        val weatherMigrated = ConfigMigration.migrate(negative(7, listOf("bookshelf"), widgetId = "weather"))
        assertEquals("clock", weatherMigrated.desktop.widgetId)

        val clockMigrated = ConfigMigration.migrate(negative(7, listOf("bookshelf"), widgetId = "clock"))
        assertEquals("clock", clockMigrated.desktop.widgetId)
    }

    /** 已是当前版本的配置原样返回，迁移幂等。 */
    @Test
    fun `v8 配置迁移幂等`() {
        val current = negative(LauncherConfig.CURRENT_VERSION, DEFAULT_NEGATIVE_MODULES)
        val migrated = ConfigMigration.migrate(current)
        assertSame(current, migrated)
    }

    /** v8 的旧模块标识按别名改写为墨台标识，顺序按新版默认排列。 */
    @Test
    fun `v8 旧模块标识改写为新标识`() {
        val migrated =
            ConfigMigration.migrate(
                negative(8, listOf("weread_fingerprint", "bookshelf", "weread_bill")),
            )
        assertEquals(listOf("bookshelf", "bill", "fp"), migrated.desktop.negativePage.modules)
    }

    /** 映射后出现重复标识时只保留一个。 */
    @Test
    fun `v8 映射后去重`() {
        val migrated =
            ConfigMigration.migrate(negative(8, listOf("weread_bill", "bill", "weread_rank")))
        assertEquals(listOf("bill", "rank"), migrated.desktop.negativePage.modules)
    }

    /** 用户自行追加的未知模块在映射后仍排在末尾。 */
    @Test
    fun `v8 自定义模块保留末尾`() {
        val migrated = ConfigMigration.migrate(negative(8, listOf("bookshelf", "custom_card")))
        assertEquals(listOf("bookshelf", "custom_card"), migrated.desktop.negativePage.modules)
    }

    /** v9 配置升级后微读数据来源标记为导入。 */
    @Test
    fun `v9 升级标记数据来源为导入`() {
        val migrated = ConfigMigration.migrate(negative(9, DEFAULT_NEGATIVE_MODULES))
        assertEquals(LauncherConfig.CURRENT_VERSION, migrated.version)
        assertEquals(
            IntegrationsConfig.WEREAD_SOURCE_DEFAULT,
            migrated.integrations.wereadSource,
        )
    }

    /** 当前版本配置再次迁移时不改动数据来源标记。 */
    @Test
    fun `v10 配置迁移幂等`() {
        val current =
            negative(LauncherConfig.CURRENT_VERSION, DEFAULT_NEGATIVE_MODULES)
                .copy(integrations = IntegrationsConfig(wereadSource = "local"))
        val migrated = ConfigMigration.migrate(current)
        assertEquals("local", migrated.integrations.wereadSource)
        assertEquals(LauncherConfig.CURRENT_VERSION, migrated.version)
    }
}
