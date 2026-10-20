package com.shuiyu.octoberrain.feature.content.ui.negative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 负一屏墨台模块的身份与默认值测试。
 *
 * 负一屏改为墨台列表态后只注册一个容器模块，本测试覆盖模块标识、展示名与默认启用状态，
 * 断言对象不依赖视图构建，可在本地 JVM 中直接执行。
 */
class NegativeModulesTest {
    /** 模块标识取墨台容器常量，负一屏据此识别整屏视图。 */
    @Test
    fun `模块标识与容器常量一致`() {
        assertEquals("desk", DeskNegativeModule().id)
        assertEquals(DeskNegativeModule.ID, DeskNegativeModule().id)
    }

    /** 展示名与顶栏标题一致。 */
    @Test
    fun `展示名与顶栏标题一致`() {
        assertEquals("墨台", DeskNegativeModule().label)
    }

    /** 墨台容器默认启用，排序提示值为零使其优先于普通卡片模块。 */
    @Test
    fun `默认启用且排序优先`() {
        val module = DeskNegativeModule()
        assertTrue(module.defaultEnabled)
        assertEquals(0, module.orderHint)
    }
}
