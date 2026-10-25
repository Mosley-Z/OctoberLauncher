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
    @Test
    fun tenBookCycleStartsAtFirstAndWrapsBothDirections() {
        val carousel = WereadBoardCarousel().apply { looping = true }
        carousel.reset(10, 0)
        assertEquals(0, carousel.selectedIndex)
        carousel.commit(-1)
        assertEquals(9, carousel.selectedIndex)
        carousel.commit(10)
        assertEquals(0, carousel.selectedIndex)
        repeat(10) { carousel.commit(carousel.selectedIndex + 1) }
        assertEquals(0, carousel.selectedIndex)
    }

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

    @Test
    fun immersiveFirstBookHasBothNeighborsAcrossBoundary() {
        val carousel =
            WereadBoardCarousel().apply {
                looping = true
                reset(8, 0)
            }
        val slots = carousel.slots()
        assertEquals(listOf(-2f, 2f, -1f, 1f, 0f), slots.map { it.position })
        assertEquals(listOf(6, 2, 7, 1, 0), slots.map { it.index })
        carousel.setPosition(-.8f)
        assertEquals(7, carousel.nearestIndex())
        carousel.commit(carousel.nearestIndex())
        carousel.setPosition(7.8f)
        assertEquals(0, carousel.nearestIndex())
    }

    @Test
    fun immersiveSmallShelvesNeverDuplicateBooks() {
        for (count in 1..5) {
            val carousel =
                WereadBoardCarousel().apply {
                    looping = true
                    reset(count, 0)
                }
            assertEquals(count, carousel.slots().size)
            assertEquals(
                count,
                carousel
                    .slots()
                    .map { it.index }
                    .distinct()
                    .size,
            )
            assertEquals(0, carousel.slots().last().index)
        }
    }

    @Test
    fun immersiveOuterCoversRemainVisibleAndHittable() {
        val frame = WereadBoardFrame.of(440f, 440f, 0f).copy(expandedCoverRings = true)
        val near = coverBoundsOf(frame, WereadCoverSlot(1, 1f))
        val far = coverBoundsOf(frame, WereadCoverSlot(2, 2f))
        assertTrue(far.right > near.right)
        val point = (near.right + far.right) / 2
        assertTrue(far.contains(point, far.centerY))
        assertFalse(near.contains(point, far.centerY))
    }

    /** 左滑换下一本、右滑换上一本，短滑回弹。 */
    @Test
    fun dragDirectionMapsToNeighborBook() {
        assertEquals(1, swipeDirectionOf(0.5f))
        assertEquals(-1, swipeDirectionOf(-0.5f))
        assertEquals(0, swipeDirectionOf(0.2f))
        assertEquals(0, swipeDirectionOf(-0.2f))
    }

    /** 循环轮播在首尾两侧都能取到相邻书。 */
    @Test
    fun loopingNeighborsWrapAround() {
        val carousel =
            WereadBoardCarousel().apply {
                looping = true
                reset(3, 0)
            }
        assertTrue(carousel.commit(-1))
        assertEquals(2, carousel.selectedIndex)
        assertTrue(carousel.commit(3))
        assertEquals(0, carousel.selectedIndex)
    }
}
