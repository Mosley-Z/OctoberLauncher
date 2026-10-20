package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.view.View
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets

/** 排序箭头种类：直达首尾的箭头在三角外侧多画一条横线。 */
internal enum class ArrowKind { TO_TOP, UP, DOWN, TO_BOTTOM }

/**
 * 左栏排序箭头按钮。
 *
 * 使用 Canvas 绘制三角与横线，避免系统字体缺少对应符号；
 * 点击行为由调用方传入，尺寸由外部布局参数决定。
 */
internal class ArrowButtonView(
    context: Context,
    private val kind: ArrowKind,
    onClick: () -> Unit,
) : View(context) {
    /** 线条画笔，宽度取设计令牌的描边宽度。 */
    private val stroke = DesignRuntime.strokePaint()

    /** 三角填充画笔。 */
    private val fill = DesignRuntime.fillPaint(InkColors.INK)

    init {
        val tokens = DesignRuntime.tokens
        background =
            InkWidgets.background(
                tokens.radiusSmall,
                InkColors.PAPER,
                tokens.stroke,
                InkColors.INK,
            )
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    /**
     * 绘制箭头：普通箭头只有三角，首尾箭头在三角外侧补一条横线。
     *
     * @param canvas 目标画布，尺寸取视图当前宽高。
     */
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val size = minOf(width, height).toFloat()
        val half = size * 0.22f
        val barHalf = size * 0.26f
        val barGap = size * 0.10f
        val path = Path()
        when (kind) {
            ArrowKind.UP, ArrowKind.TO_TOP -> {
                val tipY = if (kind == ArrowKind.TO_TOP) cy - half + barGap else cy - half
                path.moveTo(cx, tipY)
                path.lineTo(cx + half, tipY + half * 1.6f)
                path.lineTo(cx - half, tipY + half * 1.6f)
                path.close()
                canvas.drawPath(path, fill)
                if (kind == ArrowKind.TO_TOP) {
                    val barY = cy - half - barGap
                    canvas.drawLine(cx - barHalf, barY, cx + barHalf, barY, stroke)
                }
            }

            ArrowKind.DOWN, ArrowKind.TO_BOTTOM -> {
                val tipY = if (kind == ArrowKind.TO_BOTTOM) cy + half - barGap else cy + half
                path.moveTo(cx, tipY)
                path.lineTo(cx + half, tipY - half * 1.6f)
                path.lineTo(cx - half, tipY - half * 1.6f)
                path.close()
                canvas.drawPath(path, fill)
                if (kind == ArrowKind.TO_BOTTOM) {
                    val barY = cy + half + barGap
                    canvas.drawLine(cx - barHalf, barY, cx + barHalf, barY, stroke)
                }
            }
        }
    }
}

/**
 * 页码切换小箭头：无边框，仅绘制折线，首末页由调用方置灰。
 *
 * @param forward 为真时指向右侧（下一页），否则指向左侧。
 * @param enabled 为假时使用浅灰线条且不响应点击。
 * @param onClick 允许切换时的回调。
 */
internal class PageArrowView(
    context: Context,
    private val forward: Boolean,
    private val enabled: Boolean,
    onClick: () -> Unit,
) : View(context) {
    /** 折线画笔，禁用时换成浅灰色。 */
    private val stroke =
        DesignRuntime.strokePaint(
            if (enabled) InkColors.INK else InkColors.INK_4,
        )

    init {
        isClickable = enabled
        isFocusable = enabled
        if (enabled) setOnClickListener { onClick() }
    }

    /**
     * 绘制尖括号折线。
     *
     * @param canvas 目标画布，尺寸取视图当前宽高。
     */
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val size = minOf(width, height).toFloat()
        val half = size * 0.28f
        val path = Path()
        if (forward) {
            path.moveTo(cx - half * 0.6f, cy - half)
            path.lineTo(cx + half * 0.6f, cy)
            path.lineTo(cx - half * 0.6f, cy + half)
        } else {
            path.moveTo(cx + half * 0.6f, cy - half)
            path.lineTo(cx - half * 0.6f, cy)
            path.lineTo(cx + half * 0.6f, cy + half)
        }
        canvas.drawPath(path, stroke)
    }
}
