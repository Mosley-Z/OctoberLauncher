package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

/** 来源升级只改变同步策略，不删除缓存或用户配置。 */
class WereadSourceTest {
    @Test
    fun `new and legacy imported settings use official sync`() {
        assertEquals("synced", IntegrationsConfig().effectiveWereadSource)
        val restored =
            AppJson.format.decodeFromString<IntegrationsConfig>(
                """{"wereadSource":"imported","wereadApiKey":"existing-key","booksDir":"MyBooks"}""",
            )
        assertEquals("synced", restored.effectiveWereadSource)
        assertEquals("existing-key", restored.wereadApiKey)
        assertEquals("MyBooks", restored.booksDir)
    }

    @Test
    fun `local cache selection remains local`() {
        assertEquals("local", IntegrationsConfig(wereadSource = "local").effectiveWereadSource)
        assertEquals(listOf("synced", "local"), IntegrationsConfig.WEREAD_SOURCE_OPTIONS)
    }
}
