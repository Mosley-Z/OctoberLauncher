package com.shuiyu.octoberrain.feature.widgets.kernel

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import com.inkread.weekread.kernel.CardState
import com.inkread.weekread.kernel.HitRegions
import com.inkread.weekread.kernel.render.HostSwitchRenderer
import com.shuiyu.octoberrain.core.device.DeviceRuntime
import com.shuiyu.octoberrain.feature.widgets.WereadBoardZone

/** Dedicated landscape canvas composition. Portrait and book keep the shipped renderer. */
internal class LandscapeCardLayout(
    private val nativeRenderer: () -> HostSwitchRenderer,
) {
    private var geometry = LandscapeCardGeometry.compute(464, 420)
    private val todo = LandscapeTodoRenderer()
    private var form = ""
    private var currentZones: List<WereadBoardZone> = emptyList()
    var localUpdatedLabel: String? = null
    var referenceWidth: Int? = null

    val noteGeometry: LandscapeCardGeometry get() = geometry

    val scrollMax: Int get() = if (form == "todo") todo.scrollMax.toInt() else 0
    val scrollViewHeight: Int get() = if (form == "todo") todo.viewHeight.toInt() else 0
    val zones: List<WereadBoardZone> get() = currentZones

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        formId: String,
        state: CardState,
        refreshing: Boolean,
    ) {
        if (form != formId) todo.reset()
        form = formId
        geometry =
            LandscapeCardGeometry.compute(
                width,
                height,
                formId == "week" || formId == "month",
                minimumTouch(canvas),
                referenceWidth ?: width,
            )
        val g = geometry
        val switchRenderer = nativeRenderer()
        val ink = LandscapeInk(canvas, g.unit)
        clearBoxes(state)
        val zones = mutableListOf(zone(g.title, "form:next"))
        landscapeRefreshZone(formId, g)?.let { zones += zone(it, "data:refresh") }
        chrome(ink, g, state, refreshing, switchRenderer)
        when {
            formId == "todo" -> zones += todo.draw(ink, g, state)
            formId == "note" -> {
                LandscapeNoteRenderer.draw(ink, g, state)
                addNoteZone(zones, state.prevBox(), "note:prev")
                addNoteZone(zones, state.openBox(), "note:next")
                addNoteZone(zones, state.expandBox(), "note:expand")
            }
            !state.errorText().isNullOrBlank() -> empty(ink, g, "获取失败", state.errorText().orEmpty())
            !state.phMain().isNullOrBlank() -> empty(ink, g, state.phMain().orEmpty(), state.phSub().orEmpty())
            else -> LandscapePeriodRenderer.draw(ink, g, state, formId == "month")
        }
        currentZones = zones
    }

    fun hitTest(
        x: Float,
        y: Float,
    ): Int {
        val action =
            currentZones
                .firstOrNull {
                    x >= it.x &&
                        x < it.x + it.width &&
                        y >= it.y &&
                        y < it.y + it.height
                }?.action
        return when {
            action == "note:prev" -> HitRegions.PREV
            action == "note:next" -> HitRegions.OPEN
            action == "note:expand" -> HitRegions.EXPAND
            action?.startsWith("todo:") == true -> HitRegions.TODO_ITEM_BASE + action.substringAfter(':').toInt()
            else -> HitRegions.NONE
        }
    }

    fun scrollBy(delta: Int): Boolean = form == "todo" && todo.scrollBy(delta)

    fun resetScroll() = todo.reset()

    fun release() {
        currentZones = emptyList()
        todo.reset()
    }

    private fun chrome(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        state: CardState,
        refreshing: Boolean,
        switchRenderer: HostSwitchRenderer,
    ) {
        val u = g.unit
        ink.rule(g.width - u, 0f, g.width - u, g.height, Color.LTGRAY)
        ink.box(g.bookmark, Color.BLACK)
        bookmark(ink, g, switchRenderer)
        val period = form == "week" || form == "month"
        if (form != "todo") {
            drawLandscapeRefresh(ink, g.refreshInk, refreshing)
            val label = landscapeUpdatedRows(localUpdatedLabel ?: state.updatedLabel()).joinToString("\n")
            verticalChromeText(ink, label, g.chrome.updatedInk, 13f)
        }
        if (period) {
            LandscapePeriodRenderer.meta(ink, g, state, form == "month")
            return
        }
        val metadata =
            when (form) {
                "note" -> ""
                "todo" -> "${state.todoCount()} 项未完成"
                else -> ""
            }
        ink.text(
            metadata,
            g.meta.left,
            g.meta.top + 10f * u,
            if (form == "note" || form == "todo") 13f else 15f,
            g.meta.width,
            color = Color.DKGRAY,
        )
    }

    private fun bookmark(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        switchRenderer: HostSwitchRenderer,
    ) {
        val title =
            when (form) {
                "month" -> "本月"
                "note" -> "本记"
                "todo" -> "待办"
                else -> "本周"
            }
        val period = form == "week" || form == "month"
        verticalChromeText(ink, title, g.chrome.titleInk, if (period) 26f else 27f, Typeface.SERIF)
        val icon = g.chrome.switchIcon
        switchRenderer.drawSwitch(ink.canvas, icon.left, icon.top, icon.width)
    }

    private fun clearBoxes(state: CardState) {
        listOf(
            state.prevBox(),
            state.openBox(),
            state.expandBox(),
            state.filterBox(),
            state.pickBox(),
            state.exportBox(),
            state.deskBox(),
        ).forEach { it.setEmpty() }
        state.todoBoxes().clear()
    }

    private fun empty(
        ink: LandscapeInk,
        g: LandscapeCardGeometry,
        title: String,
        subtitle: String,
    ) {
        ink.text(title, g.body.left, g.body.top + 30f * g.unit, 20f, g.body.width)
        ink.text(subtitle, g.body.left, g.body.top + 65f * g.unit, 13f, g.body.width, color = Color.DKGRAY)
    }

    private fun zone(
        rect: LandscapeRect,
        action: String,
    ): WereadBoardZone = WereadBoardZone(rect.left, rect.top, rect.width, rect.height, action)
}

/** The outer widget scales 368 logical pixels; retain a real 44dp hit height after that scale. */
private fun minimumTouch(canvas: Canvas): Float {
    val values = FloatArray(9)

    @Suppress("DEPRECATION")
    val matrix = canvas.matrix
    matrix.getValues(values)
    val scale = kotlin.math.hypot(values[Matrix.MSCALE_X], values[Matrix.MSKEW_Y]).coerceAtLeast(.01f)
    val density = if (DeviceRuntime.isReady()) DeviceRuntime.profile.density else 1f
    return 44f * density / scale
}

private fun addNoteZone(
    zones: MutableList<WereadBoardZone>,
    rect: android.graphics.RectF,
    action: String,
) {
    if (!rect.isEmpty) zones += WereadBoardZone(rect.left, rect.top, rect.width(), rect.height(), action)
}

private fun verticalChromeText(
    ink: LandscapeInk,
    text: String,
    area: LandscapeRect,
    size: Float,
    face: Typeface = Typeface.DEFAULT,
) {
    val multiline = '\n' in text
    val rows = if (multiline) text.split('\n') else text.map { it.toString() }
    val step = minOf(size * 1.4f * ink.unit, area.height / rows.size.coerceAtLeast(1))
    val actual = minOf(size, step * .82f / ink.unit)
    rows.forEachIndexed { index, row ->
        ink.paint.typeface = face
        ink.paint.textSize = actual * ink.unit
        if (multiline) {
            val box = LandscapeRect(area.left, area.top + index * step, area.right, area.top + (index + 1) * step)
            val save = ink.canvas.save()
            if (row == ":") ink.canvas.rotate(90f, box.centerX, box.centerY)
            ink.centered(row, box, actual)
            ink.canvas.restoreToCount(save)
        } else {
            val x = area.centerX - ink.paint.measureText(row) / 2f
            ink.text(row, x, area.top + index * step, actual, face = face)
        }
    }
}

private fun drawLandscapeRefresh(
    ink: LandscapeInk,
    rect: LandscapeRect,
    refreshing: Boolean,
) {
    val paint = ink.paint
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = Color.BLACK
    paint.strokeWidth = (if (refreshing) 2.2f else 1.5f) * ink.unit
    ink.canvas.drawArc(rect.androidRect(), -80f, 290f, false, paint)
    val r = rect.width / 2f
    val x = rect.centerX
    val y = rect.centerY
    val arrow =
        android.graphics.Path().apply {
            moveTo(x - .866f * r, y - .5f * r)
            lineTo(x - 1.18f * r, y)
            lineTo(x - .6f * r, y - .05f * r)
            close()
        }
    paint.style = android.graphics.Paint.Style.FILL
    ink.canvas.drawPath(arrow, paint)
}
