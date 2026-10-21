package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.widget.WidgetHitRegion
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 隐藏菜单的一行：绘制矩形、动作与文案。
 *
 * @property rect 组件内像素矩形
 * @property action 行对应的动作标识
 * @property title 行文案
 */
internal data class WereadBoardOverlayRow(
    val rect: BoardBounds,
    val action: String,
    val title: String,
) {
    /**
     * 把该行换算为命中区域。
     *
     * @return 组件内像素坐标系的命中区域
     */
    fun toRegion(): WidgetHitRegion =
        WidgetHitRegion(
            rect.left.roundToInt(),
            rect.top.roundToInt(),
            (rect.right - rect.left).roundToInt().coerceAtLeast(1),
            (rect.bottom - rect.top).roundToInt().coerceAtLeast(1),
            action,
        )
}

/**
 * 长按抬头的隐藏菜单与「隐藏若干秒」状态。
 *
 * 菜单在八秒无操作后自动关闭；隐藏到期后自动恢复并请求重绘。
 * 组件只负责把点击与绘制委托给本类，状态全部留在实例内部，多实例互不影响。
 *
 * @param onChanged 需要重绘时调用
 */
internal class WereadBoardOverlay(
    private val onChanged: () -> Unit,
) {
    /** 主线程消息队列，用于菜单超时与隐藏恢复。 */
    private val handler = Handler(Looper.getMainLooper())

    /** 菜单是否可见。 */
    private var menuVisible: Boolean = false

    /** 隐藏截止时刻，取值自单调时钟；为零表示当前不隐藏。 */
    private var hiddenUntil: Long = 0L

    /** 菜单超时关闭任务。 */
    private val menuTimeout: Runnable = Runnable { dismiss() }

    /** 隐藏到期恢复任务。 */
    private val revealTask: Runnable =
        Runnable {
            hiddenUntil = 0L
            onChanged()
        }

    /** 菜单当前是否可见。 */
    val visible: Boolean
        get() = menuVisible

    /** 组件当前是否处于隐藏状态。 */
    fun isHidden(): Boolean = SystemClock.elapsedRealtime() < hiddenUntil

    /** 打开菜单并重新计时。 */
    fun show() {
        menuVisible = true
        handler.removeCallbacks(menuTimeout)
        handler.postDelayed(menuTimeout, OVERLAY_TIMEOUT_MS)
        onChanged()
    }

    /** 关闭菜单。 */
    fun dismiss() {
        menuVisible = false
        handler.removeCallbacks(menuTimeout)
        onChanged()
    }

    /**
     * 处理菜单点击：命中行执行隐藏，空白处关闭菜单。
     *
     * @param rows 当前行
     * @param x 组件内横坐标
     * @param y 组件内纵坐标
     */
    fun click(
        rows: List<WereadBoardOverlayRow>,
        x: Float,
        y: Float,
    ) {
        val row = rows.firstOrNull { it.rect.contains(x, y) }
        if (row == null) {
            dismiss()
        } else {
            hideFor(row.action.removePrefix(ACTION_HIDE_PREFIX).toIntOrNull() ?: 0)
        }
    }

    /**
     * 隐藏组件指定时长，到时自动恢复。
     *
     * @param seconds 隐藏秒数，零表示取消
     */
    fun hideFor(seconds: Int) {
        dismiss()
        if (seconds <= 0) return
        val millis = seconds * MILLIS_PER_SECOND
        hiddenUntil = SystemClock.elapsedRealtime() + millis
        handler.removeCallbacks(revealTask)
        handler.postDelayed(revealTask, millis)
        onChanged()
    }

    /**
     * 计算菜单行的像素矩形，贴组件右上角排列。
     *
     * @param width 组件宽度，单位像素
     * @param height 组件高度，单位像素
     * @param scale 画布缩放系数
     * @return 行矩形与文案
     */
    fun rows(
        width: Int,
        height: Int,
        scale: Float,
    ): List<WereadBoardOverlayRow> {
        val logicalWidth = if (scale <= 0f || width <= 0 || height <= 0) 0f else width / scale
        val available = logicalWidth - OVERLAY_MARGIN * 2f
        if (available <= 0f) return emptyList()
        val rowWidth = min(OVERLAY_WIDTH, available).coerceAtLeast(min(OVERLAY_MIN_WIDTH, available))
        val left = logicalWidth - rowWidth - OVERLAY_MARGIN
        return HIDE_OPTIONS.mapIndexed { index, option ->
            val top = OVERLAY_MARGIN + index * OVERLAY_ROW_HEIGHT
            WereadBoardOverlayRow(
                BoardBounds(
                    left * scale,
                    top * scale,
                    (left + rowWidth) * scale,
                    (top + OVERLAY_ROW_HEIGHT) * scale,
                ),
                "$ACTION_HIDE_PREFIX${option.first}",
                option.second,
            )
        }
    }

    /**
     * 绘制菜单：白底、墨框与行分隔线，文字在行内垂直居中。
     *
     * @param canvas 目标画布
     * @param rows 菜单行
     * @param scale 画布缩放系数
     */
    fun draw(
        canvas: Canvas,
        rows: List<WereadBoardOverlayRow>,
        scale: Float,
    ) {
        if (rows.isEmpty() || scale <= 0f) return
        val fill = DesignRuntime.fillPaint(InkColors.PAPER)
        val stroke = DesignRuntime.strokePaint(InkColors.INK)
        val textSize = OVERLAY_TEXT_SIZE * scale
        val text = DesignRuntime.textPaint(textSize, InkColors.INK)
        val first = rows.first().rect
        val last = rows.last().rect
        val outer = BoardBounds(first.left, first.top, last.right, last.bottom)
        canvas.drawRect(outer.left, outer.top, outer.right, outer.bottom, fill)
        canvas.drawRect(outer.left, outer.top, outer.right, outer.bottom, stroke)
        val padding = OVERLAY_TEXT_PADDING * scale
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                val dividerY = row.rect.top
                canvas.drawLine(outer.left, dividerY, outer.right, dividerY, stroke)
            }
            val title = InkDraw.ellipsize(row.title, text, row.rect.width - padding * 2f)
            val textTop = row.rect.top + (row.rect.height - textSize) / 2f
            InkDraw.text(canvas, title, row.rect.left + padding, textTop, text)
        }
    }

    /** 释放消息队列中的回调。 */
    fun release() {
        handler.removeCallbacksAndMessages(null)
    }

    /** 菜单与隐藏相关的常量。 */
    companion object {
        /** 菜单超时关闭时间，单位毫秒。 */
        const val OVERLAY_TIMEOUT_MS: Long = 8000L

        /** 秒与毫秒的换算。 */
        const val MILLIS_PER_SECOND: Long = 1000L

        /** 菜单宽度上限，单位逻辑像素。 */
        const val OVERLAY_WIDTH: Float = 220f

        /** 菜单宽度下限，单位逻辑像素。 */
        const val OVERLAY_MIN_WIDTH: Float = 140f

        /** 菜单与卡片边缘的间距，单位逻辑像素。 */
        const val OVERLAY_MARGIN: Float = 8f

        /** 菜单行高，单位逻辑像素。 */
        const val OVERLAY_ROW_HEIGHT: Float = 32f

        /** 菜单文字尺寸，单位逻辑像素。 */
        const val OVERLAY_TEXT_SIZE: Float = 14f

        /** 菜单文字左内边距，单位逻辑像素。 */
        const val OVERLAY_TEXT_PADDING: Float = 10f

        /** 动作前缀：隐藏。 */
        const val ACTION_HIDE_PREFIX: String = "hide:"

        /** 隐藏选项：秒数与文案。 */
        val HIDE_OPTIONS: List<Pair<Int, String>> =
            listOf(
                15 to "隐藏 15 秒",
                60 to "隐藏 1 分钟",
                300 to "隐藏 5 分钟",
                0 to "取消",
            )
    }
}
