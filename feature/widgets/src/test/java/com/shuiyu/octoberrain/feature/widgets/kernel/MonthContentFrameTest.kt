package com.shuiyu.octoberrain.feature.widgets.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthContentFrameTest {
    @Test
    fun retainsWidthsAtOrBelowCap() {
        listOf(384, 412).forEach { width ->
            val frame = MonthContentFrame(width, portraitMonth = true)
            assertEquals(width, frame.width)
            assertEquals(0f, frame.offsetX, 0f)
            assertEquals(20f, frame.localX(20f)!!, 0f)
        }
    }

    @Test
    fun centersCappedMonthIncludingHalfPixelOffset() {
        listOf(553 to 70.5f, 592 to 90f).forEach { (width, offset) ->
            val frame = MonthContentFrame(width, portraitMonth = true)
            assertEquals(412, frame.width)
            assertEquals(offset, frame.offsetX, 0f)
            assertEquals(0f, frame.localX(offset)!!, 0f)
            assertEquals(200f, frame.localX(offset + 200f)!!, 0f)
            assertEquals(411.5f, frame.localX(offset + 411.5f)!!, 0f)
            assertNull(frame.localX(offset - .5f))
            assertNull(frame.localX(offset + 412f))
            assertNull(frame.localX(width.toFloat()))
        }
    }

    @Test
    fun otherCallersRetainWidthAndCoordinates() {
        val frame = MonthContentFrame(592)
        assertEquals(592, frame.width)
        assertEquals(0f, frame.offsetX, 0f)
        assertEquals(550f, frame.localX(550f)!!, 0f)
    }
}
