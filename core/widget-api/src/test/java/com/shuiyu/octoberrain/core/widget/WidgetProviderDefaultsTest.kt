package com.shuiyu.octoberrain.core.widget

import android.content.Context
import com.shuiyu.octoberrain.core.common.Capability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证组件提供者扩展声明的默认值。
 *
 * 仅声明基础信息的组件沿用默认分类、手动刷新与空能力集合，
 * 与扩充声明之前的可观察行为保持一致。
 */
class WidgetProviderDefaultsTest {
    /** 只实现必选成员的组件提供者，用于读取扩展属性的默认值。 */
    private class UndeclaredProvider : WidgetProvider {
        override val id: String = "defaults"
        override val label: String = "默认组件"
        override val description: String = "未声明扩展字段"

        override fun create(context: Context): WidgetView = error("测试中不创建视图")
    }

    private val undeclared = UndeclaredProvider()

    /** 未声明分类时归入其他分类。 */
    @Test
    fun categoryDefaultsToOther() {
        assertEquals(WidgetCategory.OTHER, undeclared.category)
    }

    /** 未声明刷新策略时保持手动刷新，避免宿主主动取数。 */
    @Test
    fun refreshPolicyDefaultsToManual() {
        assertEquals(WidgetRefreshPolicy.MANUAL, undeclared.refreshPolicy)
    }

    /** 未声明能力需求时返回空集合，渲染层不做缺失能力判定。 */
    @Test
    fun requiredCapabilitiesDefaultToEmpty() {
        assertTrue(undeclared.requiredCapabilities.isEmpty())
    }

    /** 默认能力集合的元素类型为能力枚举，便于渲染层直接比对。 */
    @Test
    fun requiredCapabilitiesUseCapabilityEnum() {
        val capabilities: Set<Capability> = undeclared.requiredCapabilities
        assertTrue(capabilities.isEmpty())
    }

    /** 整分刷新策略参与桌面每分钟刷新。 */
    @Test
    fun minutePolicyTriggersOnMinute() {
        assertTrue(WidgetRefreshPolicy.MINUTE.triggersOnMinute())
    }

    /** 事件驱动策略不参与整分刷新。 */
    @Test
    fun eventPolicyDoesNotTriggerOnMinute() {
        assertFalse(WidgetRefreshPolicy.EVENT.triggersOnMinute())
    }

    /** 手动策略不参与整分刷新，由组件自身或用户操作驱动。 */
    @Test
    fun manualPolicyDoesNotTriggerOnMinute() {
        assertFalse(WidgetRefreshPolicy.MANUAL.triggersOnMinute())
    }
}
