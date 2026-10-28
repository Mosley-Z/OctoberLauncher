package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.view.View
import com.shuiyu.octoberrain.core.common.DeskModulePolicy
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.NegativeHost
import com.shuiyu.octoberrain.core.common.NegativeModule
import com.shuiyu.octoberrain.core.common.WereadDeskConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 负一屏卡片设置的纯逻辑测试。
 *
 * 覆盖默认五项启用状态、模块列表增删、设置页展示顺序与上下移动的写入结果。
 */
class NegativeSettingsLogicTest {
    /** 默认配置启用三项卡片，阅读资产与阅读指纹由用户开启。 */
    @Test
    fun `默认配置启用五张卡片`() {
        val config = LauncherConfig()
        val modules = config.desktop.negativePage.modules
        assertEquals(
            listOf("bookshelf", "negative_files", "bill", "rank", "fp"),
            modules,
        )
        assertTrue(NegativeSettingsLogic.enabled(config, "bookshelf"))
        assertTrue(NegativeSettingsLogic.enabled(config, "negative_files"))
        assertTrue(NegativeSettingsLogic.enabled(config, "bill"))
        assertTrue(NegativeSettingsLogic.enabled(config, "rank"))
        assertTrue(NegativeSettingsLogic.enabled(config, "fp"))
    }

    /** 关闭书架卡片后模块列表不再包含书架标识。 */
    @Test
    fun `关闭书架卡片后移除标识`() {
        val modules =
            NegativeSettingsLogic.applyNegativeModule(
                listOf("bookshelf", "negative_files"),
                "bookshelf",
                false,
            )
        assertEquals(listOf("negative_files"), modules)
    }

    /** 未知标识在增删过程中保持原有顺序与内容。 */
    @Test
    fun `未知标识保留顺序`() {
        val original = listOf("custom_weather", "bookshelf", "custom_clock")
        val removed = NegativeSettingsLogic.applyNegativeModule(original, "bookshelf", false)
        assertEquals(listOf("custom_weather", "custom_clock"), removed)
    }

    /** 重复开启同一卡片不产生重复项。 */
    @Test
    fun `重复开启不产生重复项`() {
        val once = NegativeSettingsLogic.applyNegativeModule(emptyList(), "negative_files", true)
        val twice = NegativeSettingsLogic.applyNegativeModule(once, "negative_files", true)
        assertEquals(listOf("negative_files"), twice)
    }

    /** 设置页展示顺序：已启用模块保持原序，其余已知模块追加在后。 */
    @Test
    fun `展示顺序补入未启用模块`() {
        val config = LauncherConfig()
        val page = config.desktop.negativePage
        val custom =
            config.copy(
                desktop =
                    config.desktop.copy(
                        negativePage = page.copy(modules = listOf("bill", "bookshelf")),
                    ),
            )
        val order = NegativeSettingsLogic.moduleOrder(custom, emptyList())
        assertEquals(
            listOf("bill", "bookshelf", "negative_files", "rank", "fp"),
            order,
        )
    }

    /** 阅读指纹不在默认列表，注册后仍以关闭状态追加到展示顺序末尾。 */
    @Test
    fun `关闭的阅读指纹追加到展示顺序末尾`() {
        val base = LauncherConfig()
        val config =
            base.copy(
                integrations = base.integrations.copy(wereadDesk = base.integrations.wereadDesk.copy(onMask = 3)),
            )
        val order = NegativeSettingsLogic.moduleOrder(config, listOf(FakeModule("fp", "阅读指纹")))
        assertEquals(
            listOf("bookshelf", "negative_files", "bill", "rank", "fp"),
            order,
        )
        assertFalse(NegativeSettingsLogic.enabled(config, "fp"))
    }

    /** 注册的整屏墨台容器不是可开关的卡片，仍只展示五个真实分区。 */
    @Test
    fun `设置不展示墨台容器伪卡片`() {
        val order = NegativeSettingsLogic.moduleOrder(LauncherConfig(), listOf(FakeModule("desk", "墨台")))
        assertEquals(listOf("bookshelf", "negative_files", "bill", "rank", "fp"), order)
    }

    /** 外部设置关闭墨台模块时同步掩码，重新开启后保持原始位置。 */
    @Test
    fun `切换墨台开关同时写入掩码`() {
        val original = LauncherConfig()
        val disabled = NegativeSettingsLogic.toggle(original, "bill")
        assertFalse(NegativeSettingsLogic.enabled(disabled, "bill"))
        assertFalse(disabled.integrations.wereadDesk.isVisible("bill"))
        val enabled = NegativeSettingsLogic.toggle(disabled, "bill")
        assertTrue(NegativeSettingsLogic.enabled(enabled, "bill"))
        assertTrue(DeskModulePolicy.isEnabled(enabled, "bill"))
        assertEquals(original.desktop.negativePage.modules, enabled.desktop.negativePage.modules)
    }

    /** 指纹原本没有显示位，外部开启后共享消费者立即可见；墨台关闭后外部状态同步。 */
    @Test
    fun `两个入口共用指纹显示状态`() {
        val base = LauncherConfig()
        val off =
            base.copy(
                integrations = base.integrations.copy(wereadDesk = base.integrations.wereadDesk.copy(onMask = 3)),
            )
        val enabled = NegativeSettingsLogic.toggle(off, "fp")
        assertTrue(enabled.integrations.wereadDesk.onMask and WereadDeskConfig.BIT_FP != 0)
        assertTrue(DeskModulePolicy.visibleOrder(enabled).contains("fp"))
        val disabled = DeskModulePolicy.toggle(enabled, "fp")
        assertFalse(NegativeSettingsLogic.enabled(disabled, "fp"))
        assertFalse(NegativeSettingsLogic.enabled(disabled, "fp"))
        val reenabled = NegativeSettingsLogic.toggle(disabled, "fp")
        assertTrue(DeskModulePolicy.isEnabled(reenabled, "fp"))
    }

    /** 调整内置模块时保留未知扩展，不复活被关闭的内置项。 */
    @Test
    fun `内置排序开关保留扩展与关闭状态`() {
        val original = LauncherConfig()
        val withExtras =
            original.copy(
                desktop =
                    original.desktop.copy(
                        negativePage =
                            original.desktop.negativePage.copy(
                                modules = listOf("custom_a", "bookshelf", "bill", "custom_b", "rank"),
                            ),
                    ),
            )
        val disabled = NegativeSettingsLogic.toggle(withExtras, "bill")
        val moved = NegativeSettingsLogic.move(disabled, "rank", -1, emptyList())
        assertEquals(
            listOf("custom_a", "custom_b"),
            moved.desktop.negativePage.modules
                .filter { it.startsWith("custom_") },
        )
        assertFalse(DeskModulePolicy.isEnabled(moved, "bill"))
        assertTrue(moved.desktop.negativePage.cardOrderInitialized)
        val extensionOff = NegativeSettingsLogic.toggle(moved, "custom_a")
        assertFalse(NegativeSettingsLogic.enabled(extensionOff, "custom_a"))
        assertTrue(NegativeSettingsLogic.enabled(extensionOff, "custom_b"))
    }

    /** 上移一格：与前一模块交换位置，并把顺序标记为已初始化。 */
    @Test
    fun `上移交换位置并标记顺序已初始化`() {
        val config = LauncherConfig()
        val moved = NegativeSettingsLogic.move(config, "rank", -1, emptyList())
        assertEquals(
            listOf("bookshelf", "negative_files", "rank", "bill", "fp"),
            moved.desktop.negativePage.modules,
        )
        assertTrue(moved.desktop.negativePage.cardOrderInitialized)
    }

    /** 到达首端后上移不改变配置。 */
    @Test
    fun `首端上移不改变配置`() {
        val config = LauncherConfig()
        val moved = NegativeSettingsLogic.move(config, "bookshelf", -1, emptyList())
        assertSame(config, moved)
    }

    /** 模块名优先取注册值，未注册时回退到内置显示名。 */
    @Test
    fun `模块名回退到内置显示名`() {
        assertEquals("墨单", NegativeSettingsLogic.label("bill", emptyList()))
        assertEquals("阅读资产", NegativeSettingsLogic.label("rank", emptyList()))
        assertEquals("阅读指纹", NegativeSettingsLogic.label("fp", emptyList()))
    }

    /**
     * 测试用负一屏模块。
     *
     * 仅提供标识与显示名，视图构建始终返回 null，用于验证展示顺序的补入规则。
     *
     * @param id 模块标识
     * @param label 模块显示名
     */
    private class FakeModule(
        override val id: String,
        override val label: String,
    ) : NegativeModule {
        override fun createView(
            context: Context,
            host: NegativeHost,
        ): View? = null

        override fun onResume() = Unit

        override fun onPause() = Unit
    }
}
