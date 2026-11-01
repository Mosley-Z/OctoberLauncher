package com.shuiyu.octoberrain.feature.home

/** One pending frame for successful icon loads; dormant while the host cannot draw. */
internal class HomeIconRedraw(
    private val post: (Runnable) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val redraw: () -> Unit,
) {
    private var attached = false
    private var visible = false
    private var preview = false
    private val canDraw: Boolean
        get() = !released && attached && (visible || preview)
    private var released = false
    private var dirty = false
    private var scheduled = false
    private val frame =
        Runnable {
            scheduled = false
            if (canDraw && dirty) {
                dirty = false
                redraw()
                schedule()
            }
        }

    fun changed() {
        if (released) return
        dirty = true
        schedule()
    }

    fun setAttached(value: Boolean) {
        attached = value
        update()
    }

    fun setVisible(value: Boolean) {
        visible = value
        update()
    }

    fun setPreviewMode(value: Boolean) {
        preview = value
        update()
    }

    fun release() {
        released = true
        dirty = false
        update()
    }

    private fun update() {
        if (!canDraw) {
            if (scheduled) cancel(frame)
            scheduled = false
        } else {
            schedule()
        }
    }

    private fun schedule() {
        if (!canDraw || !dirty || scheduled) return
        scheduled = true
        post(frame)
    }
}
