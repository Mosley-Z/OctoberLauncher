package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.widget.WidgetHitRegion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点击分发的回归用例。
 *
 * 内核形态不绘制右下角按钮，命中链路必须直接落到内核触点，
 * 不能因为缺少按钮几何而提前返回。
 */
class WereadBoardTapsTest {
    /** 记录动作与按钮调用的测试端口。 */
    private class RecordingHost(
        override val form: WereadBoardForm,
        override val chrome: WereadBoardChrome?,
        private val regions: List<WidgetHitRegion> = emptyList(),
    ) : WereadBoardGestureHost() {
        /** 收到的动作序列。 */
        val actions = mutableListOf<String>()

        /** 形态切换次数。 */
        var switches: Int = 0

        /** 打开次数。 */
        var opens: Int = 0

        override fun switchForm() {
            switches++
        }

        override fun dispatch(action: String): Boolean {
            actions += action
            return true
        }

        override fun open() {
            opens++
        }

        override fun hitRegion(
            x: Float,
            y: Float,
        ): WidgetHitRegion? = regions.firstOrNull { it.contains(x, y) }
    }

    private fun chromeOf(): WereadBoardChrome {
        val layout = BoardBounds(0f, 0f, 10f, 10f)
        val refresh = BoardBounds(12f, 0f, 22f, 10f)
        return WereadBoardChrome(BoardBounds(24f, 0f, 34f, 10f), layout, refresh)
    }

    /** 内核形态缺少按钮几何时，点击仍要落到内核触点。 */
    @Test
    fun kernelFormDispatchesZoneWithoutChrome() {
        val host =
            RecordingHost(
                form = WereadBoardForm.MONTH,
                chrome = null,
                regions = listOf(WidgetHitRegion(0, 0, 132, 51, ACTION_FORM_NEXT)),
            )
        assertTrue(WereadBoardTaps(host).click(30f, 20f))
        assertTrue(host.actions == listOf(ACTION_FORM_NEXT))
    }

    /** 内核触点的空白区域不消费点击。 */
    @Test
    fun kernelFormIgnoresOutsideZone() {
        val host =
            RecordingHost(
                form = WereadBoardForm.WEEK,
                chrome = null,
                regions = listOf(WidgetHitRegion(0, 0, 132, 51, ACTION_FORM_NEXT)),
            )
        assertFalse(WereadBoardTaps(host).click(400f, 300f))
        assertTrue(host.actions.isEmpty())
    }

    /** 本书形态点切换按钮触发形态切换。 */
    @Test
    fun bookFormLayoutButtonSwitches() {
        val host = RecordingHost(WereadBoardForm.BOOK, chromeOf())
        assertTrue(WereadBoardTaps(host).click(5f, 5f))
        assertTrue(host.switches == 1)
    }

    /** 本书形态点打开按钮打开当前书目。 */
    @Test
    fun bookFormOpenButtonOpens() {
        val host = RecordingHost(WereadBoardForm.BOOK, chromeOf())
        assertTrue(WereadBoardTaps(host).click(28f, 5f))
        assertTrue(host.opens == 1)
    }
}
