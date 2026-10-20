package com.shuiyu.octoberrain.feature.files.ui

import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 触摸状态机。
 *
 * 负责按下计时、滑动取消长按与抬起分发，把命中判断交给 [FilePagerChrome]，
 * 把页面动作与内容回调交给 [Handler]，使 [FilePager] 只保留视图职责。
 * 顶栏返回按钮与动作按钮优先于底栏与内容区分发。
 *
 * 参数 view 用于投递长按任务；tokens 提供触摸容差；chrome 提供命中矩形；
 * handler 接收返回、动作、点击、长按与翻页事件。
 */
internal class FilePagerTouch(
    private val view: View,
    private val tokens: FileTokens,
    private val chrome: FilePagerChrome,
    private val handler: Handler,
) {
    /** 触摸事件接收方。 */
    interface Handler {
        /** 点击顶栏返回按钮。 */
        fun onBack()

        /** 点击顶栏右侧动作按钮。 */
        fun onTopAction()

        /** 点击内容区。 */
        fun onTap(
            x: Int,
            y: Int,
        )

        /** 长按内容区。 */
        fun onLongPress(
            x: Int,
            y: Int,
        )

        /** 翻到上一页。 */
        fun onPrevious()

        /** 翻到下一页。 */
        fun onNext()

        /** 触发底栏动作按钮。 */
        fun onAction()
    }

    private var downX = 0
    private var downY = 0
    private var tracking = false
    private var longFired = false

    private val longPressTask =
        Runnable {
            if (tracking && !longFired) {
                longFired = true
                handler.onLongPress(downX, downY)
                view.invalidate()
            }
        }

    /** 处理一次触摸事件，返回是否消费。 */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(x, y)
            MotionEvent.ACTION_MOVE -> onMove(x, y)
            MotionEvent.ACTION_CANCEL -> reset()
            MotionEvent.ACTION_UP -> {
                reset()
                handleUp(x, y)
            }
        }
        return true
    }

    /** 记录按下位置并开始长按计时。 */
    private fun onDown(
        x: Int,
        y: Int,
    ) {
        downX = x
        downY = y
        tracking = true
        longFired = false
        view.removeCallbacks(longPressTask)
        view.postDelayed(longPressTask, FilePaging.LONG_PRESS_MS)
    }

    /** 任一方向超过容差即视为滑动，取消长按计时，避免滑动途中弹出面板。 */
    private fun onMove(
        x: Int,
        y: Int,
    ) {
        if (tracking && (abs(x - downX) > tokens.slop || abs(y - downY) > tokens.slop)) {
            longFired = false
            view.removeCallbacks(longPressTask)
        }
    }

    /** 结束本次触摸跟踪并取消未触发的长按。 */
    private fun reset() {
        tracking = false
        view.removeCallbacks(longPressTask)
    }

    /** 抬起时按位置分发：顶栏按钮、底栏按钮、翻页手势与点击。 */
    private fun handleUp(
        x: Int,
        y: Int,
    ) {
        when {
            chrome.containsBack(x, y) -> handler.onBack()
            chrome.containsTopAction(x, y) -> handler.onTopAction()
            chrome.containsPrev(x, y) -> handler.onPrevious()
            chrome.containsNext(x, y) -> handler.onNext()
            chrome.containsAction(x, y) -> handler.onAction()
            chrome.containsBar(x, y) -> Unit
            longFired -> Unit
            else -> dispatchContent(x, y)
        }
    }

    /** 非按钮区域按位移判定翻页或点击。 */
    private fun dispatchContent(
        x: Int,
        y: Int,
    ) {
        val dx = x - downX
        val dy = y - downY
        val threshold = tokens.dp(FilePaging.SWIPE_THRESHOLD_DP)
        when {
            FilePaging.isVerticalSwipe(dx, dy, threshold) -> if (dy < 0) handler.onNext() else handler.onPrevious()
            FilePaging.isTap(dx, dy, tokens.slop) -> handler.onTap(x, y)
        }
    }
}
