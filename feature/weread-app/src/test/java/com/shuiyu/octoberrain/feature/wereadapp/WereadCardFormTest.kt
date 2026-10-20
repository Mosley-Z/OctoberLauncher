package com.shuiyu.octoberrain.feature.wereadapp

import com.inkread.weekread.kernel.PeriodCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [WereadCardForm] 形态与内核周期常量映射的单元测试。
 *
 * 覆盖五种形态与内核 `PeriodCalendar` 常量的对应关系，以及未知形态的容错。
 */
class WereadCardFormTest {
    /** 五种形态的周期常量应覆盖内核全部周期类型。 */
    @Test
    fun formsMatchKernelModes() {
        val modes = WereadCardForm.entries.map { it.mode }
        assertEquals(
            listOf(
                PeriodCalendar.WEEKLY,
                PeriodCalendar.MONTHLY,
                PeriodCalendar.BOOK,
                PeriodCalendar.NOTE,
                PeriodCalendar.TODO,
            ),
            modes,
        )
    }

    /** 形态反向查找应按周期常量命中唯一形态。 */
    @Test
    fun ofModeReturnsMatchingForm() {
        WereadCardForm.entries.forEach { form ->
            assertEquals(form, WereadCardForm.ofMode(form.mode))
        }
    }

    /** 未知周期常量应返回 null，避免页面误绘。 */
    @Test
    fun ofModeReturnsNullForUnknownMode() {
        assertNull(WereadCardForm.ofMode(PeriodCalendar.INSIGHT))
        assertNull(WereadCardForm.ofMode(""))
    }
}
