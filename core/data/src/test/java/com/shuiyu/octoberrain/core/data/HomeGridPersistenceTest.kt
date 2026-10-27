package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.DesktopMode
import com.shuiyu.octoberrain.core.common.DesktopOrientation
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.firstGridColumns
import com.shuiyu.octoberrain.core.common.firstGridRows
import com.shuiyu.octoberrain.core.common.withFirstGrid
import org.junit.Assert.assertEquals
import org.junit.Test

/** 模式与方向切换只改对应字段，自动和手动容量均经真实存储重载验证。 */
class HomeGridPersistenceTest {
    @Test fun `automatic landscape stays three by two after mode switches and reload`() {
        val desktop = DesktopConfig(firstPageRows = 2).withFirstGrid(true, 0, 0)
        val restored = switchAndReload(desktop)
        assertEquals(0, restored.landscapeFirstPageRows)
        assertEquals(0, restored.landscapeFirstPageColumns)
        assertEquals(3, restored.firstGridRows(true, "S4"))
        assertEquals(2, restored.firstGridColumns(true))
    }

    @Test fun `custom direction capacities survive mode switches and reload`() {
        val desktop = DesktopConfig().withFirstGrid(false, 3, 1).withFirstGrid(true, 2, 4)
        val restored = switchAndReload(desktop)
        assertEquals(4, restored.firstGridRows(true, "S4"))
        assertEquals(2, restored.firstGridColumns(true))
        assertEquals(1, restored.firstGridRows(false, "S4"))
        assertEquals(3, restored.firstGridColumns(false))
    }

    private fun switchAndReload(desktop: DesktopConfig): DesktopConfig {
        val persistence =
            Memory(AppJson.format.encodeToString(LauncherConfig.serializer(), LauncherConfig(desktop = desktop)))
        val store = JsonConfigStore(persistence)
        listOf(DesktopMode.IMMERSIVE, DesktopMode.NORMAL).forEach { mode ->
            listOf(DesktopOrientation.PORTRAIT, DesktopOrientation.LANDSCAPE).forEach { orientation ->
                store.edit { it.copy(desktop = it.desktop.copy(mode = mode, orientation = orientation)) }
            }
        }
        return JsonConfigStore(persistence).state.value.desktop
    }

    private class Memory(
        private var raw: String,
    ) : ConfigPersistence {
        override fun read(): String = raw

        override fun write(value: String): Boolean {
            raw = value
            return true
        }
    }
}
