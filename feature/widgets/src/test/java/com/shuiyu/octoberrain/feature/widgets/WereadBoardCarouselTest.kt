package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 叠放封面轮播状态的单元测试。
 *
 * 覆盖书目数量、边界位置、拖动范围、落位提交与各层缩放、间距换算。
 */
class WereadBoardCarouselTest {
    /** 空列表不产生任何封面层。 */
    @Test
    fun emptyListHasNoSlots() {
        val carousel = WereadBoardCarousel()
        carousel.reset(0, 0)
        assertEquals(0, carousel.count)
        assertTrue(carousel.slots().isEmpty())
        carousel.dragBy(1f)
        assertEquals(0f, carousel.drag, 0.0001f)
        assertFalse(carousel.commit(0))
    }

    /** 单本书只显示中央一层，且不产生拖动偏移。 */
    @Test
    fun singleBookShowsCenterOnly() {
        val carousel = WereadBoardCarousel()
        carousel.reset(1, 0)
        val slots = carousel.slots()
        assertEquals(1, slots.size)
        assertTrue(slots[0].centered)
        carousel.dragBy(0.8f)
        assertEquals(0f, carousel.drag, 0.0001f)
    }

    /** 三本以内只绘制实际存在的邻近封面，不复制书目凑数。 */
    @Test
    fun slotsCoverOnlyExistingBooks() {
        val carousel = WereadBoardCarousel()
        carousel.reset(3, 1)
        val indexes = carousel.slots().map { it.index }.sorted()
        assertEquals(listOf(0, 1, 2), indexes)
        carousel.reset(4, 0)
        assertEquals(listOf(0, 1, 2), carousel.slots().map { it.index }.sorted())
    }

    /** 五本及以上最多绘制五层，中央两侧各两层。 */
    @Test
    fun slotsLimitToFiveLayers() {
        val carousel = WereadBoardCarousel()
        carousel.reset(8, 4)
        val slots = carousel.slots()
        assertEquals(5, slots.size)
        assertEquals(listOf(2, 3, 4, 5, 6), slots.map { it.index }.sorted())
        assertEquals(4, slots.last().index)
        assertTrue(slots.last().centered)
    }

    /** 首尾位置不产生越界封面层。 */
    @Test
    fun boundaryPositionsOmitMissingSides() {
        val carousel = WereadBoardCarousel()
        carousel.reset(5, 0)
        assertEquals(listOf(0, 1, 2), carousel.slots().map { it.index }.sorted())
        carousel.reset(5, 4)
        assertEquals(listOf(2, 3, 4), carousel.slots().map { it.index }.sorted())
    }

    /** 拖动偏移限制在列表范围内，首本不能右切、末本不能左切。 */
    @Test
    fun dragClampsAtListEdges() {
        val carousel = WereadBoardCarousel()
        carousel.reset(3, 0)
        carousel.dragBy(-2f)
        assertEquals(0f, carousel.drag, 0.0001f)
        carousel.dragBy(5f)
        assertEquals(2f, carousel.drag, 0.0001f)
    }

    /** 落位提交后位置变化，且越界目标被截断。 */
    @Test
    fun commitClampsTarget() {
        val carousel = WereadBoardCarousel()
        carousel.reset(3, 1)
        assertTrue(carousel.commit(2))
        assertEquals(2, carousel.selectedIndex)
        assertFalse(carousel.commit(2))
        assertFalse(carousel.commit(9))
        assertEquals(2, carousel.selectedIndex)
    }

    /** 换书后拖动偏移清零，避免残留位移。 */
    @Test
    fun commitClearsDrag() {
        val carousel = WereadBoardCarousel()
        carousel.reset(4, 1)
        carousel.dragBy(0.6f)
        assertTrue(carousel.commit(2))
        assertEquals(0f, carousel.drag, 0.0001f)
    }

    /** 缩放在层间连续过渡，中央最大、两侧依次缩小。 */
    @Test
    fun scaleFallsWithRing() {
        assertEquals(1f, WereadBoardCarousel.scaleAt(0f), 0.0001f)
        assertEquals(0.88f, WereadBoardCarousel.scaleAt(1f), 0.0001f)
        assertEquals(0.80f, WereadBoardCarousel.scaleAt(2f), 0.0001f)
        assertEquals(0.80f, WereadBoardCarousel.scaleAt(3f), 0.0001f)
        assertTrue(WereadBoardCarousel.scaleAt(0.5f) > WereadBoardCarousel.scaleAt(1.5f))
    }

    /** 中心距在中央到一层递增，一层到二层回收到更小值，使二层只露窄边。 */
    @Test
    fun offsetKeepsFarLayerNarrow() {
        assertEquals(0f, WereadBoardCarousel.offsetAt(0f), 0.0001f)
        assertEquals(0.40f, WereadBoardCarousel.offsetAt(1f), 0.0001f)
        assertEquals(0.32f, WereadBoardCarousel.offsetAt(2f), 0.0001f)
        assertTrue(WereadBoardCarousel.offsetAt(1.5f) > WereadBoardCarousel.offsetAt(2f))
        assertTrue(WereadBoardCarousel.offsetAt(2.5f) > WereadBoardCarousel.offsetAt(2f))
    }

    /** 二层封面的左边缘超出一层封面的左边缘，形成可见窄边。 */
    @Test
    fun farLayerPeeksOutsideSideLayer() {
        val farLeft = WereadBoardCarousel.offsetAt(2f) - WereadBoardCarousel.scaleAt(2f) / 2f
        val sideLeft = WereadBoardCarousel.offsetAt(1f) - WereadBoardCarousel.scaleAt(1f) / 2f
        assertTrue(farLeft < sideLeft)
    }
}
