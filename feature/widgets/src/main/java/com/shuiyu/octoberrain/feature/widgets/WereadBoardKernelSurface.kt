package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.roundToInt

/**
 * 内核整卡内容的绘制。
 *
 * 纸色为素白时直接绘制到目标画布；纸色被改写时先绘制到同尺寸位图，
 * 再以正片叠底把位图映射到纸色，保持内核文本与线条的明度关系。
 * 绘制过程按组件宽度换算缩放，内核始终按 368 逻辑宽度的坐标系排版。
 */
internal class WereadBoardKernelSurface {
    /** 纸色绘制时复用的同尺寸位图，尺寸变化时重建。 */
    private var paperBitmap: Bitmap? = null

    /** 纸色叠加画笔，以正片叠底把内核白底映射为纸色。 */
    private val paperPaint: Paint =
        Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }

    /**
     * 绘制内核整卡内容。
     *
     * @param canvas 目标画布
     * @param kernel 已绑定的内核
     * @param formId 形态标识
     * @param width 组件宽度，单位像素
     * @param height 组件高度，单位像素
     * @param scale 画布缩放系数，必须为正数
     * @param paperArgb 目标纸色
     * @return 是否绘制成功
     */
    fun draw(
        canvas: Canvas,
        kernel: WereadBoardKernel,
        formId: String,
        width: Int,
        height: Int,
        scale: Float,
        paperArgb: Int,
    ): Boolean {
        if (scale <= 0f) return false
        val bitmap = if (paperArgb == PAPER_WHITE) null else obtainBitmap(width, height)
        bitmap?.eraseColor(PAPER_WHITE)
        val target = bitmap?.let(::Canvas) ?: canvas
        val rendered = renderInto(target, kernel, formId, width, height, scale)
        if (rendered && bitmap != null) {
            canvas.drawColor(paperArgb)
            canvas.drawBitmap(bitmap, 0f, 0f, paperPaint)
        }
        return rendered
    }

    /**
     * 取得与组件同尺寸的复用位图。
     *
     * @param width 组件宽度
     * @param height 组件高度
     * @return 位图，尺寸非法时为空
     */
    private fun obtainBitmap(
        width: Int,
        height: Int,
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val current = paperBitmap
        return if (current != null && current.width == width && current.height == height) {
            current
        } else {
            current?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { paperBitmap = it }
        }
    }

    /**
     * 按缩放绘制内核内容。
     *
     * @param canvas 目标画布
     * @param kernel 已绑定内核
     * @param formId 形态标识
     * @param width 组件宽度
     * @param height 组件高度
     * @param scale 画布缩放系数
     * @return 是否绘制成功
     */
    private fun renderInto(
        canvas: Canvas,
        kernel: WereadBoardKernel,
        formId: String,
        width: Int,
        height: Int,
        scale: Float,
    ): Boolean {
        val logicalWidth = (width / scale).roundToInt().coerceAtLeast(1)
        val logicalHeight = (height / scale).roundToInt().coerceAtLeast(1)
        canvas.save()
        canvas.scale(scale, scale)
        val rendered = runCatching { kernel.render(canvas, logicalWidth, logicalHeight, formId) }.isSuccess
        canvas.restore()
        return rendered
    }

    /** 释放位图。 */
    fun release() {
        paperBitmap?.recycle()
        paperBitmap = null
    }

    /** 素白纸色，直接绘制内核内容，不做叠加。 */
    companion object {
        /** 素白纸色。 */
        const val PAPER_WHITE: Int = 0xFFFFFFFF.toInt()
    }
}
