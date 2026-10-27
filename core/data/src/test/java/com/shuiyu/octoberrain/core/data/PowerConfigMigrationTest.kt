package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.IMAGE_CAROUSEL_OPTIONS
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.effectiveImageCarouselMinutes
import com.shuiyu.octoberrain.core.common.forWidgetPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerConfigMigrationTest {
    @Test fun `legacy seconds become same number of minutes on home and extra pages`() {
        val raw =
            """
            {"version":16,"desktop":{"imageCarouselSeconds":5,
              "pageWidgets":{"1":{"widgetId":"image","imageCarouselSeconds":30}}},
              "tray":{"autoRefreshMinutes":10}}
            """.trimIndent()
        val memory = Memory(raw)
        val config = JsonConfigStore(memory).state.value
        assertEquals(5, config.desktop.effectiveImageCarouselMinutes)
        assertEquals(30, config.desktop.forWidgetPage(1).effectiveImageCarouselMinutes)
        assertEquals(10, config.tray.autoRefreshMinutes)
        assertFalse(memory.raw.contains("imageCarouselSeconds"))
        assertTrue(memory.raw.contains("imageCarouselMinutes"))
        assertEquals(config, JsonConfigStore(memory).state.value)
        val backup = AppJson.format.decodeFromString(LauncherConfig.serializer(), raw)
        assertEquals(5, backup.desktop.effectiveImageCarouselMinutes)
        assertEquals(30, backup.desktop.forWidgetPage(1).effectiveImageCarouselMinutes)
    }

    @Test fun `new defaults disable full refresh and support one minute`() {
        val config = JsonConfigStore(Memory("""{"version":16}""")).state.value
        assertEquals(0, config.tray.autoRefreshMinutes)
        assertEquals(15, config.desktop.effectiveImageCarouselMinutes)
        assertEquals(1, IMAGE_CAROUSEL_OPTIONS.minOrNull())
        val one = config.copy(desktop = config.desktop.copy(imageCarouselMinutes = 1))
        val encoded = AppJson.format.encodeToString(LauncherConfig.serializer(), one)
        assertEquals(
            1,
            JsonConfigStore(Memory(encoded))
                .state.value.desktop.effectiveImageCarouselMinutes,
        )
    }

    private class Memory(
        var raw: String,
    ) : ConfigPersistence {
        override fun read(): String = raw

        override fun write(value: String): Boolean {
            raw = value
            return true
        }
    }
}
