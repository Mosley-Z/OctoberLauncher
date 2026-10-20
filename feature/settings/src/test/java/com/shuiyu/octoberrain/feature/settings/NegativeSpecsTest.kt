package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.view.View
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.NegativeHost
import com.shuiyu.octoberrain.core.common.NegativeModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 负一屏卡片设置的纯逻辑测试。
 *
 * 覆盖默认五项启用状态、模块列表增删、设置页展示顺序与上下移动的写入结果，
 * 并核对桌面偏好页只保留一条入口声明。
 */
class NegativeSpecsTest {
    /** 默认配置启用四项卡片，阅读指纹与墨台默认掩码一致处于关闭状态。 */
    @Test
    fun `默认配置启用四张卡片`() {
        val config = LauncherConfig()
        val modules = config.desktop.negativePage.modules
        assertEquals(
            listOf("bookshelf", "negative_files", "bill", "rank"),
            modules,
        )
        assertTrue(NegativeSpecs.moduleEnabled(config, NegativeSpecs.MODULE_BOOKSHELF))
        assertTrue(NegativeSpecs.moduleEnabled(config, NegativeSpecs.MODULE_FILES))
        assertTrue(NegativeSpecs.moduleEnabled(config, "bill"))
        assertTrue(NegativeSpecs.moduleEnabled(config, "rank"))
        assertFalse(NegativeSpecs.moduleEnabled(config, "fp"))
    }

    /** 关闭书架卡片后模块列表不再包含书架标识。 */
    @Test
    fun `关闭书架卡片后移除标识`() {
        val modules =
            NegativeSpecs.applyNegativeModule(
                listOf(NegativeSpecs.MODULE_BOOKSHELF, NegativeSpecs.MODULE_FILES),
                NegativeSpecs.MODULE_BOOKSHELF,
                false,
            )
        assertEquals(listOf(NegativeSpecs.MODULE_FILES), modules)
    }

    /** 未知标识在增删过程中保持原有顺序与内容。 */
    @Test
    fun `未知标识保留顺序`() {
        val original = listOf("custom_weather", "bookshelf", "custom_clock")
        val removed = NegativeSpecs.applyNegativeModule(original, "bookshelf", false)
        assertEquals(listOf("custom_weather", "custom_clock"), removed)
    }

    /** 重复开启同一卡片不产生重复项。 */
    @Test
    fun `重复开启不产生重复项`() {
        val once = NegativeSpecs.applyNegativeModule(emptyList(), NegativeSpecs.MODULE_FILES, true)
        val twice = NegativeSpecs.applyNegativeModule(once, NegativeSpecs.MODULE_FILES, true)
        assertEquals(listOf(NegativeSpecs.MODULE_FILES), twice)
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
            listOf("bill", "bookshelf", "negative_files", "rank"),
            order,
        )
    }

    /** 阅读指纹不在默认列表，注册后仍以关闭状态追加到展示顺序末尾。 */
    @Test
    fun `关闭的阅读指纹追加到展示顺序末尾`() {
        val config = LauncherConfig()
        val order = NegativeSettingsLogic.moduleOrder(config, listOf(FakeModule("fp", "阅读指纹")))
        assertEquals(
            listOf("bookshelf", "negative_files", "bill", "rank", "fp"),
            order,
        )
        assertFalse(NegativeSettingsLogic.enabled(config, "fp"))
    }

    /** 切换启用状态：关闭从列表移除，开启追加到末尾。 */
    @Test
    fun `切换开关写入模块列表`() {
        val disabled = NegativeSettingsLogic.toggle(LauncherConfig(), "bill")
        assertFalse(
            disabled.desktop.negativePage.modules
                .contains("bill"),
        )
        val enabled = NegativeSettingsLogic.toggle(disabled, "bill")
        assertEquals(
            "bill",
            enabled.desktop.negativePage.modules
                .last(),
        )
    }

    /** 上移一格：与前一模块交换位置，并把顺序标记为已初始化。 */
    @Test
    fun `上移交换位置并标记顺序已初始化`() {
        val config = LauncherConfig()
        val moved = NegativeSettingsLogic.move(config, "rank", -1, emptyList())
        assertEquals(
            listOf("bookshelf", "negative_files", "rank", "bill"),
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

    /** 桌面偏好页只保留一条入口声明，点击跳转负一屏设置页。 */
    @Test
    fun `声明表包含一条入口项`() {
        val specs = SettingsCatalog.specsOf(SettingsCatalog.GROUP_NEGATIVE_CARDS)
        assertEquals(1, specs.size)
        val spec = specs.single()
        assertEquals(NegativeSpecs.SPEC_SETTINGS_ENTRY, spec.id)
        assertTrue(spec.control is SettingControl.Action)
        assertEquals(SettingEffect.NONE, spec.effect)
    }

    /** 分组顺序位于系统设置之后、书架设置之前，页面拼接结果一致。 */
    @Test
    fun `分组顺序位于系统与书架之间`() {
        val ids = SettingsCatalog.groups.map { it.id }
        val negativeIndex = ids.indexOf(SettingsCatalog.GROUP_NEGATIVE_CARDS)
        val systemIndex = ids.indexOf(SettingsCatalog.GROUP_SYSTEM_SYSTEM)
        val bookshelfIndex = ids.indexOf(SettingsCatalog.GROUP_BOOKSHELF_SOURCE)
        assertTrue(negativeIndex > systemIndex)
        assertTrue(negativeIndex < bookshelfIndex)
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
