package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 钟面样式标识。
 *
 * id 写入 `AppearanceConfig.clockFace` 并长期保存，发布后不可修改；
 * label 用于设置页与钟面选择页展示。
 */
enum class ClockFace(
    val id: String,
    val label: String,
) {
    DEFAULT("default", "大字"),
    STACKED("stacked", "上下"),
    FLIP("flip", "翻页"),
    BOXED("boxed", "方框"),
    ANALOG("analog", "指针"),
    ;

    companion object {
        /** 按配置标识查找样式，未知或缺失时回退到大字样式。 */
        fun fromId(id: String?): ClockFace = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 钟面绘制使用的时间快照。
 *
 * 绘制函数只读取该快照，不直接访问系统时钟，便于预览与单元测试构造固定时间。
 */
data class ClockTime(
    val hour24: Int,
    val minute: Int,
    val second: Int,
    val twelveHour: Boolean = false,
) {
    /** 小时文本：十二小时制去掉前导零，二十四小时制保持两位。 */
    val hourText: String
        get() =
            if (twelveHour) {
                val hour = hour24 % 12
                (if (hour == 0) 12 else hour).toString()
            } else {
                hour24.toString().padStart(2, '0')
            }

    /** 分钟文本，固定两位。 */
    val minuteText: String
        get() = minute.toString().padStart(2, '0')

    /** 组合后的时分文本。 */
    val timeText: String
        get() = "$hourText:$minuteText"

    companion object {
        /** 从日历快照构造时间对象。 */
        fun from(
            calendar: Calendar,
            twelveHour: Boolean,
        ): ClockTime =
            ClockTime(
                hour24 = calendar.get(Calendar.HOUR_OF_DAY),
                minute = calendar.get(Calendar.MINUTE),
                second = calendar.get(Calendar.SECOND),
                twelveHour = twelveHour,
            )
    }
}

/**
 * 钟面绘制集合。
 *
 * 每个绘制函数接收画布、可用区域、时间快照、文本画笔与字号基准，不修改画布状态，
 * 文字颜色取自传入画笔，字号在字号基准与区域尺寸之间自适应。
 * 尺寸比例均按基准字号或区域短边计算，不写死像素值。
 */
internal object ClockFaces {
    /** 按样式分派绘制。 */
    fun draw(
        face: ClockFace,
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
        baseSize: Float,
    ) {
        when (face) {
            ClockFace.DEFAULT -> drawDefault(canvas, area, time, paint, baseSize)
            ClockFace.STACKED -> drawStacked(canvas, area, time, paint, baseSize)
            ClockFace.FLIP -> drawFlip(canvas, area, time, paint, baseSize)
            ClockFace.BOXED -> drawBoxed(canvas, area, time, paint, baseSize)
            ClockFace.ANALOG -> drawAnalog(canvas, area, time, paint, baseSize)
        }
    }

    /**
     * 大字样式：单行时分文本，按区域宽度收缩字号后居中绘制。
     */
    fun drawDefault(
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
        baseSize: Float,
    ) {
        drawCenteredText(canvas, time.timeText, area, paint, baseSize)
    }

    /**
     * 上下样式：小时在上、分钟在下，只绘制数字。
     *
     * 原分隔线属于装饰边框，按透明背景要求取消；上下两段之间保留随字号变化的行距。
     */
    fun drawStacked(
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
        baseSize: Float,
    ) {
        if (area.width() <= 0f || area.height() <= 0f) return
        val lineGap = maxOf(1f, baseSize * 0.16f)
        val halfHeight = maxOf(1f, (area.height() - lineGap) / 2f)
        val lineY = area.centerY()
        val hourArea = RectF(area.left, area.top, area.right, area.top + halfHeight)
        val minuteArea = RectF(area.left, area.bottom - halfHeight, area.right, area.bottom)
        drawCenteredText(canvas, time.hourText, hourArea, paint, baseSize)
        drawCenteredText(canvas, time.minuteText, minuteArea, paint, baseSize)
    }

    /**
     * 翻页样式：小时与分钟横向并排，仅绘制数字，不绘制卡片底色与边框。
     *
     * 两张数字区之间保留随区域宽度变化的间隔，壁纸可直接从空白处透出。
     */
    fun drawFlip(
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
        baseSize: Float,
    ) {
        if (area.width() <= 0f || area.height() <= 0f) return
        val gap = maxOf(1f, area.width() * 0.04f)
        val cardWidth = (area.width() - gap) / 2f
        if (cardWidth <= 0f) return
        val leftCard = RectF(area.left, area.top, area.left + cardWidth, area.bottom)
        val rightCard =
            RectF(
                area.left + cardWidth + gap,
                area.top,
                area.left + cardWidth + gap + cardWidth,
                area.bottom,
            )
        val innerPad = maxOf(1f, baseSize * 0.08f)
        drawCenteredText(canvas, time.hourText, inset(leftCard, innerPad), paint, baseSize)
        drawCenteredText(canvas, time.minuteText, inset(rightCard, innerPad), paint, baseSize)
    }

    /**
     * 方框样式的间距版式：保留内边距，仅绘制时分文本，不绘制方框边框与底色。
     */
    fun drawBoxed(
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
        baseSize: Float,
    ) {
        if (area.width() <= 0f || area.height() <= 0f) return
        val outerPad = maxOf(1f, baseSize * 0.05f)
        val box = inset(area, outerPad)
        if (box.width() <= 0f || box.height() <= 0f) return
        val innerPad = maxOf(1f, baseSize * 0.12f)
        drawCenteredText(canvas, time.timeText, inset(box, innerPad), paint, baseSize)
    }

    /**
     * 指针样式：圆形表盘、十二个刻度与时分秒针。
     *
     * 表盘取区域短边作为直径并居中；12 点与 3 点刻度加粗加长，其余刻度保持细线。
     * 指针长度按表盘半径比例计算，秒针最细，中心绘制实心圆点。
     */
    fun drawAnalog(
        canvas: Canvas,
        area: RectF,
        time: ClockTime,
        paint: Paint,
        baseSize: Float,
    ) {
        val diameter = min(area.width(), area.height())
        if (diameter <= 0f) return
        val centerX = area.centerX()
        val centerY = area.centerY()
        val radius = diameter / 2f - maxOf(1f, diameter * 0.02f)
        if (radius <= 1f) return
        val lineWidth = maxOf(1f, baseSize * 0.04f, diameter * 0.018f)
        val thinPaint =
            Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = lineWidth
                strokeCap = Paint.Cap.ROUND
            }
        val boldPaint =
            Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = lineWidth * 2.4f
                strokeCap = Paint.Cap.ROUND
            }

        // 只保留刻度，不绘制表盘外圈边框

        // 十二个刻度：12 点与 3 点加粗加长
        for (index in 0 until 12) {
            val angle = Math.toRadians(index * 30.0 - 90.0)
            val bold = index == 0 || index == 3
            val outer = radius - lineWidth
            val length = radius * if (bold) 0.2f else 0.1f
            val inner = outer - length
            canvas.drawLine(
                centerX + (cos(angle) * inner).toFloat(),
                centerY + (sin(angle) * inner).toFloat(),
                centerX + (cos(angle) * outer).toFloat(),
                centerY + (sin(angle) * outer).toFloat(),
                if (bold) boldPaint else thinPaint,
            )
        }

        // 时针
        val hourAngle =
            Math.toRadians(
                (time.hour24 % 12 + time.minute / 60f) * 30.0 - 90.0,
            )
        drawHand(
            canvas,
            centerX,
            centerY,
            radius * 0.52f,
            hourAngle,
            Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = lineWidth * 2.2f
                strokeCap = Paint.Cap.ROUND
            },
        )

        // 分针
        val minuteAngle = Math.toRadians(time.minute * 6.0 - 90.0)
        drawHand(
            canvas,
            centerX,
            centerY,
            radius * 0.76f,
            minuteAngle,
            Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = lineWidth * 1.4f
                strokeCap = Paint.Cap.ROUND
            },
        )

        // 秒针
        val secondAngle = Math.toRadians(time.second * 6.0 - 90.0)
        drawHand(
            canvas,
            centerX,
            centerY,
            radius * 0.86f,
            secondAngle,
            Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = maxOf(1f, lineWidth * 0.7f)
                strokeCap = Paint.Cap.ROUND
            },
        )

        // 中心圆点
        canvas.drawCircle(
            centerX,
            centerY,
            maxOf(1f, lineWidth * 1.6f),
            Paint(paint).apply { style = Paint.Style.FILL },
        )
    }

    /** 在区域内按可用宽度收缩字号并居中绘制单行文本。 */
    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        area: RectF,
        paint: Paint,
        baseSize: Float,
    ) {
        if (text.isEmpty() || area.width() <= 0f || area.height() <= 0f) return
        val desired = min(baseSize, area.height() * 0.92f)
        val minimum = maxOf(1f, baseSize * 0.32f)
        paint.textAlign = Paint.Align.LEFT
        InkDraw.fitSize(text, paint, area.width(), desired, minimum)
        InkDraw.textCenterBaseline(canvas, text, area.centerX(), area.centerY(), paint)
    }

    /** 绘制一根从表盘中心出发的指针。 */
    private fun drawHand(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        length: Float,
        angleRadians: Double,
        paint: Paint,
    ) {
        canvas.drawLine(
            centerX,
            centerY,
            centerX + (cos(angleRadians) * length).toFloat(),
            centerY + (sin(angleRadians) * length).toFloat(),
            paint,
        )
    }

    /** 返回缩小后的矩形副本，不修改传入对象。 */
    private fun inset(
        rect: RectF,
        padding: Float,
    ): RectF =
        RectF(rect).apply {
            inset(padding, padding)
        }
}
