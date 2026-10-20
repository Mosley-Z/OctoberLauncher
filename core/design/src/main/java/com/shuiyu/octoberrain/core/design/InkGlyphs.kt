package com.shuiyu.octoberrain.core.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * 单色矢量图标：以路径与线段绘制，适配墨水屏的黑白二值显示。
 *
 * 图标种类集中在 Kind 枚举，绘制统一从中心点与尺寸推导，
 * 调用方只需提供画布、中心与目标边长。
 */
object InkGlyphs {
    /** 图标种类。 */
    enum class Kind {
        BACK,
        CHEVRON,
        REFRESH,
        WIFI,
        BLUETOOTH,
        BATTERY,

        /** 充电闪电。 */
        BOLT,
        SUN,
        SEARCH,
        PLUS,
        CHECK,
        CLOSE,
        GEAR,
        BOOK,
        FOLDER,
        LIST,
        VOLUME,
        LOCK,
    }

    /**
     * 绘制图标。
     *
     * 参数 canvas 为画布；kind 为图标种类；cx 与 cy 为中心点坐标；
     * size 为图标外接正方形边长；paint 为画笔，函数内部会设置颜色与线宽。
     * haloColor 非透明时先以较粗的描边绘制白色外轮廓，用于壁纸上提高可读性。
     */
    fun draw(
        canvas: Canvas,
        kind: Kind,
        cx: Float,
        cy: Float,
        size: Float,
        paint: Paint,
        haloColor: Int = 0,
        haloWidth: Float = 0f,
    ) {
        if (haloColor != 0) {
            paint.color = haloColor
            drawOnce(canvas, kind, cx, cy, size, paint, haloWidth)
        }
        paint.color = InkColors.INK
        drawOnce(canvas, kind, cx, cy, size, paint, 0f)
    }

    /** 按指定附加描边宽度绘制一遍图标路径。 */
    private fun drawOnce(
        canvas: Canvas,
        kind: Kind,
        cx: Float,
        cy: Float,
        size: Float,
        paint: Paint,
        extraStroke: Float,
    ) {
        val stroke = maxOf(1.6f, size * 0.09f) + extraStroke
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val half = size / 2f
        val left = cx - half
        val top = cy - half
        when (kind) {
            Kind.BACK -> {
                canvas.drawLine(cx + half * 0.45f, cy, cx - half * 0.45f, cy, paint)
                canvas.drawLine(cx - half * 0.45f, cy, cx, cy - half * 0.45f, paint)
                canvas.drawLine(cx - half * 0.45f, cy, cx, cy + half * 0.45f, paint)
            }

            Kind.CHEVRON -> {
                canvas.drawLine(cx - half * 0.25f, cy - half * 0.35f, cx + half * 0.25f, cy, paint)
                canvas.drawLine(cx + half * 0.25f, cy, cx - half * 0.25f, cy + half * 0.35f, paint)
            }

            Kind.REFRESH -> {
                val rect = RectF(cx - half * 0.62f, cy - half * 0.62f, cx + half * 0.62f, cy + half * 0.62f)
                canvas.drawArc(rect, -70f, 300f, false, paint)
                val ex = cx + half * 0.62f * kotlin.math.cos(Math.toRadians(-70.0)).toFloat()
                val ey = cy + half * 0.62f * kotlin.math.sin(Math.toRadians(-70.0)).toFloat()
                canvas.drawLine(ex, ey, ex - half * 0.34f, ey, paint)
                canvas.drawLine(ex, ey, ex, ey + half * 0.34f, paint)
            }

            Kind.WIFI -> {
                val path = Path()
                for (i in 0..2) {
                    val inset = size * (0.14f + i * 0.17f)
                    path.reset()
                    path.addArc(
                        RectF(left + inset, top + inset, cx + half - inset, cy + half - inset),
                        225f,
                        90f,
                    )
                    canvas.drawPath(path, paint)
                }
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy + half * 0.45f, stroke * 0.9f, paint)
            }

            Kind.BLUETOOTH -> {
                canvas.drawLine(cx, top + size * 0.08f, cx, top + size * 0.92f, paint)
                canvas.drawLine(cx, cy - half * 0.45f, cx + half * 0.45f, cy - half * 0.12f, paint)
                canvas.drawLine(cx + half * 0.45f, cy - half * 0.12f, cx - half * 0.45f, cy + half * 0.2f, paint)
                canvas.drawLine(cx, cy + half * 0.45f, cx + half * 0.45f, cy + half * 0.12f, paint)
                canvas.drawLine(cx + half * 0.45f, cy + half * 0.12f, cx - half * 0.45f, cy - half * 0.2f, paint)
            }

            Kind.BATTERY -> {
                val body = RectF(left + size * 0.04f, top + size * 0.24f, cx + half * 0.78f, top + size * 0.76f)
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(body, size * 0.06f, size * 0.06f, paint)
                canvas.drawLine(body.right, cy - size * 0.08f, body.right, cy + size * 0.08f, paint)
            }

            Kind.BOLT -> {
                // 实心闪电：上窄下宽的两段折线闭合，用于替代“充”字。
                val path = Path()
                path.moveTo(cx + half * 0.16f, cy - half * 0.56f)
                path.lineTo(cx - half * 0.40f, cy + half * 0.04f)
                path.lineTo(cx - half * 0.04f, cy + half * 0.04f)
                path.lineTo(cx - half * 0.18f, cy + half * 0.58f)
                path.lineTo(cx + half * 0.42f, cy - half * 0.06f)
                path.lineTo(cx + half * 0.08f, cy - half * 0.06f)
                path.close()
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)
            }

            Kind.SUN -> {
                canvas.drawCircle(cx, cy, size * 0.2f, paint)
                for (i in 0 until 8) {
                    val angle = Math.toRadians(i * 45.0)
                    val cos = kotlin.math.cos(angle).toFloat()
                    val sin = kotlin.math.sin(angle).toFloat()
                    canvas.drawLine(
                        cx + cos * half * 0.34f,
                        cy + sin * half * 0.34f,
                        cx + cos * half * 0.62f,
                        cy + sin * half * 0.62f,
                        paint,
                    )
                }
            }

            Kind.SEARCH -> {
                canvas.drawCircle(cx - size * 0.08f, cy - size * 0.08f, size * 0.3f, paint)
                canvas.drawLine(
                    cx + size * 0.12f,
                    cy + size * 0.12f,
                    cx + size * 0.4f,
                    cy + size * 0.4f,
                    paint,
                )
            }

            Kind.PLUS -> {
                canvas.drawLine(cx, cy - half * 0.55f, cx, cy + half * 0.55f, paint)
                canvas.drawLine(cx - half * 0.55f, cy, cx + half * 0.55f, cy, paint)
            }

            Kind.CHECK -> {
                canvas.drawLine(cx - half * 0.5f, cy, cx - half * 0.12f, cy + half * 0.36f, paint)
                canvas.drawLine(cx - half * 0.12f, cy + half * 0.36f, cx + half * 0.55f, cy - half * 0.42f, paint)
            }

            Kind.CLOSE -> {
                canvas.drawLine(cx - half * 0.42f, cy - half * 0.42f, cx + half * 0.42f, cy + half * 0.42f, paint)
                canvas.drawLine(cx + half * 0.42f, cy - half * 0.42f, cx - half * 0.42f, cy + half * 0.42f, paint)
            }

            Kind.GEAR -> {
                canvas.drawCircle(cx, cy, size * 0.17f, paint)
                for (i in 0 until 8) {
                    val angle = Math.toRadians(i * 45.0)
                    val cos = kotlin.math.cos(angle).toFloat()
                    val sin = kotlin.math.sin(angle).toFloat()
                    canvas.drawLine(
                        cx + cos * half * 0.34f,
                        cy + sin * half * 0.34f,
                        cx + cos * half * 0.58f,
                        cy + sin * half * 0.58f,
                        paint,
                    )
                }
            }

            Kind.BOOK -> {
                canvas.drawLine(cx, cy - half * 0.5f, cx, cy + half * 0.5f, paint)
                val leftPage = Path()
                leftPage.moveTo(cx, cy - half * 0.5f)
                leftPage.lineTo(left + size * 0.06f, cy - half * 0.38f)
                leftPage.lineTo(left + size * 0.06f, cy + half * 0.46f)
                leftPage.lineTo(cx, cy + half * 0.56f)
                canvas.drawPath(leftPage, paint)
                val rightPage = Path()
                rightPage.moveTo(cx, cy - half * 0.5f)
                rightPage.lineTo(cx + half * 0.9f, cy - half * 0.38f)
                rightPage.lineTo(cx + half * 0.9f, cy + half * 0.46f)
                rightPage.lineTo(cx, cy + half * 0.56f)
                canvas.drawPath(rightPage, paint)
            }

            Kind.FOLDER -> {
                val rect = RectF(left + size * 0.04f, top + size * 0.24f, cx + half * 0.9f, top + size * 0.8f)
                canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, paint)
                canvas.drawLine(
                    rect.left,
                    top + size * 0.24f,
                    rect.left + size * 0.3f,
                    top + size * 0.14f,
                    paint,
                )
                canvas.drawLine(
                    rect.left + size * 0.3f,
                    top + size * 0.14f,
                    rect.left + size * 0.38f,
                    top + size * 0.24f,
                    paint,
                )
            }

            Kind.LIST -> {
                for (i in 0 until 3) {
                    val y = cy - half * 0.45f + i * half * 0.45f
                    canvas.drawCircle(left + half * 0.2f, y, stroke * 0.8f, paint)
                    canvas.drawLine(left + half * 0.45f, y, cx + half * 0.75f, y, paint)
                }
            }

            Kind.VOLUME -> {
                val path = Path()
                path.moveTo(left + size * 0.12f, cy - size * 0.14f)
                path.lineTo(left + size * 0.3f, cy - size * 0.14f)
                path.lineTo(cx, cy - size * 0.4f)
                path.lineTo(cx, cy + size * 0.4f)
                path.lineTo(left + size * 0.3f, cy + size * 0.14f)
                path.lineTo(left + size * 0.12f, cy + size * 0.14f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawArc(
                    RectF(cx - size * 0.04f, cy - size * 0.24f, cx + size * 0.42f, cy + size * 0.24f),
                    -55f,
                    110f,
                    false,
                    paint,
                )
            }

            Kind.LOCK -> {
                val body = RectF(left + size * 0.2f, cy - size * 0.02f, cx + half * 0.6f, top + size * 0.86f)
                canvas.drawRoundRect(body, size * 0.08f, size * 0.08f, paint)
                val shackle = RectF(left + size * 0.3f, top + size * 0.14f, cx + half * 0.5f, cy + size * 0.12f)
                canvas.drawArc(shackle, 180f, 180f, false, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    /**
     * 创建承载单色图标的视图。
     *
     * 参数 kind 为图标种类；尺寸取设计令牌的图标边长；
     * haloColor 非透明时为图标绘制白色外轮廓。
     */
    fun view(
        context: Context,
        kind: Kind,
        sizePx: Int = DesignRuntime.tokens.settingsIcon,
        haloColor: Int = 0,
    ): View = GlyphView(context, kind, sizePx, haloColor)
}

/** 单色图标视图，固定尺寸绘制。 */
private class GlyphView(
    context: Context,
    private val kind: InkGlyphs.Kind,
    private val sizePx: Int,
    private val haloColor: Int,
) : View(context) {
    private val paint = DesignRuntime.fillPaint(InkColors.INK)

    init {
        layoutParams = android.widget.LinearLayout.LayoutParams(sizePx, sizePx)
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        InkGlyphs.draw(
            canvas,
            kind,
            width / 2f,
            height / 2f,
            minOf(width, height) * 0.82f,
            paint,
            haloColor = haloColor,
            haloWidth = if (haloColor != 0) maxOf(2f, minOf(width, height) * 0.1f) else 0f,
        )
    }
}
