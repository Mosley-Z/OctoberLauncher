package com.shuiyu.octoberrain.feature.content.ui.negative

import com.inkread.weekread.kernel.desk.PricePad
import org.junit.Assert.assertEquals
import org.junit.Test

/** 针对实际 k6 jar 的反射字段与录价规则契约。 */
class WereadDeskEditBridgeTest {
    /** 两个分区均保留 PricePad 字段，替换 jar 时不静默失去持久化。 */
    @Test
    fun bundledKernelExposesExpectedPads() {
        assertEquals(2, WereadDeskEditBridge.padFields().size)
    }

    /** 空输入、免费、正常价格和小数价与真实 PricePad 的提交值一致。 */
    @Test
    fun priceConversionMatchesKernel() {
        for (text in listOf("0", "35", "12.34", "0.29", "9999.99")) {
            val pad = PricePad()
            pad.open("book", "title", -1)
            text.forEach { char -> pad.press(if (char == '.') PricePad.PK_DOT else char.digitToInt()) }
            pad.press(PricePad.PK_OK)
            assertEquals(pad.resultFen(), WereadDeskEditBridge.priceFen(pad.text()))
        }
        assertEquals(-1, WereadDeskEditBridge.priceFen(""))
    }
}
