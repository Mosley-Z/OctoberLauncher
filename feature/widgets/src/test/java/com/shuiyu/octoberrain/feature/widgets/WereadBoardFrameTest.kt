package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.common.ImmersiveLandscapeBounds
import com.shuiyu.octoberrain.core.common.ImmersiveLandscapeGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 杂志版式几何推导的单元测试。
 *
 * 覆盖多种组件尺寸下的分带顺序、字号相对关系、封面适配与点击区域换算。
 */
class WereadBoardFrameTest {
    @Test
    fun enhancedSideCoversPreserveMainAndInterpolateOriginalGeometry() {
        val original =
            WereadBoardFrame.of(1680f, 1264f, 0f).copy(
                coverArea = BoardBounds(400f, 476f, 1410f, 1163f),
                coverCenterX = 907.5f,
                coverCenterY = 784f,
                coverWidth = 425f,
                coverHeight = 616f,
                expandedCoverRings = true,
            )
        val enhanced = original.copy(sideCoverWeight = 1f)
        assertEquals(coverBoundsOf(original, WereadCoverSlot(0, 0f)), coverBoundsOf(enhanced, WereadCoverSlot(0, 0f)))
        listOf(-2f, -1f, 1f, 2f).forEach { position ->
            val slot = WereadCoverSlot(0, position)
            val old = coverBoundsOf(original, slot)
            val target = coverBoundsOf(enhanced, slot)
            val inner = kotlin.math.abs(position) == 1f
            assertEquals(425f * if (inner) .845f else .75f, target.width, .001f)
            assertEquals(if (inner) 536.06f else 577.64f, target.top, .01f)
            assertEquals(old.centerX, target.centerX, .001f)
            val half = coverBoundsOf(original.copy(sideCoverWeight = .5f), slot)
            assertEquals((old.top + target.top) / 2f, half.top, .001f)
            assertEquals((old.width + target.width) / 2f, half.width, .001f)
            assertEquals(old, coverBoundsOf(original.copy(sideCoverWeight = 0f), slot))
        }
        val slots = listOf(-2f, 2f, -1f, 1f, 0f).mapIndexed { index, position -> WereadCoverSlot(index, position) }
        val pointX = 1080f
        val pointY = 700f
        val frontmost = slots.asReversed().first { coverBoundsOf(enhanced, it).contains(pointX, pointY) }
        assertEquals(0f, frontmost.position, 0f)
        val sidePoint = coverBoundsOf(enhanced, slots[3])
        assertEquals(
            1f,
            slots
                .asReversed()
                .first {
                    coverBoundsOf(enhanced, it).contains(sidePoint.right - 1f, sidePoint.centerY)
                }.position,
            0f,
        )
    }

    @Test
    fun animatedOuterCoversRetainOriginalHorizontalMovement() {
        val frames =
            listOf(720f to 1200f, 1680f to 1264f).flatMap { (width, height) ->
                val original = WereadBoardFrame.of(width, height, INSET)
                listOf(false, true).flatMap { expanded ->
                    listOf(0f, .5f, 1f).map { weight ->
                        original.copy(expandedCoverRings = expanded, sideCoverWeight = weight)
                    }
                }
            }
        frames.forEach { frame ->
            listOf(-3f, -2.5f, 2.5f, 3f).forEach { position ->
                val ring = kotlin.math.abs(position)
                val offset =
                    if (frame.expandedCoverRings) {
                        minOf(ring, 1f) * .40f + maxOf(ring - 1f, 0f) * .32f
                    } else {
                        WereadBoardCarousel.offsetAt(position)
                    }
                val center = frame.coverCenterX + offset * frame.coverWidth * kotlin.math.sign(position)
                val sideRing = ring.coerceAtMost(2f)
                val scale =
                    WereadBoardCarousel.scaleAt(position) -
                        frame.sideCoverWeight * (minOf(sideRing, 1f) * .035f + maxOf(sideRing - 1f, 0f) * .015f)
                val halfWidth = frame.coverWidth * scale / 2f
                val bounds = coverBoundsOf(frame, WereadCoverSlot(0, position))
                assertEquals(center - halfWidth, bounds.left, 0f)
                assertEquals(center + halfWidth, bounds.right, 0f)
            }
        }
    }

    @Test
    fun landscapeCoverRingsUseTheSameBoundsForDrawingAndSelection() {
        listOf(900f to 650f, 1680f to 1264f, 2400f to 900f).forEach { (width, height) ->
            val geometry =
                ImmersiveLandscapeGeometry.create(
                    ImmersiveLandscapeBounds(0f, 0f, width, height),
                    0f,
                    24f,
                    width,
                    height - 24f,
                    ImmersiveLandscapeBounds(24f, 24f, width - 120f, height - 48f),
                    1f,
                )
            val cover = geometry.readingBudget(height * .12f, height * .07f, height * .075f, 8f).cover
            val area = geometry.coverArea
            val frame =
                WereadBoardFrame.of(geometry.book.width, geometry.book.height, 0f).copy(
                    coverArea = BoardBounds(area.left, cover.top, area.right, area.bottom),
                    coverCenterX = cover.centerX,
                    coverCenterY = cover.centerY,
                    coverWidth = cover.width,
                    coverHeight = cover.height,
                    expandedCoverRings = true,
                )
            (-2..2).forEach { ring ->
                val bounds = coverBoundsOf(frame, WereadCoverSlot(ring + 2, ring.toFloat()))
                assertTrue(bounds.left >= frame.coverArea.left)
                assertTrue(bounds.right <= frame.coverArea.right)
                assertTrue(bounds.top >= frame.coverArea.top)
                assertTrue(bounds.bottom <= frame.coverArea.bottom)
                assertTrue(frame.coverArea.contains(bounds.centerX, bounds.centerY))
                assertEquals(1.45f, bounds.height / bounds.width, .0001f)
            }
        }
    }

    @Test
    fun oceanDefaultFiveBookContourStaysBetweenStatsAndRail() {
        val geometry =
            ImmersiveLandscapeGeometry.create(
                ImmersiveLandscapeBounds(0f, 0f, 1680f, 1264f),
                0f,
                24f,
                1680f,
                1240f,
                ImmersiveLandscapeBounds(100f, 20f, 1450f, 1200f),
                2f,
            )
        val cover = geometry.readingBudget(84f, 42f, 47f, 8f, 1).cover
        val frame =
            WereadBoardFrame.of(geometry.book.width, geometry.book.height, 0f).copy(
                coverCenterX = cover.centerX,
                coverCenterY = cover.centerY,
                coverWidth = cover.width,
                coverHeight = cover.height,
                expandedCoverRings = true,
            )
        (-2..2).forEach { ring ->
            val bounds = coverBoundsOf(frame, WereadCoverSlot(ring + 2, ring.toFloat()))
            assertTrue(bounds.left >= geometry.coverArea.left)
            assertTrue(bounds.right <= geometry.coverArea.right)
            assertTrue(bounds.left > geometry.statsRight)
            assertTrue(bounds.right + geometry.book.left < geometry.railCenter)
            assertTrue(bounds.top >= geometry.coverArea.top)
            assertTrue(bounds.bottom <= geometry.coverArea.bottom)
        }
    }

    /** 时间位于左上，日期在其下方，两者都在封面区之上。 */
    @Test
    fun headerStaysAboveCoverArea() {
        listOf(SMALL, MEDIUM, LARGE, WIDE).forEach { size ->
            val frame = WereadBoardFrame.of(size[0], size[1], INSET)
            assertTrue(frame.timeBaseline > frame.inset)
            assertTrue(frame.dateTop > frame.timeBaseline)
            assertTrue(frame.coverArea.top > frame.dateTop)
            assertTrue(frame.coverArea.top > frame.noteBounds.bottom)
        }
    }

    /** 书名、进度条与底部信息依次向下排列且不重叠。 */
    @Test
    fun footerBandsDoNotOverlap() {
        listOf(SMALL, MEDIUM, LARGE, WIDE).forEach { size ->
            val frame = WereadBoardFrame.of(size[0], size[1], INSET)
            assertTrue(frame.titleTop >= frame.coverArea.bottom)
            assertTrue(frame.barBounds.top > frame.titleTop)
            assertTrue(frame.barBounds.bottom < frame.infoBottom)
            assertTrue(frame.infoBottom <= size[1])
        }
    }

    /** 时间字号明显大于日期与底部信息字号，书名字号大于底部信息。 */
    @Test
    fun typeScaleKeepsHierarchy() {
        val frame = WereadBoardFrame.of(LARGE[0], LARGE[1], INSET)
        assertTrue(frame.timeSize > frame.dateSize * 2.5f)
        assertTrue(frame.titleSize > frame.infoSize)
        assertTrue(frame.noteSize >= frame.dateSize * 0.9f)
        assertTrue(frame.noteSize <= frame.dateSize * 1.1f)
    }

    /** 笔记区位于组件右上角，宽度不超过组件一半。 */
    @Test
    fun noteSitsAtTopRight() {
        val frame = WereadBoardFrame.of(MEDIUM[0], MEDIUM[1], INSET)
        assertEquals(MEDIUM[0] - INSET, frame.noteBounds.right, 0.01f)
        assertEquals(INSET, frame.noteBounds.top, 0.01f)
        assertTrue(frame.noteBounds.width <= MEDIUM[0] / 2f)
    }

    /** 中央封面按真实比例适配，不超出封面带纵横范围。 */
    @Test
    fun coverFitsWithinArea() {
        listOf(SMALL, MEDIUM, LARGE, WIDE).forEach { size ->
            val frame = WereadBoardFrame.of(size[0], size[1], INSET)
            assertTrue(frame.coverWidth > 0f)
            assertTrue(frame.coverWidth <= frame.coverArea.width)
            assertTrue(frame.coverHeight <= frame.coverArea.height + 0.01f)
            assertEquals(
                WereadBoardFrame.DEFAULT_COVER_ASPECT,
                frame.coverHeight / frame.coverWidth,
                0.001f,
            )
        }
    }

    /** 五层封面完整展开时仍在组件宽度之内。 */
    @Test
    fun fiveLayersStayInsideWidth() {
        val frame = WereadBoardFrame.of(WIDE[0], WIDE[1], INSET)
        val center = frame.coverCenterX
        val left =
            center - WereadBoardCarousel.offsetAt(2f) * frame.coverWidth -
                WereadBoardCarousel.scaleAt(2f) * frame.coverWidth / 2f
        val right =
            center + WereadBoardCarousel.offsetAt(2f) * frame.coverWidth +
                WereadBoardCarousel.scaleAt(2f) * frame.coverWidth / 2f
        assertTrue(left >= 0f)
        assertTrue(right <= WIDE[0])
    }

    /** 进度条宽度约为组件宽度的四分之一到三分之一，且水平居中。 */
    @Test
    fun progressBarIsCenteredAndNarrow() {
        val frame = WereadBoardFrame.of(LARGE[0], LARGE[1], INSET)
        assertEquals(LARGE[0] / 2f, frame.barBounds.centerX, 0.01f)
        assertTrue(frame.barBounds.width > LARGE[0] * 0.2f)
        assertTrue(frame.barBounds.width < LARGE[0] * 0.32f)
        assertTrue(frame.barBounds.height <= 4f * WereadBoardFrame.INFO_TEXT_SCALE)
    }

    /** 中央封面绘制区域以封面带中心对称。 */
    @Test
    fun centerSlotIsSymmetric() {
        val frame = WereadBoardFrame.of(MEDIUM[0], MEDIUM[1], INSET)
        val bounds = coverBoundsOf(frame, WereadCoverSlot(0, 0f))
        assertEquals(frame.coverCenterX, bounds.centerX, 0.01f)
        assertEquals(frame.coverCenterY, bounds.centerY, 0.01f)
        assertEquals(frame.coverWidth, bounds.width, 0.01f)
    }

    /** 极矮组件下封面带退化为窄带，仍不与书名重叠。 */
    @Test
    fun shortWidgetKeepsBandsSeparated() {
        val frame = WereadBoardFrame.of(SHORT[0], SHORT[1], INSET)
        assertTrue(frame.coverArea.bottom <= frame.titleTop)
        assertTrue(frame.coverWidth >= 0f)
        assertTrue(frame.barBounds.top > frame.titleTop)
    }

    /** 点击区域判定覆盖边界值。 */
    @Test
    fun boundsContainBoundary() {
        val bounds = BoardBounds(0f, 0f, 10f, 10f)
        assertTrue(bounds.contains(0f, 0f))
        assertTrue(bounds.contains(10f, 10f))
        assertFalse(bounds.contains(10.1f, 5f))
    }

    /** 版式切换与刷新按钮固定在右下角，且不压住阅读信息带。 */
    @Test
    fun footerButtonsSitAtBottomRight() {
        listOf(SMALL, MEDIUM, LARGE, WIDE).forEach { size ->
            val frame = WereadBoardFrame.of(size[0], size[1], INSET)
            val buttons =
                footerButtonsOf(
                    width = size[0],
                    inset = INSET,
                    infoBottom = frame.infoBottom,
                    height = 24f,
                    layoutWidth = 48f,
                    refreshWidth = 48f,
                    gap = 8f,
                )
            assertEquals(size[0] - INSET, buttons.refreshButton.right, 0.01f)
            assertEquals(frame.infoBottom, buttons.refreshButton.bottom, 0.01f)
            assertTrue(buttons.layoutButton.right < buttons.refreshButton.left)
            assertEquals(buttons.layoutButton.left - 8f, buttons.infoRight, 0.01f)
            assertTrue(buttons.infoRight >= INSET)
            assertTrue(buttons.layoutButton.left >= INSET)
        }
    }

    /** 窄组件下按钮向左收拢，仍保持完整宽度并留在组件内。 */
    @Test
    fun footerButtonsStayInsideNarrowWidget() {
        val buttons =
            footerButtonsOf(
                width = 200f,
                inset = INSET,
                infoBottom = 180f,
                height = 24f,
                layoutWidth = 48f,
                refreshWidth = 48f,
                gap = 8f,
            )
        assertTrue(buttons.layoutButton.left >= INSET)
        assertTrue(buttons.layoutButton.width > 0f)
        assertTrue(buttons.refreshButton.right <= 200f - INSET)
        assertTrue(buttons.infoRight >= INSET)
    }

    /** 底部信息字号按设计基准放大，按钮与进度条仍留在组件内。 */
    @Test
    fun footerTextIsScaledUp() {
        listOf(SMALL, MEDIUM, LARGE).forEach { size ->
            val frame = WereadBoardFrame.of(size[0], size[1], INSET)
            val innerW = size[0] - INSET * 2f
            val expected =
                (innerW * WereadBoardFrame.INFO_WIDTH_RATIO * WereadBoardFrame.INFO_TEXT_SCALE)
                    .coerceIn(9f, 42f)
            assertEquals(expected, frame.infoSize, 0.01f)
            assertTrue(frame.barBounds.bottom < frame.infoBottom)
        }
    }

    private companion object {
        /** 测试用左右边距。 */
        const val INSET: Float = 24f

        /** 小尺寸组件。 */
        val SMALL = floatArrayOf(300f, 220f)

        /** 常规尺寸组件。 */
        val MEDIUM = floatArrayOf(720f, 420f)

        /** 大尺寸组件。 */
        val LARGE = floatArrayOf(1000f, 620f)

        /** 宽扁组件。 */
        val WIDE = floatArrayOf(1200f, 480f)

        /** 极矮组件，用于验证分带不重叠。 */
        val SHORT = floatArrayOf(1200f, 360f)
    }
}
