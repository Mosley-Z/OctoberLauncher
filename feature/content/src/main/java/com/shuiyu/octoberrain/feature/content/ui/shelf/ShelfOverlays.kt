package com.shuiyu.octoberrain.feature.content.ui.shelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors

/** 遮罩底色的不透明度，取值对应八位 ARGB 的 alpha 通道。 */
private const val SCRIM_ALPHA = 0x9E

/** 进度百分比的上限，同时用于截断越界数据。 */
private const val PERCENT_MAX = 100

/** 进度条高度。 */
private const val BAR_DP = 4f

/** 进度层左右留白。 */
private const val INSET_DP = 10f

/** 状态图标边长。 */
private const val GLYPH_DP = 12f

/** 进度条与状态图标之间的留白。 */
private const val GLYPH_GAP_DP = 8f

/** 状态图标的描边宽度。 */
private const val GLYPH_STROKE_DP = 1.4f

/** 进度条外框的描边宽度。 */
private const val BORDER_DP = 0.8f

/** 沙漏腰部相对半高的比例。 */
private const val HOURGLASS_WAIST = 0.8f

/** 角标宽度、高度与底部尖角高度。 */
private const val BADGE_WIDTH_DP = 42f
private const val BADGE_HEIGHT_DP = 30f
private const val BADGE_TIP_DP = 6f

/**
 * 底部阅读进度覆盖层。
 *
 * 覆盖层贴住卡片底边，自上而下由半透明遮罩、细长圆角进度条与右下角沙漏图标
 * 构成。遮罩上缘平直、下缘取卡片圆角；已读段与未读段沿用反白块的配色对，
 * 外框单独描边，保证在任意封面底色上均可分辨。
 *
 * @param percent 阅读进度百分比，取值 0 到 100
 * @param cornerRadiusPx 卡片圆角像素，用于遮罩下缘
 */
internal class ShelfProgressOverlay(
    context: Context,
    percent: Int,
    private val cornerRadiusPx: Int,
) : View(context) {
    private val ratio: Float = percent.coerceIn(0, PERCENT_MAX) / PERCENT_MAX.toFloat()
    private val scrimPaint: Paint = DesignRuntime.fillPaint(scrimColor())
    private val trackPaint: Paint = DesignRuntime.fillPaint(InkColors.INVERT_INK)
    private val fillPaint: Paint = DesignRuntime.fillPaint(InkColors.INVERT_PAPER)
    private val borderPaint: Paint =
        DesignRuntime.strokePaint(InkColors.INVERT_PAPER, maxOf(1, DesignRuntime.tokens.dp(BORDER_DP)))
    private val glyphStrokeWidth: Int = maxOf(1, DesignRuntime.tokens.dp(GLYPH_STROKE_DP))
    private val glyphPaint: Paint =
        DesignRuntime.strokePaint(InkColors.INVERT_INK, glyphStrokeWidth)
    private val scrimPath = Path()
    private val scrimRect = RectF()
    private val barRect = RectF()

    init {
        glyphPaint.strokeCap = Paint.Cap.ROUND
        glyphPaint.strokeJoin = Paint.Join.ROUND
    }

    /**
     * 绘制遮罩、进度条、外框与状态图标。
     *
     * @param canvas 目标画布
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthPx = width.toFloat()
        val heightPx = height.toFloat()
        if (widthPx <= 0f || heightPx <= 0f) return
        drawScrim(canvas, widthPx, heightPx)
        val tokens = DesignRuntime.tokens
        val inset = tokens.dpF(INSET_DP)
        val glyphSize = tokens.dpF(GLYPH_DP)
        val barHeight = tokens.dpF(BAR_DP)
        val glyphLeft = widthPx - inset - glyphSize
        val barTop = (heightPx - barHeight) / 2f
        barRect.set(inset, barTop, glyphLeft - tokens.dpF(GLYPH_GAP_DP), barTop + barHeight)
        val radius = barHeight / 2f
        canvas.drawRoundRect(barRect, radius, radius, trackPaint)
        drawFilled(canvas, radius)
        canvas.drawRoundRect(barRect, radius, radius, borderPaint)
        drawHourglass(canvas, glyphLeft + glyphSize / 2f, heightPx / 2f, glyphSize)
    }

    /**
     * 绘制底部遮罩：上缘平直，下缘使用与卡片一致的圆角。
     *
     * @param canvas 目标画布
     * @param widthPx 覆盖层宽度
     * @param heightPx 覆盖层高度
     */
    private fun drawScrim(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
    ) {
        val radius = cornerRadiusPx.toFloat()
        val radii =
            floatArrayOf(
                0f,
                0f,
                0f,
                0f,
                radius,
                radius,
                radius,
                radius,
            )
        scrimRect.set(0f, 0f, widthPx, heightPx)
        scrimPath.reset()
        scrimPath.addRoundRect(scrimRect, radii, Path.Direction.CW)
        canvas.drawPath(scrimPath, scrimPaint)
    }

    /**
     * 绘制已读段：按比例裁剪后绘制同一圆角矩形，左端保持圆角。
     *
     * @param canvas 目标画布
     * @param radius 进度条圆角
     */
    private fun drawFilled(
        canvas: Canvas,
        radius: Float,
    ) {
        if (ratio <= 0f) return
        val filledWidth = barRect.width() * ratio
        val save = canvas.save()
        canvas.clipRect(barRect.left, barRect.top, barRect.left + filledWidth, barRect.bottom)
        canvas.drawRoundRect(barRect, radius, radius, fillPaint)
        canvas.restoreToCount(save)
    }

    /**
     * 绘制线性沙漏图标，用于标示阅读进度状态。
     *
     * @param canvas 目标画布
     * @param centerX 图标中心横坐标
     * @param centerY 图标中心纵坐标
     * @param size 图标边长
     */
    private fun drawHourglass(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float,
    ) {
        val half = size / 2f
        val waist = half * HOURGLASS_WAIST
        val left = centerX - waist
        val right = centerX + waist
        val top = centerY - half
        val bottom = centerY + half
        canvas.drawLine(left, top, right, top, glyphPaint)
        canvas.drawLine(left, bottom, right, bottom, glyphPaint)
        canvas.drawLine(left, top, centerX, centerY, glyphPaint)
        canvas.drawLine(right, top, centerX, centerY, glyphPaint)
        canvas.drawLine(left, bottom, centerX, centerY, glyphPaint)
        canvas.drawLine(right, bottom, centerX, centerY, glyphPaint)
    }
}

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

/**
 * 由反白块底色派生半透明遮罩色。
 *
 * 遮罩跟随主题的反白配色：浅色主题下为深色遮罩，深色主题下为浅色遮罩。
 *
 * @return 带 alpha 通道的遮罩颜色
 */
private fun scrimColor(): Int {
    val base = InkColors.INVERT_PAPER
    return Color.argb(SCRIM_ALPHA, Color.red(base), Color.green(base), Color.blue(base))
}
