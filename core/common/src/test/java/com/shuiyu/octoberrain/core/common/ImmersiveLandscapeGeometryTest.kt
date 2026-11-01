package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveLandscapeGeometryTest {
    @Test
    fun visibleDateGapFitsDespitePositiveClockFontDescent() {
        val clockBaseline = 273f
        val oldFontDescent = 8f
        val dateInkTop = 280f
        assertTrue(dateInkTop < clockBaseline + oldFontDescent + 4f)
        assertTrue(ImmersiveLandscapeGeometry.dateInkFits(clockBaseline, dateInkTop, 320f, 420f, 4f))
    }

    @Test
    fun actualClockAndDateInkOverlapIsRejected() {
        assertFalse(ImmersiveLandscapeGeometry.dateInkFits(283f, 280f, 320f, 420f, 4f))
    }

    @Test
    fun finalDateInkCrossingDividerIsRejected() {
        assertFalse(ImmersiveLandscapeGeometry.dateInkFits(273f, 280f, 421f, 420f, 4f))
    }

    @Test
    fun exactVisibleInkGapBoundariesFit() {
        assertTrue(ImmersiveLandscapeGeometry.dateInkFits(273f, 277f, 416f, 420f, 4f))
    }

    @Test
    fun admissionPublishesTheUnchangedCandidateOrRestoresAllOriginalBounds() {
        val candidate = geometry(125f)
        assertSame(candidate, candidate.admitted(dateFits = true, statisticsFit = true))
        listOf(false to true, true to false, false to false).forEach { (dateFits, statsFit) ->
            val admitted = candidate.admitted(dateFits, statsFit)
            assertNull(admitted)
            assertEquals(candidate.originalBook, admitted?.book ?: candidate.originalBook)
        }
        val compact = geometry(90f)
        assertEquals(compact.originalBook, compact.admitted(true, true)?.book)
    }

    @Test
    fun compactSlotKeepsOriginalBookExactly() {
        listOf(30f, 89f, 90f).forEach { slot ->
            val geometry = geometry(slot)
            assertEquals(0f, geometry.weight, .0001f)
            assertEquals(geometry.originalBook, geometry.book)
            assertEquals(123f, geometry.blend(123f, 999f), 0f)
        }
    }

    @Test
    fun originalSlotControlsLinearContinuousWeight() {
        listOf(90f, 95f, 100f, 105f, 110f, 140f).forEach { slot ->
            val geometry = geometry(slot)
            assertEquals(((slot - 90f) / 20f).coerceIn(0f, 1f), geometry.weight, .0001f)
            assertEquals(slot, geometry.originalBook.height * .21f / 2f, .0001f)
        }
        listOf(90f, 100f, 110f).forEach { slot ->
            val before = geometry(slot - .001f)
            val after = geometry(slot + .001f)
            assertTrue(kotlin.math.abs(after.weight - before.weight) < .001f)
            assertTrue(kotlin.math.abs(after.book.left - before.book.left) < .1f)
            assertTrue(kotlin.math.abs(after.cover().width - before.cover().width) < .01f)
        }
    }

    @Test
    fun oceanTargetsUseFullWindowAndPreserveCoverAspect() {
        val geometry = geometry(125f)
        val cover = geometry.cover()
        assertEquals(.061f * 1680, geometry.clockLeft + geometry.homeX, .01f)
        assertEquals(.907f * 1680, geometry.railCenter + geometry.homeX, .01f)
        assertEquals(.289f * 1680, geometry.titleLeft + geometry.book.left + geometry.homeX, .01f)
        assertEquals(.774f * 1680, geometry.progress.right + geometry.book.left + geometry.homeX, .01f)
        assertEquals(.810f * 1680, geometry.refreshX + geometry.book.left + geometry.homeX, .01f)
        assertEquals(.243f * 1264, geometry.refreshY + geometry.book.top + geometry.homeY, .01f)
        assertEquals(.377f * 1264, cover.top + geometry.book.top + geometry.homeY, .01f)
        assertEquals(.414f * 1680, cover.left + geometry.book.left + geometry.homeX, 1f)
        assertEquals(.253f * 1680, cover.width, 1f)
        assertEquals(.487f * 1264, cover.height, 1f)
        assertEquals(1.45f, cover.height / cover.width, .0001f)
        listOf(.339f, .524f, .712f).forEachIndexed { index, y ->
            assertEquals(y * 1264, geometry.dividerYs[index] + geometry.book.top + geometry.homeY, .01f)
        }
        listOf(.428f, .615f, .802f).forEachIndexed { index, y ->
            assertEquals(y * 1264, geometry.valueYs[index] + geometry.book.top + geometry.homeY, .01f)
        }
        listOf(.470f, .659f, .845f).forEachIndexed { index, y ->
            assertEquals(y * 1264, geometry.labelYs[index] + geometry.book.top + geometry.homeY, .01f)
        }
    }

    @Test
    fun singleLineTargetKeepsProgressWhileLongTitlesRetreatAtReadableSize() {
        val geometry = geometry(125f)
        val single = geometry.readingBudget(84f, 42f, 47f, 8f, 1)
        val long = geometry.readingBudget(84f, 42f, 47f, 8f, 2)
        assertEquals(geometry.progress.top, single.barY, .001f)
        assertTrue(long.barY > single.barY)
        assertTrue(long.barY >= geometry.titleTop + 84f * 2 + 42f + 16f)
        assertTrue(long.cover.top >= long.barY + 47f + 16f + 4f)
        assertTrue(long.cover.bottom <= geometry.coverArea.bottom)
    }

    @Test
    fun rootAndHomeOriginsAreConvertedOnce() {
        val geometry =
            ImmersiveLandscapeGeometry.create(
                ImmersiveLandscapeBounds(17f, 23f, 1697f, 1287f),
                37f,
                63f,
                1660f,
                1224f,
                ImmersiveLandscapeBounds(50f, 20f, 1450f, 1200f),
                2f,
            )
        assertEquals(17f + 1680f * .289f - 37f, geometry.homeX(.289f), .001f)
        assertEquals(23f + 1264f * .243f - 63f, geometry.homeY(.243f), .001f)
        assertEquals(geometry.homeX(.289f), geometry.titleLeft + geometry.book.left, .001f)
        assertEquals(geometry.homeY(.243f), geometry.refreshY + geometry.book.top, .001f)
        assertTrue(geometry.statsLeft >= 0f)
        assertTrue(geometry.coverArea.right <= geometry.book.width)
    }

    @Test
    fun narrowWindowAndLargeMeasuredFontsSpendCoverSpace() {
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
            val normal = geometry.readingBudget(height * .065f, height * .034f, height * .037f, 4f, 1)
            val large = geometry.readingBudget(height * .12f, height * .07f, height * .075f, 8f)
            assertTrue(large.barY >= normal.barY)
            assertTrue(large.cover.top >= normal.cover.top)
            assertTrue(large.cover.height <= normal.cover.height)
            assertTrue(large.cover.bottom <= geometry.coverArea.bottom + .01f)
            assertTrue(large.cover.left >= geometry.coverArea.left)
            assertTrue(large.cover.right <= geometry.coverArea.right)
            assertTrue(large.cover.top > large.barY + height * .075f)
            assertEquals(1.45f, large.cover.height / large.cover.width, .0001f)
            assertTrue(geometry.book.left <= geometry.homeX(.061f))
            assertTrue(geometry.book.right >= geometry.homeX(.84f))
        }
    }

    @Test
    fun glyphCalibrationInheritsAppAndSystemMagnification() {
        listOf(1f, 1.3f, 2f, 2.6f).forEach { factor ->
            val size = ImmersiveLandscapeGeometry.magnifiedGlyphSize(48f, 28f * factor, 28f)
            assertEquals(48f * factor, size, .001f)
            assertTrue(size >= 28f * factor)
            val smallTarget = ImmersiveLandscapeGeometry.magnifiedGlyphSize(18f, 28f * factor, 28f)
            assertEquals(28f * factor, smallTarget, .001f)
        }
    }

    @Test
    fun fourFavoriteRailAndAuthorUseFrozenWindowCoordinates() {
        val geometry = geometry(125f)
        listOf(.152f, .343f, .519f, .707f, .869f).forEachIndexed { index, fraction ->
            assertEquals(fraction * 1264, geometry.railIconYs[index] + geometry.homeY, .001f)
        }
        assertEquals(.915f * 1264, geometry.railLabelBaseline + geometry.homeY, .001f)
        assertEquals(
            .214f * 1264,
            geometry.authorBaseline + geometry.book.top + geometry.homeY,
            .001f,
        )
        val cover = geometry.readingBudget(84f, 42f, 47f, 8f).cover
        assertEquals(.414f, (cover.left + geometry.book.left + geometry.homeX) / 1680, .005f)
        assertEquals(.377f, (cover.top + geometry.book.top + geometry.homeY) / 1264, .005f)
        assertEquals(.253f, cover.width / 1680, .005f)
        assertEquals(.487f, cover.height / 1264, .005f)
    }

    private fun geometry(slotDp: Float): ImmersiveLandscapeGeometry =
        ImmersiveLandscapeGeometry.create(
            ImmersiveLandscapeBounds(0f, 0f, 1680f, 1264f),
            0f,
            24f,
            1680f,
            1240f,
            ImmersiveLandscapeBounds(100f, 20f, 1450f, 20f + slotDp * 2f / .21f),
            2f,
        )
}
