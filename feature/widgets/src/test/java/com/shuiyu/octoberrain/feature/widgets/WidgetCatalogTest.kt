package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.common.Capability
import com.shuiyu.octoberrain.core.widget.WidgetRefreshPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组件声明的回归用例。
 *
 * 校验标识唯一、分类分组覆盖全部条目、刷新策略与能力声明与组件实现一致，
 * 并确认组件删减后清单只保留六个条目、被删标识不再登记。
 */
class WidgetCatalogTest {
    /** 标识唯一，保证配置查找与选择页选中状态准确。 */
    @Test
    fun identifiersAreUnique() {
        val ids = WidgetCatalog.providers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /** 分类分组覆盖全部组件且不重复。 */
    @Test
    fun categoriesCoverAllProviders() {
        val grouped = WidgetCatalog.byCategory.flatMap { it.second }
        assertEquals(WidgetCatalog.providers.size, grouped.size)
        assertEquals(WidgetCatalog.providers.toSet(), grouped.toSet())
    }

    /** 每个条目声明的分类与所在分组一致。 */
    @Test
    fun declaredCategoryMatchesGroup() {
        WidgetCatalog.byCategory.forEach { (category, providers) ->
            providers.forEach { provider ->
                assertEquals("组件 ${provider.id} 的分类与分组不一致", category, provider.category)
            }
        }
    }

    /** 整分刷新仅授予依赖时间推进的组件，时钟由内部定时器驱动。 */
    @Test
    fun minuteRefreshOnlyAppliesToCalendar() {
        val minuteIds =
            WidgetCatalog.providers
                .filter { it.refreshPolicy == WidgetRefreshPolicy.MINUTE }
                .map { it.id }
        assertEquals(listOf("calendar"), minuteIds)
        assertEquals(WidgetRefreshPolicy.MANUAL, WidgetCatalog.find("clock")?.refreshPolicy)
    }

    /** 能力声明覆盖需要系统授权或外部数据的组件。 */
    @Test
    fun requiredCapabilitiesAreDeclared() {
        assertEquals(
            setOf(Capability.USAGE_ACCESS),
            WidgetCatalog.find("usage")?.requiredCapabilities,
        )
        assertEquals(
            setOf(Capability.WEREAD),
            WidgetCatalog.find("weread_board")?.requiredCapabilities,
        )
        assertEquals(
            setOf(Capability.STORAGE),
            WidgetCatalog.find("image")?.requiredCapabilities,
        )
        assertTrue(WidgetCatalog.find("clock")?.requiredCapabilities?.isEmpty() == true)
    }

    /** 组件删减后清单只保留六个条目。 */
    @Test
    fun requiresSixProviders() {
        assertEquals(6, WidgetCatalog.providers.size)
    }

    /** 被删组件不再登记，旧标识仅由配置迁移改写。 */
    @Test
    fun removedWidgetsAreAbsent() {
        val removed =
            listOf(
                "reading",
                "weather",
                "clock_compact",
                "covers",
                "weread_covers",
                "weread_month",
                "weread_mark",
                "weread_note",
                "weread_stats",
                "weread_bill",
                "weread_rank",
                "weread_fingerprint",
                "koreader_month",
            )
        removed.forEach { id -> assertNull("组件 $id 应已删除", WidgetCatalog.find(id)) }
    }

    /** 时钟声明常规与紧凑两种布局，供选择页说明。 */
    @Test
    fun clockDeclaresBothLayoutForms() {
        val clock = WidgetCatalog.find("clock") as? WidgetCatalog.Entry
        assertEquals(listOf("stacked", "compact"), clock?.forms?.map { it.id })
    }
}
