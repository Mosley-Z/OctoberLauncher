package com.shuiyu.octoberrain.feature.content.ui.shelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors

/** 进度百分比的上限。 */
private const val PERCENT_MAX = 100
private const val BADGE_WIDTH_DP = 42f
private const val BADGE_HEIGHT_DP = 30f
private const val BADGE_TIP_DP = 6f

/**
 * 右上角阅读进度角标。
 *
 * 角标贴住卡片右上角，主体为反白块底色的矩形，底边中部向下伸出尖角，
 * 百分比文字加粗并水平居中。
 *
 * @param percent 阅读进度百分比，取值 0 到 100
 */
internal class ShelfProgressBadge(
    context: Context,
    percent: Int,
) : View(context) {
    private val label: String = "${percent.coerceIn(0, PERCENT_MAX)}%"
    private val bodyPaint: Paint = DesignRuntime.fillPaint(InkColors.INVERT_PAPER)
    private val labelPaint: Paint =
        DesignRuntime.textPaint(
            size = DesignRuntime.tokens.textCaption,
            color = InkColors.INVERT_INK,
            typeface = Typeface.create(DesignRuntime.typeface(), Typeface.BOLD),
        )
    private val badgePath = Path()

    init {
        labelPaint.textAlign = Paint.Align.CENTER
    }

    /**
     * 按固定尺寸测量，宽度为百分比文字预留空间。
     *
     * @param widthMeasureSpec 父容器给出的宽度测量规格
     * @param heightMeasureSpec 父容器给出的高度测量规格
     */
    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(
            DesignRuntime.tokens.dp(BADGE_WIDTH_DP),
            DesignRuntime.tokens.dp(BADGE_HEIGHT_DP),
        )
    }

    /**
     * 绘制角标主体与百分比文字。
     *
     * @param canvas 目标画布
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthPx = width.toFloat()
        val heightPx = height.toFloat()
        if (widthPx <= 0f || heightPx <= 0f) return
        val tip = DesignRuntime.tokens.dpF(BADGE_TIP_DP)
        badgePath.reset()
        badgePath.moveTo(0f, 0f)
        badgePath.lineTo(widthPx, 0f)
        badgePath.lineTo(widthPx, heightPx - tip)
        badgePath.lineTo(widthPx / 2f, heightPx)
        badgePath.lineTo(0f, heightPx - tip)
        badgePath.close()
        canvas.drawPath(badgePath, bodyPaint)
        val baseline = (heightPx - tip) / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2f
        canvas.drawText(label, widthPx / 2f, baseline, labelPaint)
    }
}
