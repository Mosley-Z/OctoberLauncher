package com.shuiyu.octoberrain.feature.widgets.kernel

import android.graphics.Color
import android.graphics.Typeface
import com.inkread.weekread.kernel.CardState
import com.shuiyu.octoberrain.feature.widgets.WereadBoardZone

/** Revision 2 checklist; visual boxes and independently sized hit rectangles share row geometry. */
internal class LandscapeTodoRenderer {
    var scrollY = 0f
        private set
    var scrollMax = 0f
        private set
    var viewHeight = 0f
        private set

    fun draw(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        state: CardState,
    ): List<WereadBoardZone> {
        val u = g.unit
        val viewport = LandscapeRect(g.body.left, g.body.top, g.body.right, g.height - 8f * u)
        viewHeight = viewport.height
        val todos = state.todo().orEmpty()
        val rows =
            todos.map { item ->
                val lines = ink.lines(item.content.orEmpty(), viewport.width - 34f * u, 19f, Typeface.DEFAULT)
                val date =
                    item
                        .whenLabel()
                        .orEmpty()
                        .takeIf { item.hasDate() || item.hasTime() }
                        .orEmpty()
                Triple(lines, date, LandscapeTodoGeometry.rowHeight(lines.size, date.isNotBlank(), u))
            }
        scrollMax = (rows.sumOf { it.third.toDouble() }.toFloat() - viewport.height).coerceAtLeast(0f)
        scrollY = scrollY.coerceIn(0f, scrollMax)
        state.todoBoxes().clear()
        val zones = mutableListOf<WereadBoardZone>()
        if (rows.isEmpty()) {
            ink.text("暂无待办", viewport.left, viewport.top + 26f * u, 19f, viewport.width)
            return zones
        }
        val save = ink.canvas.save()
        ink.canvas.clipRect(viewport.androidRect())
        var top = viewport.top - scrollY
        rows.forEachIndexed { index, row ->
            val bottom = top + row.third
            val touch = LandscapeTodoGeometry.visibleRow(viewport, top, row.third)
            state.todoBoxes().add(touch?.androidRect() ?: android.graphics.RectF())
            if (touch != null) {
                zones += WereadBoardZone(touch.left, touch.top, touch.width, touch.height, "todo:$index")
            }
            drawRow(ink, viewport, top, bottom, row.first, row.second)
            top = bottom
        }
        ink.canvas.restoreToCount(save)
        return zones
    }

    fun scrollBy(delta: Int): Boolean {
        val target = (scrollY + delta).coerceIn(0f, scrollMax)
        val changed = target != scrollY
        scrollY = target
        return changed
    }

    fun reset() {
        scrollY = 0f
        scrollMax = 0f
    }

    private fun drawRow(
        ink: LandscapeInk,
        viewport: LandscapeRect,
        top: Float,
        bottom: Float,
        lines: List<String>,
        date: String,
    ) {
        val u = ink.unit
        val y = top + 16f * u
        ink.box(LandscapeRect(viewport.left, y + 2f * u, viewport.left + 22f * u, y + 24f * u))
        lines.forEachIndexed { index, line ->
            ink.text(
                line,
                viewport.left + 32f * u,
                y + index * 28.5f * u,
                19f,
                viewport.width - 32f * u,
            )
        }
        if (date.isNotBlank()) {
            ink.text(
                date,
                viewport.left + 32f * u,
                y + lines.size * 28.5f * u + 5f * u,
                13f,
                viewport.width - 32f * u,
                color = Color.DKGRAY,
            )
        }
        ink.rule(viewport.left, bottom, viewport.right, bottom)
    }
}
