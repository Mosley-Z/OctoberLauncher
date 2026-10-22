package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 微信读书阅读设置用例。
 *
 * 覆盖刷新间隔与数据来源的取值校验、旧配置与非法配置的回退、声明表条目、
 * 选项写入换算、生效方式分发与许可清单。
 */
class WereadReadingSettingsTest {
    @Test
    fun `刷新间隔越界时回退默认值`() {
        assertEquals(60, IntegrationsConfig(wereadRefreshMinutes = 45).effectiveWereadRefreshMinutes)
        assertEquals(60, IntegrationsConfig(wereadRefreshMinutes = 0).effectiveWereadRefreshMinutes)
        assertEquals(60, IntegrationsConfig(wereadRefreshMinutes = -30).effectiveWereadRefreshMinutes)
        assertEquals(60, IntegrationsConfig(wereadRefreshMinutes = 999).effectiveWereadRefreshMinutes)
    }

    @Test
    fun `刷新间隔合法值原样保留`() {
        IntegrationsConfig.WEREAD_REFRESH_OPTIONS.forEach { minutes ->
            assertEquals(
                minutes,
                IntegrationsConfig(wereadRefreshMinutes = minutes).effectiveWereadRefreshMinutes,
            )
        }
    }

    @Test
    fun `统计范围非法时回退月统计`() {
        assertEquals("month", IntegrationsConfig(wereadStatsRange = "week").effectiveWereadStatsRange)
        assertEquals("month", IntegrationsConfig(wereadStatsRange = "").effectiveWereadStatsRange)
        assertEquals("month", IntegrationsConfig(wereadStatsRange = "MONTH").effectiveWereadStatsRange)
    }

    @Test
    fun `统计范围合法值原样保留`() {
        IntegrationsConfig.WEREAD_STATS_OPTIONS.forEach { range ->
            assertEquals(range, IntegrationsConfig(wereadStatsRange = range).effectiveWereadStatsRange)
        }
    }

    @Test
    fun `旧配置缺失字段时取默认值`() {
        val json = """{"version":7,"integrations":{"wereadEnabled":true}}"""
        val config = AppJson.format.decodeFromString(LauncherConfig.serializer(), json)
        assertEquals(60, config.integrations.wereadRefreshMinutes)
        assertEquals("month", config.integrations.wereadStatsRange)
        assertEquals(60, config.integrations.effectiveWereadRefreshMinutes)
        assertEquals("month", config.integrations.effectiveWereadStatsRange)
    }

    @Test
    fun `解码后的非法取值按默认值读取`() {
        val json =
            """{"version":7,"integrations":{"wereadRefreshMinutes":120,"wereadStatsRange":"quarter"}}"""
        val config = AppJson.format.decodeFromString(LauncherConfig.serializer(), json)
        assertEquals(120, config.integrations.wereadRefreshMinutes)
        assertEquals("quarter", config.integrations.wereadStatsRange)
        assertEquals(60, config.integrations.effectiveWereadRefreshMinutes)
        assertEquals("month", config.integrations.effectiveWereadStatsRange)
    }

    @Test
    fun `旧书架设置声明全部移出设置应用`() {
        listOf(
            "bookshelf.localEnabled",
            "bookshelf.wereadEnabled",
            "bookshelf.rows.count",
            "bookshelf.open",
            "bookshelf.wereadStatsRange",
            "bookshelf.wereadRefreshMinutes",
            "bookshelf.wereadSource",
        ).forEach { assertNull(SettingsCatalog.specOf(it)) }
        assertTrue(SettingsCatalog.bookshelfGroups.isEmpty())
    }

    @Test
    fun `内容刷新生效方式调用缓存刷新与局部重绘`() {
        val calls = ArrayList<String>()
        SettingEffectDispatcher.dispatch(SettingEffect.CONTENT_REFRESH, RecordingHooks(calls))
        assertEquals(listOf("refreshContent", "partialRefresh"), calls)
    }

    @Test
    fun `其余生效方式的分发动作保持既有顺序`() {
        val calls = ArrayList<String>()
        SettingEffectDispatcher.dispatch(SettingEffect.NONE, RecordingHooks(calls))
        assertTrue(calls.isEmpty())
        SettingEffectDispatcher.dispatch(SettingEffect.APPEARANCE_REBUILD, RecordingHooks(calls))
        assertEquals(listOf("applyAppearance", "rebuildPage"), calls)
        calls.clear()
        SettingEffectDispatcher.dispatch(SettingEffect.REBUILD, RecordingHooks(calls))
        assertEquals(listOf("partialRefresh", "rebuildPage"), calls)
    }

    @Test
    fun `许可清单登记微读墨记条目`() {
        val entry = ThirdPartyLicenses.entries.firstOrNull { it.name == "WereadMoji" }
        assertNotNull(entry)
        assertEquals("WereadMoji — MIT — 2026 — Mosley-Z", entry?.summary)
        assertTrue(ThirdPartyLicenses.WEREAD_SOURCE_NOTICE.contains("MIT 许可"))
        assertTrue(ThirdPartyLicenses.WEREAD_SOURCE_NOTICE.contains("WereadMoji"))
    }

    /** 记录调用顺序的生效动作接收者。 */
    private class RecordingHooks(
        private val calls: MutableList<String>,
    ) : SettingEffectHooks {
        override fun partialRefresh() {
            calls += "partialRefresh"
        }

        override fun fullRefresh() {
            calls += "fullRefresh"
        }

        override fun applyAppearance() {
            calls += "applyAppearance"
        }

        override fun rebuildPage() {
            calls += "rebuildPage"
        }

        override fun refreshContent() {
            calls += "refreshContent"
        }
    }
}
