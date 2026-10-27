package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors

/** 组件库内的静态示意：复用钟面绘制，不创建组件、计时器或读取个人数据。 */
internal class WidgetPreviewView(
    context: Context,
    private val widgetId: String,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sample = ClockTime(10, 28, 0, date = ClockDate(2026, 10, 27, 3))

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        // 统一预览画布，尺寸由卡片约束；钟面按自身几何适配。
        val scale = minOf(width / 180f, height / 140f)
        canvas.translate((width - 180f * scale) / 2f, (height - 140f * scale) / 2f)
        canvas.scale(scale, scale)
        paint.color = InkColors.INK
        paint.typeface = DesignRuntime.typeface("sans")
        paint.style = Paint.Style.FILL
        when (widgetId) {
            "calendar" -> drawCalendar(canvas)
            "todo_week" -> drawWeek(canvas)
            else -> drawClock(canvas)
        }
        canvas.restoreToCount(save)
    }

    private fun drawClock(canvas: Canvas) {
        val appearance = AppServices.config.state.value.appearance
        val face =
            if (widgetId ==
                "clock"
            ) {
                ClockFace.fromId(appearance.clockFace)
            } else {
                ClockFace.fromId(widgetId.removePrefix("clock_"))
            }
        paint.typeface = clockTypeface(face, appearance.clockFont)
        paint.textScaleX = clockNumberScale(face, appearance.clockFont)
        val area = RectF(6f, 8f, 174f, if (face.includesDate) 132f else 110f)
        ClockFaces.draw(face, canvas, area, sample, paint, 62f)
        if (!face.includesDate) {
            paint.textScaleX = 1f
            drawClockText(canvas, "10月27日 星期二", RectF(6f, 112f, 174f, 134f), paint, 13f)
        }
    }

    private fun drawCalendar(canvas: Canvas) {
        drawClockText(canvas, "2026年10月", RectF(0f, 0f, 180f, 22f), paint, 16f)
        "一二三四五六日".forEachIndexed { index, day ->
            val left = index * 25f + 2f
            drawClockText(canvas, day.toString(), RectF(left, 24f, left + 24f, 42f), paint, 11f)
        }
        for (day in 1..31) {
            val position = day + 2
            val left = position % 7 * 25f + 2f
            val top = 44f + position / 7 * 18f
            if (day == 27) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.3f
                canvas.drawRect(left + 2, top, left + 22, top + 18, paint)
                paint.style = Paint.Style.FILL
            }
            drawClockText(canvas, day.toString(), RectF(left, top, left + 24, top + 18), paint, 12f)
        }
    }

    private fun drawWeek(canvas: Canvas) {
        drawClockText(canvas, "待办周历", RectF(0f, 0f, 180f, 24f), paint, 16f)
        "一二三四五六日".forEachIndexed { index, day ->
            val left = index * 25f + 2
            drawClockText(canvas, day.toString(), RectF(left, 28f, left + 24, 48f), paint, 12f)
            paint.strokeWidth = 1f
            canvas.drawLine(left, 52f, left, 126f, paint)
            if (index % 2 == 0) {
                paint.style = Paint.Style.STROKE
                canvas.drawRect(left + 5, 62f, left + 11, 68f, paint)
                canvas.drawLine(left + 5, 78f, left + 19, 78f, paint)
                canvas.drawLine(left + 5, 88f, left + 16, 88f, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }
}
