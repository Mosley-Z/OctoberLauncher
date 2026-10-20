package com.shuiyu.octoberrain.core.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * 墨水屏绘制工具：直角边框、胶囊、描边与反色。
 *
 * 上游以固定半径的类名封装同样能力，这里只保留参数化函数，
 * 半径与线宽由尺寸令牌传入，避免在类内写死像素。
 */
object InkCanvas {
    /**
     * 绘制直角矩形边框或填充。
     *
     * 参数 canvas 为目标画布；rect 为区域；paint 已设置颜色与线宽；
     * filled 为 true 时填充，否则描边。
     */
    fun drawRect(
        canvas: Canvas,
        rect: RectF,
        paint: Paint,
        filled: Boolean,
    ) {
        val mode = if (filled) Paint.Style.FILL else Paint.Style.STROKE
        paint.style = mode
        canvas.drawRect(rect, paint)
    }

    /**
     * 绘制圆角矩形。
     *
     * 参数 radius 为圆角半径，墨水屏上应取 4dp 以内，过大圆角在抖动下会发虚。
     */
    fun drawRoundRect(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        paint: Paint,
        filled: Boolean,
    ) {
        paint.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    /**
     * 绘制胶囊形，半径取矩形高度的一半。
     *
     * 用于状态标签一类的短元素。
     */
    fun drawCapsule(
        canvas: Canvas,
        rect: RectF,
        paint: Paint,
        filled: Boolean,
    ) {
        drawRoundRect(canvas, rect, rect.height() / 2f, paint, filled)
    }

    /**
     * 绘制水平分隔线。
     *
     * 参数 y 为线的纵坐标；left 与 right 为起止横坐标。
     */
    fun drawHairline(
        canvas: Canvas,
        left: Float,
        right: Float,
        y: Float,
        paint: Paint,
    ) {
        paint.style = Paint.Style.FILL
        canvas.drawRect(left, y, right, y + maxOf(1f, paint.strokeWidth), paint)
    }

    /**
     * 反色填充：先铺底色再以纸白绘制文字，用于选中态。
     *
     * 参数 rect 为反色区域；radius 为圆角半径。
     */
    fun fillInverted(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        paint: Paint,
    ) {
        drawRoundRect(canvas, rect, radius, paint, filled = true)
    }
}
