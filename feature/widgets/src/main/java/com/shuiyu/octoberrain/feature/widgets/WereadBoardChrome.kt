package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import kotlin.math.min

/**
 * 底部按钮的几何。
 *
 * @property openButton 打开按钮区域；由内核整卡绘制的形态自带打开入口，此处为空
 * @property layoutButton 形态切换按钮区域
 * @property refreshButton 刷新按钮区域
 */
internal data class WereadBoardChrome(
    val openButton: BoardBounds?,
    val layoutButton: BoardBounds,
    val refreshButton: BoardBounds,
)

/**
 * 右下角两个动作按钮的几何与排版参数。
 *
 * @property layoutButton 形态切换按钮区域
 * @property refreshButton 刷新按钮区域
 * @property height 按钮高度
 * @property textSize 按钮字号
 * @property padding 按钮文字与边框之间的水平内边距
 */
internal data class WereadBoardActions(
    val layoutButton: BoardBounds,
    val refreshButton: BoardBounds,
    val height: Float,
    val textSize: Float,
    val padding: Float,
)

/**
 * 推导右下角两个动作按钮的几何。
 *
 * 按钮自右向左依次为刷新与形态切换，高度与打开按钮一致；内部宽度不足时整体向左收拢。
 *
 * @param paint 复用的画笔，用于量取按钮文字宽度
 * @param frame 版式参数
 * @param width 组件宽度，单位像素
 * @return 两个按钮的几何
 */
internal fun actionsOf(
    paint: Paint,
    frame: WereadBoardFrame,
    width: Float,
): WereadBoardActions {
    val height = (frame.infoSize * BUTTON_HEIGHT_RATIO).coerceAtLeast(MIN_BUTTON_HEIGHT)
    val textSize = (frame.infoSize * BUTTON_TEXT_RATIO).coerceAtLeast(MIN_BUTTON_TEXT)
    paint.typeface = DesignRuntime.typeface()
    paint.textSize = textSize
    val padding = buttonPaddingOf(paint, frame, width)
    val layoutWidth = paint.measureText(LAYOUT_LABEL) + padding * 2f
    val refreshWidth = paint.measureText(REFRESH_LABEL) + padding * 2f
    val buttons =
        footerButtonsOf(
            width = width,
            inset = frame.inset,
            infoBottom = frame.infoBottom,
            height = height,
            layoutWidth = layoutWidth,
            refreshWidth = refreshWidth,
            gap = frame.infoSize * BUTTON_GAP_RATIO,
        )
    return WereadBoardActions(buttons.layoutButton, buttons.refreshButton, height, textSize, padding)
}

/**
 * 计算按钮内边距。
 *
 * 底部一行变宽后可能挤占信息文字，宽度不足时先压缩内边距，按钮文字按实测宽度保留，
 * 避免裁切。
 *
 * @param paint 复用的画笔，调用前字号已设为按钮字号
 * @param frame 版式参数
 * @param width 组件宽度，单位像素
 * @return 实际使用的水平内边距
 */
private fun buttonPaddingOf(
    paint: Paint,
    frame: WereadBoardFrame,
    width: Float,
): Float {
    val base = frame.infoSize * BUTTON_PADDING_RATIO
    val textWidth = paint.measureText(LAYOUT_LABEL) + paint.measureText(REFRESH_LABEL)
    val available = (width - frame.inset * 2f) * BUTTON_BLOCK_RATIO
    val fitted = (available - textWidth - frame.infoSize * BUTTON_GAP_RATIO) / BUTTON_PADDING_DIVISOR
    return min(base, fitted.coerceAtLeast(base * MIN_BUTTON_PADDING_RATIO))
}

/**
 * 绘制右下角两个动作按钮。
 *
 * @param canvas 目标画布
 * @param paint 复用的画笔，绘制期间会改写样式与颜色
 * @param actions 按钮几何
 */
internal fun drawActions(
    canvas: Canvas,
    paint: Paint,
    actions: WereadBoardActions,
) {
    drawChromeButton(canvas, paint, actions.layoutButton, LAYOUT_LABEL, actions.textSize)
    drawChromeButton(canvas, paint, actions.refreshButton, REFRESH_LABEL, actions.textSize)
}

/**
 * 绘制白底墨框的文字按钮。
 *
 * 按钮共用同一外观：接近直角的细边框、白底、黑色文字，文字水平与垂直居中。
 *
 * @param canvas 目标画布
 * @param paint 复用的画笔，绘制期间会改写样式与颜色
 * @param bounds 按钮区域
 * @param label 按钮文案
 * @param textSize 按钮字号
 */
internal fun drawChromeButton(
    canvas: Canvas,
    paint: Paint,
    bounds: BoardBounds,
    label: String,
    textSize: Float,
) {
    val rect = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
    paint.style = Paint.Style.FILL
    paint.color = InkColors.PAPER
    canvas.drawRect(rect, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = BUTTON_STROKE_WIDTH
    paint.color = InkColors.INK
    canvas.drawRect(rect, paint)
    paint.style = Paint.Style.FILL
    paint.color = InkColors.INK
    paint.textSize = textSize
    paint.textAlign = Paint.Align.CENTER
    val baseline = bounds.centerY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    canvas.drawText(label, bounds.centerX, baseline, paint)
    paint.textAlign = Paint.Align.LEFT
}

/** 按钮边框线宽，取细线的两倍以在浅色背景上保持清晰。 */
private const val BUTTON_STROKE_WIDTH: Float = 1.6f

/** 按钮高度相对信息字号的比例。 */
private const val BUTTON_HEIGHT_RATIO: Float = 1.90f

/** 按钮最小高度。 */
private const val MIN_BUTTON_HEIGHT: Float = 20f

/** 按钮字号相对信息字号的比例。 */
private const val BUTTON_TEXT_RATIO: Float = 0.92f

/** 按钮最小字号。 */
private const val MIN_BUTTON_TEXT: Float = 7f

/** 按钮内边距相对信息字号的比例。 */
private const val BUTTON_PADDING_RATIO: Float = 0.90f

/** 相邻按钮之间的间距相对信息字号的比例。 */
private const val BUTTON_GAP_RATIO: Float = 0.70f

/** 形态切换按钮文案。 */
private const val LAYOUT_LABEL: String = "切换"

/** 刷新按钮文案。 */
private const val REFRESH_LABEL: String = "刷新"

/** 两个按钮在底部一行中最多占用的宽度比例。 */
private const val BUTTON_BLOCK_RATIO: Float = 0.42f

/** 内边距最多压缩到基准值的这个比例。 */
private const val MIN_BUTTON_PADDING_RATIO: Float = 0.55f

/** 内边距压缩时两侧合计占用的份数。 */
private const val BUTTON_PADDING_DIVISOR: Float = 4f
