package com.shuiyu.octoberrain.feature.content.ui.negative

import com.shuiyu.octoberrain.core.common.DeskModulePolicy
import com.shuiyu.octoberrain.core.common.LauncherConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 容器恢复时只对影响实际分区的变化重建。 */
class DeskContainerConfigTest {
    /** 外部开关和排序即使注册容器 id 不变，也必须使装配快照失效。 */
    @Test
    fun moduleToggleAndOrderInvalidateSnapshot() {
        val original = LauncherConfig()
        val snapshot = DeskContainerConfig.from(original)
        assertNotEquals(snapshot, DeskContainerConfig.from(DeskModulePolicy.toggle(original, "fp")))
        assertNotEquals(snapshot, DeskContainerConfig.from(DeskModulePolicy.move(original, "rank", -1)))
        assertNotEquals(
            snapshot,
            DeskContainerConfig.from(
                original.copy(
                    integrations =
                        original.integrations.copy(
                            wereadMenu = original.integrations.wereadMenu.copy(title = "新标题"),
                        ),
                ),
            ),
        )
    }

    /** 与分区无关的宿主锁屏、外观变更无需重新读取并绑定全部内核页面。 */
    @Test
    fun unrelatedSettingsKeepSnapshot() {
        val original = LauncherConfig()
        val changed = original.copy(lock = original.lock.copy(autoLock = false))
        assertEquals(DeskContainerConfig.from(original), DeskContainerConfig.from(changed))
    }
}
