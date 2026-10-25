package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 外观快照对旧偏好文档的兼容契约。 */
class WereadAppearanceTest {
    @Test
    fun `missing month style uses heatmap and explicit choices survive`() {
        assertTrue(WereadAppearance.of(documentOf("{}")).monthHeatmap)
        assertFalse(WereadAppearance.of(documentOf("""{"int":{"month_style":0}}""")).monthHeatmap)
        assertTrue(WereadAppearance.of(documentOf("""{"int":{"month_style":1}}""")).monthHeatmap)
    }

    @Test
    fun `appearance decodes when card enabled key is absent`() {
        val document = documentOf("""{"bool":{"app_show_signature":false},"int":{"month_style":1}}""")
        val appearance = WereadAppearance.of(document)
        assertFalse(appearance.showSignature)
        assertTrue(appearance.monthHeatmap)
        assertEquals(WereadAppearance.PAPER_DEFAULT, appearance.paperColor)
        assertEquals(0, appearance.cardPoolMask)
    }

    @Test
    fun `legacy card enabled key is ignored`() {
        val document = documentOf("""{"bool":{"card_enabled":false},"int":{"card_pool_mask":31}}""")
        val appearance = WereadAppearance.of(document)
        assertEquals(31, appearance.cardPoolMask)
        assertEquals(WereadAppearance.BODY_SIZE_DEFAULT, appearance.bodySize)
        assertTrue(appearance.showSignature)
    }

    /** 把 JSON 文本解析为偏好根对象。 */
    private fun documentOf(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject
}
