package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WereadBoardGesturesTest {
    private class Host : WereadBoardGestureHost() {
        override val form = WereadBoardForm.BOOK
        override val frame = WereadBoardFrame.of(440f, 440f, 0f)
        override val carousel = WereadBoardCarousel().apply { reset(3, 1) }
        var target: Int? = null

        override fun settle(target: Int) {
            this.target = target
        }
    }

    @Test
    fun coverSwipeSelectsBothDirectionsAndCancellationKeepsSelection() {
        val host = Host()
        val gestures = WereadBoardGestures(host, 8f)
        val x = host.frame.coverArea.centerX
        val y = host.frame.coverArea.centerY
        val distance = host.frame.coverWidth * .5f
        gestures.down(x, y)
        assertTrue(gestures.move(x + distance, y))
        gestures.up()
        assertEquals(0, host.target)
        gestures.down(x, y)
        gestures.move(x - distance, y)
        gestures.up()
        assertEquals(2, host.target)
        host.target = null
        gestures.down(x, y)
        gestures.move(x + distance, y)
        gestures.cancel()
        assertEquals(null, host.target)
        assertEquals(1, host.carousel.selectedIndex)
    }

    @Test
    fun outsideCoverAndVerticalMotionDoNotSwitchBooks() {
        val host = Host()
        val gestures = WereadBoardGestures(host, 8f)
        gestures.down(10f, 1f)
        assertFalse(gestures.move(220f, 1f))
        assertFalse(gestures.up())
        val area = host.frame.coverArea
        gestures.down(area.centerX, area.centerY)
        assertFalse(gestures.move(area.centerX + 15f, area.centerY + 100f))
        assertFalse(gestures.up())
        assertEquals(null, host.target)
    }
}
