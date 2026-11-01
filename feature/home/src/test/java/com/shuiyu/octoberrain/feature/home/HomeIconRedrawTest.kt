package com.shuiyu.octoberrain.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeIconRedrawTest {
    private class Host {
        val frames = mutableListOf<Runnable>()
        var home = 0
        var overlay = 0
        var overlayOpen = false
        var duringRedraw: () -> Unit = {}
        val state =
            HomeIconRedraw(
                post = { frames.add(it) },
                cancel = { frames.remove(it) },
                redraw = {
                    home++
                    if (overlayOpen) overlay++
                    duringRedraw()
                },
            )

        fun show() {
            state.setAttached(true)
            state.setVisible(true)
        }

        fun frame() = frames.removeAt(0).run()
    }

    @Test fun coldBurstAndCurrentOverlayAreCoalescedPerFrame() {
        val host = Host()
        host.show()
        repeat(20) { host.state.changed() }
        assertEquals(1, host.frames.size)
        host.frame()
        assertEquals(1, host.home)
        assertEquals(0, host.overlay)
        repeat(20) { host.state.changed() }
        host.overlayOpen = true
        host.frame()
        assertEquals(2, host.home)
        assertEquals(1, host.overlay)
        assertEquals(0, host.frames.size)
    }

    @Test fun hiddenAndDetachedPreserveDirtyButReleaseDiscardsIt() {
        val host = Host()
        host.show()
        host.state.changed()
        host.state.setVisible(false)
        assertEquals(0, host.frames.size)
        repeat(20) { host.state.changed() }
        host.state.setAttached(false)
        host.state.setVisible(true)
        assertEquals(0, host.frames.size)
        host.state.setAttached(true)
        assertEquals(1, host.frames.size)
        host.frame()
        assertEquals(1, host.home)
        host.state.changed()
        host.state.release()
        host.state.changed()
        host.show()
        assertEquals(0, host.frames.size)
    }

    @Test fun completionDuringRedrawSchedulesOnlyOneFollowingFrame() {
        val host = Host()
        host.show()
        host.duringRedraw = { repeat(20) { host.state.changed() } }
        host.state.changed()
        host.frame()
        assertEquals(1, host.frames.size)
        host.duringRedraw = {}
        host.frame()
        assertEquals(2, host.home)
        assertEquals(0, host.frames.size)
    }

    @Test fun previewWithoutResumeUsesActualDrawEligibility() {
        val host = Host()
        host.state.setAttached(true)
        host.state.changed()
        assertEquals(0, host.frames.size)
        host.state.setPreviewMode(true)
        assertEquals(1, host.frames.size)
        host.frame()
        assertEquals(1, host.home)
        host.state.setAttached(false)
        host.state.changed()
        assertEquals(0, host.frames.size)
        host.state.setAttached(true)
        assertEquals(1, host.frames.size)
        host.state.setPreviewMode(false)
        assertEquals(0, host.frames.size)
        host.state.setPreviewMode(true)
        host.state.release()
        host.state.changed()
        assertEquals(0, host.frames.size)
    }
}
