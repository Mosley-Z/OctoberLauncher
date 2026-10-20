package com.shuiyu.octoberrain.core.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [DeviceProfileDetector] 前光判定逻辑的单元测试。 */
class DeviceProfileDetectorTest {
    @Test
    fun `液晶屏不探测前光并判定不支持`() {
        var probed = false
        val available =
            DeviceProfileDetector.resolveFrontLight(isEink = false) {
                probed = true
                true
            }
        assertFalse(available)
        assertFalse(probed)
    }

    @Test
    fun `墨水屏探测成功判定支持前光`() {
        val available = DeviceProfileDetector.resolveFrontLight(isEink = true) { true }
        assertTrue(available)
    }

    @Test
    fun `探测抛出异常时降级为不支持`() {
        val available =
            DeviceProfileDetector.resolveFrontLight(isEink = true) {
                throw IllegalStateException("设置项不存在")
            }
        assertFalse(available)
    }

    @Test
    fun `探测返回假时判定不支持`() {
        val available = DeviceProfileDetector.resolveFrontLight(isEink = true) { false }
        assertFalse(available)
    }
}
