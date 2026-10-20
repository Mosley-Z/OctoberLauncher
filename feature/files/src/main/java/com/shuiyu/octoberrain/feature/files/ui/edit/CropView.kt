package com.shuiyu.octoberrain.feature.files.ui.edit

import android.graphics.Canvas
import android.graphics.Paint
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.feature.files.ui.FileTokens

/**
 * 裁剪框绘制。
 *
 * 在预览图上叠加暗区、边框、三分线与四角手柄。绘制只读取传入矩形，不持有触摸状态；
 * 命中判定由 [CropHitTest] 负责，拖动计算由 [CropDrag] 负责。
 *
 * 参数 tokens 提供描边宽度、内边距与触摸尺寸。
 */
internal class CropView(
    private val tokens: FileTokens,
) {
    private val dimPaint =
        Paint().apply {
            color = InkColors.INK
            alpha = DIM_ALPHA
            style = Paint.Style.FILL
        }
    private val borderPaint =
        Paint().apply {
            color = InkColors.INK
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
        }
    private val borderHaloPaint =
        Paint().apply {
            color = InkColors.PAPER
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat() * 3f
        }
    private val gridPaint =
        Paint().apply {
            color = InkColors.INK
            alpha = GRID_ALPHA
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
        }
    private val gridHaloPaint =
        Paint().apply {
            color = InkColors.PAPER
            alpha = HALO_ALPHA
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat() * 2f
        }
    private val handlePaint =
        Paint().apply {
            color = InkColors.PAPER
            style = Paint.Style.FILL
        }
    private val handleBorderPaint =
        Paint().apply {
            color = InkColors.INK
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
        }
    private val hintPaint =
        Paint().apply {
            color = InkColors.INK_3
            textSize = tokens.smallText.toFloat()
        }

    /** 手柄命中半径，取触摸下限的一半，保证四角可点中。 */
    val handleRadius: Int get() = tokens.touchMin / 2

    /**
     * 绘制裁剪框。
     *
     * 参数 canvas 为画布；area 为图片绘制区域；rect 为归一化裁剪框；frame 为换算后的像素框。
     * 框外区域填充半透明暗色，框内绘制三分线与四角短边。
     */
    fun draw(
        canvas: Canvas,
        area: IntRect,
        rect: NormRect,
        frame: IntRect,
    ) {
        drawDim(canvas, area, frame)
        drawThirds(canvas, frame)
        canvas.drawRect(
            frame.left.toFloat(),
            frame.top.toFloat(),
            frame.right.toFloat(),
            frame.bottom.toFloat(),
            borderHaloPaint,
        )
        canvas.drawRect(
            frame.left.toFloat(),
            frame.top.toFloat(),
            frame.right.toFloat(),
            frame.bottom.toFloat(),
            borderPaint,
        )
        drawHandles(canvas, frame)
        drawAspectHint(canvas, area, rect)
    }

    /** 在裁剪框四周填充暗色，保留框内原图亮度。 */
    private fun drawDim(
        canvas: Canvas,
        area: IntRect,
        frame: IntRect,
    ) {
        canvas.drawRect(area.left.toFloat(), area.top.toFloat(), area.right.toFloat(), frame.top.toFloat(), dimPaint)
        canvas.drawRect(
            area.left.toFloat(),
            frame.bottom.toFloat(),
            area.right.toFloat(),
            area.bottom.toFloat(),
            dimPaint,
        )
        canvas.drawRect(
            area.left.toFloat(),
            frame.top.toFloat(),
            frame.left.toFloat(),
            frame.bottom.toFloat(),
            dimPaint,
        )
        canvas.drawRect(
            frame.right.toFloat(),
            frame.top.toFloat(),
            area.right.toFloat(),
            frame.bottom.toFloat(),
            dimPaint,
        )
    }

    /** 绘制三分线。 */
    private fun drawThirds(
        canvas: Canvas,
        frame: IntRect,
    ) {
        val thirdWidth = frame.width / 3f
        val thirdHeight = frame.height / 3f
        for (index in 1..2) {
            val x = frame.left + thirdWidth * index
            canvas.drawLine(x, frame.top.toFloat(), x, frame.bottom.toFloat(), gridHaloPaint)
            canvas.drawLine(x, frame.top.toFloat(), x, frame.bottom.toFloat(), gridPaint)
            val y = frame.top + thirdHeight * index
            canvas.drawLine(frame.left.toFloat(), y, frame.right.toFloat(), y, gridHaloPaint)
            canvas.drawLine(frame.left.toFloat(), y, frame.right.toFloat(), y, gridPaint)
        }
    }

    /** 在四角绘制方形手柄。 */
    private fun drawHandles(
        canvas: Canvas,
        frame: IntRect,
    ) {
        val size = (tokens.touchMin / 3).coerceAtLeast(8)
        drawHandle(canvas, frame.left, frame.top, size)
        drawHandle(canvas, frame.right, frame.top, size)
        drawHandle(canvas, frame.left, frame.bottom, size)
        drawHandle(canvas, frame.right, frame.bottom, size)
    }

    /** 绘制单个手柄，位置向框内偏移一半，避免被裁切。 */
    private fun drawHandle(
        canvas: Canvas,
        x: Int,
        y: Int,
        size: Int,
    ) {
        val left = (x - size / 2).toFloat()
        val top = (y - size / 2).toFloat()
        val right = (x + size / 2).toFloat()
        val bottom = (y + size / 2).toFloat()
        canvas.drawRect(left, top, right, bottom, handlePaint)
        canvas.drawRect(left, top, right, bottom, handleBorderPaint)
    }

    /** 在裁剪框上方绘制百分比提示。 */
    private fun drawAspectHint(
        canvas: Canvas,
        area: IntRect,
        rect: NormRect,
    ) {
        if (rect.width <= 0f || rect.height <= 0f) return
        val text = "${(rect.width * 100).toInt()}% × ${(rect.height * 100).toInt()}%"
        canvas.drawText(text, area.left.toFloat() + tokens.padH, hintPaint.textSize, hintPaint)
    }

    private companion object {
        /** 框外暗区透明度。 */
        const val DIM_ALPHA = 72

        /** 三分线透明度。 */
        const val GRID_ALPHA = 96

        /** 衬线底色透明度。 */
        const val HALO_ALPHA = 168
    }
}
