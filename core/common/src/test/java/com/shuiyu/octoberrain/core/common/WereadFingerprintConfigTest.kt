package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 指纹选择在配置重建、升级和清空后的持久契约。 */
class WereadFingerprintConfigTest {
    @Test
    fun `multiple books survive config serialization in selection order`() {
        val config = WereadDeskConfig(fpBookIds = listOf("book-b", "book-a"), fpFontPx = 16)
        val restored = AppJson.format.decodeFromString<WereadDeskConfig>(AppJson.format.encodeToString(config))
        assertEquals(listOf("book-b", "book-a"), restored.effectiveFpBookIds)
        assertEquals(16, restored.effectiveFpFontPx)
    }

    @Test
    fun `old config and explicit clear keep empty selection`() {
        val old = AppJson.format.decodeFromString<WereadDeskConfig>("{}")
        val cleared = old.copy(fpBookIds = emptyList())
        val restored = AppJson.format.decodeFromString<WereadDeskConfig>(AppJson.format.encodeToString(cleared))
        assertTrue(restored.effectiveFpBookIds.isEmpty())
    }

    @Test
    fun `book ids are cleaned deduplicated and limited without reordering`() {
        val ids = listOf(" ", " b ", "b") + (1..60).map { "book-$it" }
        val selected = WereadDeskConfig(fpBookIds = ids).effectiveFpBookIds
        assertEquals(50, selected.size)
        assertEquals("b", selected.first())
        assertEquals("book-49", selected.last())
    }

    @Test
    fun `all supported font settings remain effective`() {
        WereadDeskConfig.FP_FONT_OPTIONS.forEach { size ->
            assertEquals(size, WereadDeskConfig(fpFontPx = size).effectiveFpFontPx)
        }
        assertEquals(18, WereadDeskConfig(fpFontPx = 48).effectiveFpFontPx)
    }
}
