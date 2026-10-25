package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.common.IMAGE_SWIPE_BOTH
import com.shuiyu.octoberrain.core.common.IMAGE_SWIPE_HORIZONTAL
import com.shuiyu.octoberrain.core.common.IMAGE_SWIPE_OFF
import com.shuiyu.octoberrain.core.common.IMAGE_SWIPE_VERTICAL
import org.junit.Assert.assertEquals
import org.junit.Test

/** 图片组件滑动切图的方向、阈值与循环规则。 */
class ImageSwipeTest {
    private val threshold = 36f

    @Test
    fun `关闭时不切图`() {
        assertEquals(ImageSwipe.STEP_NONE, ImageSwipe.step(IMAGE_SWIPE_OFF, -200f, 0f, threshold))
    }

    @Test
    fun `左右模式左滑下一张右滑上一张`() {
        assertEquals(ImageSwipe.STEP_NEXT, ImageSwipe.step(IMAGE_SWIPE_HORIZONTAL, -100f, 4f, threshold))
        assertEquals(ImageSwipe.STEP_PREVIOUS, ImageSwipe.step(IMAGE_SWIPE_HORIZONTAL, 100f, -4f, threshold))
    }

    @Test
    fun `左右模式忽略纵向位移`() {
        assertEquals(ImageSwipe.STEP_NONE, ImageSwipe.step(IMAGE_SWIPE_HORIZONTAL, 4f, -200f, threshold))
    }

    @Test
    fun `上下模式上滑下一张下滑上一张`() {
        assertEquals(ImageSwipe.STEP_NEXT, ImageSwipe.step(IMAGE_SWIPE_VERTICAL, 5f, -120f, threshold))
        assertEquals(ImageSwipe.STEP_PREVIOUS, ImageSwipe.step(IMAGE_SWIPE_VERTICAL, -5f, 120f, threshold))
    }

    @Test
    fun `双轴按主轴判定且斜向过近不切`() {
        assertEquals(ImageSwipe.STEP_NEXT, ImageSwipe.step(IMAGE_SWIPE_BOTH, -120f, 20f, threshold))
        assertEquals(ImageSwipe.STEP_PREVIOUS, ImageSwipe.step(IMAGE_SWIPE_BOTH, 10f, 140f, threshold))
        assertEquals(ImageSwipe.STEP_NONE, ImageSwipe.step(IMAGE_SWIPE_BOTH, 90f, 90f, threshold))
    }

    @Test
    fun `未达阈值不切`() {
        assertEquals(ImageSwipe.STEP_NONE, ImageSwipe.step(IMAGE_SWIPE_BOTH, -20f, 0f, threshold))
        assertEquals(ImageSwipe.STEP_NONE, ImageSwipe.step(IMAGE_SWIPE_VERTICAL, 0f, 30f, threshold))
    }

    @Test
    fun `序号在首尾循环`() {
        assertEquals(0, ImageSwipe.wrap(2, 3, ImageSwipe.STEP_NEXT))
        assertEquals(2, ImageSwipe.wrap(0, 3, ImageSwipe.STEP_PREVIOUS))
        assertEquals(0, ImageSwipe.wrap(0, 1, ImageSwipe.STEP_NEXT))
    }
}
