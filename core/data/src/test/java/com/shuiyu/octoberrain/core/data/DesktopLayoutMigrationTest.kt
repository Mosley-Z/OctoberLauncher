package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.ThemeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopLayoutMigrationTest {
    private class Memory(
        var value: String,
    ) : ConfigPersistence {
        override fun read(): String = value

        override fun write(value: String): Boolean {
            this.value = value
            return true
        }
    }

    @Test
    fun `旧列表容量与应用顺序迁移且重载幂等`() {
        val memory =
            Memory(
                """{"version":13,"desktop":{"kind":"LIST","columns":4,"rows":7,
            "firstPageRows":2,"columnSpacingDp":20,"rowSpacingDp":16,"order":["app:test"]}}""",
            )
        val config = JsonConfigStore(memory).state.value
        assertEquals(LauncherConfig.CURRENT_VERSION, config.version)
        assertEquals(4, config.desktop.firstPageColumns)
        assertEquals(7, config.desktop.listRows)
        assertEquals(2, config.desktop.listFirstRows)
        assertEquals(0, config.desktop.rowSpacingDp)
        assertEquals(0, config.desktop.columnSpacingDp)
        assertEquals(listOf("app:test"), config.desktop.order)
        assertEquals(config, JsonConfigStore(memory).state.value)
    }

    @Test
    fun `显式自动零值不被旧列数覆盖且逐字段迁移`() {
        val memory =
            Memory(
                """{"version":13,"desktop":{"kind":"LIST","columns":5,"rows":8,
            "firstPageRows":3,"firstPageColumns":0,"listFirstRows":0}}""",
            )
        val config = JsonConfigStore(memory).state.value
        assertEquals(0, config.desktop.firstPageColumns)
        assertEquals(0, config.desktop.listFirstRows)
        assertEquals(8, config.desktop.listRows)
    }

    @Test
    fun `备份解析与配置加载复用相同原始字段存在性`() {
        val raw = """{"version":13,"desktop":{"columns":4,"rows":5,"firstPageColumns":2}}"""
        val root = AppJson.format.parseToJsonElement(raw)
        val parsed = AppJson.format.decodeFromString(LauncherConfig.serializer(), raw)
        val migrated = ConfigMigration.migrate(parsed, desktopLayoutFields = DesktopLayoutMigration.presentFields(root))
        assertEquals(JsonConfigStore(Memory(raw)).state.value, migrated)
    }

    @Test
    fun `旧仅图标偏好合并到名称开关`() {
        val config = JsonConfigStore(Memory("""{"version":13,"desktop":{"iconOnly":true}}""")).state.value
        assertEquals(false, config.appearance.showLabels)
        assertEquals(false, config.desktop.iconOnly)
    }

    @Test
    fun `主题新增字段往返并区分旧包缺失和显式自动`() {
        val old = AppJson.format.decodeFromString(ThemeLayout.serializer(), """{"columns":4}""")
        assertNull(old.firstPageColumns)
        val layout = old.copy(firstPageColumns = 0, listRows = 7, listMarginDp = 28)
        val raw = AppJson.format.encodeToString(ThemeLayout.serializer(), layout)
        assertEquals(layout, AppJson.format.decodeFromString(ThemeLayout.serializer(), raw))
    }
}
