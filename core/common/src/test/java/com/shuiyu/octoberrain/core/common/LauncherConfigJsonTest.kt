package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配置序列化契约单元测试。
 *
 * 配置结构与备份文件直接对应，字段缺失、未知字段与默认值写回都会影响旧版本
 * 配置的读取结果，因此以 JSON 往返与兼容读取作为断言对象。
 */
class LauncherConfigJsonTest {
    /** 默认配置编码后解码应完全一致。 */
    @Test
    fun `默认配置往返保持字段`() {
        val config = LauncherConfig()
        val restored =
            AppJson.format.decodeFromString<LauncherConfig>(
                AppJson.format.encodeToString(config),
            )
        assertEquals(config, restored)
    }

    /** 各 section 的修改值在往返后保持不变。 */
    @Test
    fun `修改后的配置往返保持字段`() {
        val config =
            LauncherConfig(
                version = LauncherConfig.CURRENT_VERSION,
                desktop =
                    DesktopConfig(
                        columns = 4,
                        rows = 6,
                        pageLimit = 8,
                        longPressMenuMs = 500,
                        editHoldMs = 1500,
                        hiddenPackages = setOf("com.example.hidden"),
                    ),
                appearance =
                    AppearanceConfig(
                        themeId = "night",
                        textScale = 1.25f,
                        iconShape = IconShape.PILL,
                        iconContrast = 2,
                        twelveHour = true,
                        wallpaperDim = 0.5f,
                    ),
                notifications = NotificationConfig(foldByApp = true, badgeMax = 9),
            )
        val restored =
            AppJson.format.decodeFromString<LauncherConfig>(
                AppJson.format.encodeToString(config),
            )
        assertEquals(config, restored)
    }

    /** 编码写出默认值，未修改的字段也出现在 JSON 中，便于人工核对备份文件。 */
    @Test
    fun `编码包含默认值`() {
        val encoded = AppJson.format.encodeToString(LauncherConfig())
        assertTrue(encoded.contains("\"version\":${LauncherConfig.CURRENT_VERSION}"))
        assertTrue(encoded.contains("\"longPressMenuMs\":400"))
        assertTrue(encoded.contains("\"editHoldMs\":1200"))
    }

    /** 空对象按默认值解析，等价于首次启动的初始配置。 */
    @Test
    fun `空对象按默认值解析`() {
        val decoded = AppJson.format.decodeFromString<LauncherConfig>("{}")
        assertEquals(LauncherConfig(), decoded)
    }

    /** 缺少 section 时该 section 使用默认值。 */
    @Test
    fun `缺少分组使用默认值`() {
        val decoded =
            AppJson.format.decodeFromString<LauncherConfig>(
                "{\"version\":2,\"appearance\":{\"themeId\":\"paper\"}}",
            )
        assertEquals("paper", decoded.appearance.themeId)
        assertEquals(DesktopConfig(), decoded.desktop)
        assertEquals(NotificationConfig(), decoded.notifications)
    }

    /** 未知字段被忽略，便于后续版本增加的字段回退到旧版本运行。 */
    @Test
    fun `未知字段被忽略`() {
        val decoded =
            AppJson.format.decodeFromString<LauncherConfig>(
                "{\"version\":2,\"futureField\":{\"nested\":true}}",
            )
        assertEquals(2, decoded.version)
        assertEquals(LauncherConfig().copy(version = 2), decoded)
    }

    /** 默认配置不携带微信读书密钥，新装设备处于未配置状态。 */
    @Test
    fun `默认配置不含微信读书密钥`() {
        assertEquals("", LauncherConfig().integrations.wereadApiKey)
        val encoded = AppJson.format.encodeToString(LauncherConfig())
        assertTrue(encoded.contains("\"wereadApiKey\":\"\""))
    }

    /** 旧配置或旧备份中的凭据字段可正常解析，导入时不会因字段存在而失败。 */
    @Test
    fun `旧备份凭据字段可解析`() {
        val decoded =
            AppJson.format.decodeFromString<LauncherConfig>(
                "{\"version\":6,\"integrations\":{\"wereadApiKey\":\"wrk-legacy-key\"}," +
                    "\"lock\":{\"pinHash\":\"legacy-hash\",\"salt\":\"legacy-salt\"}}",
            )
        assertEquals("wrk-legacy-key", decoded.integrations.wereadApiKey)
        assertEquals("legacy-hash", decoded.lock.pinHash)
        assertEquals("legacy-salt", decoded.lock.salt)
    }

    /** 旧配置缺少应急清除开关时按默认值解析为开启，保证升级后长按流程仍可用。 */
    @Test
    fun `旧配置缺失应急清除开关时为开启`() {
        val decoded =
            AppJson.format.decodeFromString<LauncherConfig>(
                "{\"version\":6,\"lock\":{\"pinHash\":\"legacy-hash\",\"salt\":\"legacy-salt\"}}",
            )
        assertEquals("legacy-hash", decoded.lock.pinHash)
        assertTrue(decoded.lock.emergencyResetEnabled)
    }

    /** 剔除本机凭据后的配置编码不包含密钥、摘要与盐，备份文件不再携带凭据。 */
    @Test
    fun `导出配置剔除本机凭据`() {
        val config =
            LauncherConfig(
                integrations = IntegrationsConfig(wereadApiKey = "wrk-test-only-key"),
                lock = LockConfig(pinHash = "pin-hash", salt = "pin-salt"),
            )
        val encoded = AppJson.format.encodeToString(config.withoutLocalCredentials())
        assertFalse(encoded.contains("wrk-"))
        assertFalse(encoded.contains("pin-hash"))
        assertFalse(encoded.contains("pin-salt"))
        assertTrue(encoded.contains("\"wereadApiKey\":\"\""))
    }
}
