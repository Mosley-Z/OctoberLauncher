package com.shuiyu.octoberrain.feature.files.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 查看器命中判定的单元测试。
 *
 * 覆盖顶栏返回、信息按钮、底栏四个动作与空白区域的判定顺序，
 * 以及底栏动作顺序与文案映射。几何取自 [ViewerMetrics] 的纯计算，不涉及绘制。
 */
class ViewerChromeTest {
    /** 480×800 视口，顶栏 64、行为 44、底栏 52 的常见手机几何。 */
    private fun metrics(): ViewerMetrics =
        ViewerMetrics(
            width = 480,
            height = 800,
            topBarHeight = 64,
            rowHeight = 44,
            minTouch = 44,
            bottomBarHeight = 52,
        )

    /** 顶栏左侧命中返回。 */
    @Test
    fun `返回按钮命中左上角区域`() {
        assertEquals(ViewerAction.BACK, ViewerHitTest.hit(10, 10, metrics()))
        assertEquals(ViewerAction.BACK, ViewerHitTest.hit(0, 0, metrics()))
    }

    /** 顶栏右侧命中信息。 */
    @Test
    fun `信息按钮命中顶栏右侧区域`() {
        assertEquals(ViewerAction.INFO, ViewerHitTest.hit(470, 20, metrics()))
        assertEquals(ViewerAction.INFO, ViewerHitTest.hit(430, 63, metrics()))
    }

    /** 顶栏标题区不返回动作。 */
    @Test
    fun `标题区不命中任何动作`() {
        assertNull(ViewerHitTest.hit(240, 20, metrics()))
    }

    /** 内容区不返回动作。 */
    @Test
    fun `内容区不命中任何动作`() {
        assertNull(ViewerHitTest.hit(240, 400, metrics()))
    }

    /** 底栏四个等分区域依次命中旋转、裁剪、滤镜、另存。 */
    @Test
    fun `底栏动作按等分顺序命中`() {
        val geometry = metrics()
        assertEquals(ViewerAction.ROTATE, ViewerHitTest.hit(10, 760, geometry))
        assertEquals(ViewerAction.CROP, ViewerHitTest.hit(130, 760, geometry))
        assertEquals(ViewerAction.FILTER, ViewerHitTest.hit(250, 760, geometry))
        assertEquals(ViewerAction.SAVE, ViewerHitTest.hit(470, 760, geometry))
        assertEquals(ViewerAction.SAVE, ViewerHitTest.hit(479, 799, geometry))
    }

    /** 底栏动作枚举顺序与几何划分一致。 */
    @Test
    fun `底栏动作顺序固定`() {
        assertEquals(
            listOf(ViewerAction.ROTATE, ViewerAction.CROP, ViewerAction.FILTER, ViewerAction.SAVE),
            ViewerHitTest.bottomActions,
        )
    }

    /** 动作文案与界面按钮一致。 */
    @Test
    fun `动作文案覆盖六个动作`() {
        assertEquals("返回", ViewerHitTest.labelOf(ViewerAction.BACK))
        assertEquals("信息", ViewerHitTest.labelOf(ViewerAction.INFO))
        assertEquals("旋转", ViewerHitTest.labelOf(ViewerAction.ROTATE))
        assertEquals("裁剪", ViewerHitTest.labelOf(ViewerAction.CROP))
        assertEquals("滤镜", ViewerHitTest.labelOf(ViewerAction.FILTER))
        assertEquals("另存", ViewerHitTest.labelOf(ViewerAction.SAVE))
    }

    /** 窄视口下顶栏与底栏收缩后仍可命中。 */
    @Test
    fun `窄视口顶栏底部仍可命中`() {
        val narrow =
            ViewerMetrics(
                width = 240,
                height = 320,
                topBarHeight = 64,
                rowHeight = 44,
                minTouch = 44,
                bottomBarHeight = 52,
            )
        assertEquals(ViewerAction.BACK, ViewerHitTest.hit(5, 5, narrow))
        assertEquals(ViewerAction.INFO, ViewerHitTest.hit(239, 5, narrow))
        assertEquals(ViewerAction.SAVE, ViewerHitTest.hit(239, 319, narrow))
    }
}
