package com.shuiyu.octoberrain.feature.content.ui.negative

import com.shuiyu.octoberrain.core.common.LauncherConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 书架行数对负一屏装配的影响测试。
 *
 * 行数存放于负一屏配置，写入后由装配快照差异触发分区重建，书架据此按新行数重排。
 */
class ShelfDeskRowsTest {
    /** 书架行数变化使装配快照失效，宿主重建分区后行数即时生效。 */
    @Test
    fun shelfRowsInvalidateContainerSnapshot() {
        val original = LauncherConfig()
        val snapshot = DeskContainerConfig.from(original)
        val changed =
            original.copy(
                desktop =
                    original.desktop.copy(
                        negativePage = original.desktop.negativePage.copy(shelfRows = 3),
                    ),
            )
        assertNotEquals(snapshot, DeskContainerConfig.from(changed))
        assertEquals(
            original.desktop.negativePage.copy(shelfRows = 3),
            DeskContainerConfig.from(changed).page,
        )
    }
}
