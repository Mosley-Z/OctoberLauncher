package com.shuiyu.octoberrain.feature.home

import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import com.shuiyu.octoberrain.core.widget.WidgetNoteOverlaySource

/** Keeps the established portrait reader independent of the landscape expansion. */
internal class DesktopNoteOverlay(
    invalidate: () -> Unit,
) {
    private val portrait = NoteOverlayView(invalidate)
    private val landscape = LandscapeNoteOverlayView(invalidate)
    val visible: Boolean get() = portrait.visible || landscape.visible

    fun open(
        source: WidgetNoteOverlaySource,
        bounds: RectF,
    ): Boolean {
        close()
        return if (source.currentNoteContent()?.presentation?.landscape !=
            null
        ) {
            landscape.open(source, bounds)
        } else {
            portrait.open(source, bounds)
        }
    }

    fun close(): Boolean {
        val wasVisible = visible
        portrait.close()
        landscape.close()
        return wasVisible
    }

    fun draw(
        canvas: Canvas,
        available: RectF,
    ) {
        if (portrait.visible) portrait.draw(canvas, available)
        if (landscape.visible) landscape.draw(canvas, available)
    }

    fun onTouchEvent(event: MotionEvent): Boolean =
        if (landscape.visible) landscape.onTouchEvent(event) else portrait.onTouchEvent(event)
}
