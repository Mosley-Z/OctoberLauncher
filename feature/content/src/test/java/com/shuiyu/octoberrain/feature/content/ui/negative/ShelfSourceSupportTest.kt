package com.shuiyu.octoberrain.feature.content.ui.negative

import com.shuiyu.octoberrain.core.common.NegativePageConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 负一屏书架书源的归一与文案测试。
 *
 * 覆盖默认值、已知取值、大小写与空白容错，以及未知取值回退微信读书的规则，
 * 保证旧配置与手工修改不会让书架进入无数据分支。
 */
class ShelfSourceSupportTest {
    /** 空串、缺失与未知取值统一按微信读书处理。 */
    @Test
    fun `未知取值回退微信读书`() {
        listOf("", "  ", "WEREAD", "imported", "本地").forEach { raw ->
            assertEquals(ShelfSourceSupport.WEREAD, ShelfSourceSupport.normalize(raw))
        }
    }

    /** 读取侧遵守公共契约，只有精确 local 为本地。 */
    @Test
    fun `本地取值与公共配置一致`() {
        listOf("local", "LOCAL", " local ").forEach { raw ->
            assertEquals(NegativePageConfig(shelfSource = raw).effectiveShelfSource, ShelfSourceSupport.normalize(raw))
        }
    }

    /** 配置缺省书源字段时按微信读书读取。 */
    @Test
    fun `配置缺省字段读取为微信读书`() {
        assertEquals(ShelfSourceSupport.WEREAD, ShelfSourceSupport.of(NegativePageConfig()))
    }

    /** 行内文案跟随归一后的书源。 */
    @Test
    fun `文案随书源变化`() {
        assertEquals("微信读书", ShelfSourceSupport.label("weread"))
        assertEquals("本地书源", ShelfSourceSupport.label("local"))
        assertEquals("微信读书", ShelfSourceSupport.label("LOCAL"))
    }
}
