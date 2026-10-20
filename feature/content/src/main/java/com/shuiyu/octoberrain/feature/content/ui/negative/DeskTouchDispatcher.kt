package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.inkread.weekread.kernel.desk.DeskPage
import kotlin.math.abs

/**
 * 墨台分区的触摸分派器。
 *
 * 按微读墨记的交互约定把负一屏内的触摸事件翻译为内核动作：
 * 轻点交内核判定命中并分派动作，长按调用内核的长按动作入口，
 * 纵向拖动在具备内滚条件时驱动分区滚动，价格键盘打开期间的事件全部交给键盘。
 *
 * 分派器只保存手势状态，页面对象、顶栏高度与重绘请求由宿主提供，
 * 命中坐标统一加上顶栏高度换算为墨台页面坐标。
 *
 * @param context 上下文，用于读取触摸阈值
 * @param view 接收触摸的视图，用于向上层请求不拦截事件
 * @param host 宿主，提供页面、顶栏高度与动作回调
 */
@Suppress("TooManyFunctions")
internal class DeskTouchDispatcher(
    context: Context,
    private val view: View,
    private val host: Host,
) {
    /** 分派器向宿主请求的状态与回调。 */
    internal interface Host {
        /**
         * 返回当前已绑定的墨台页面。
         *
         * @return 已绑定页面，尚未加载时返回 null
         */
        fun kernelPage(): DeskPage?

        /**
         * 返回墨台顶栏高度。
         *
         * @return 顶栏高度，单位为像素
         */
        fun headerOffset(): Float

        /**
         * 处理内核长按动作。
         *
         * @param action 内核返回的长按动作编码
         */
        fun onLongPressAction(action: Int)

        /**
         * 处理内核点按动作。
         *
         * @param action 内核返回的动作编码
         */
        fun onTapAction(action: Int)

        /** 价格键盘状态变化后刷新页面尺寸与绘制内容。 */
        fun onPadChanged()

        /** 请求宿主重绘分区。 */
        fun invalidateSection()

        /** 内滚结束后请求宿主刷新分区的绘制内容。 */
        fun onScrollFinished()
    }

    private val handler = Handler(Looper.getMainLooper())

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    private val longPressRunnable = Runnable { fireLongPress() }

    private var state: State = State.IDLE

    private var downX: Float = 0f

    private var downY: Float = 0f

    private var lastY: Float = 0f

    /**
     * 处理分区触摸事件。
     *
     * @param event 触摸事件
     * @return 事件被消费时返回 true
     */
    fun onTouchEvent(event: MotionEvent): Boolean =
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> handleCancel()
            else -> false
        }

    /**
     * 判断是否处于纵向拖动状态。
     *
     * 视图绘制在拖动期间需要直接渲染，避免位图缓存的异步刷新造成画面滞后。
     *
     * @return 正在拖动时返回 true
     */
    fun isScrolling(): Boolean = state == State.SCROLLING

    /** 视图从窗口移除时停止计时并复位手势状态。 */
    fun cancel() {
        reset()
    }

    private fun handleDown(event: MotionEvent): Boolean {
        reset()
        downX = event.x
        downY = event.y
        lastY = event.y
        val page = host.kernelPage()
        if (page?.padOpen() == true) {
            // 键盘打开期间不启动长按计时，全部事件交给键盘处理
            state = State.KEYPAD
            disallowIntercept(true)
        } else {
            handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            state = State.PRESSED
        }
        return true
    }

    private fun handleMove(event: MotionEvent): Boolean {
        val delta = event.y - lastY
        lastY = event.y
        return when (state) {
            State.PRESSED -> beginDrag(event, delta)
            State.SCROLLING -> scrollContent(delta)
            State.KEYPAD, State.LONG_CONSUMED -> true
            State.IDLE -> false
        }
    }

    /**
     * 按下后移动超过触摸阈值时决定是否进入内滚。
     *
     * 分区支持内滚且滚动被内核接受时，接管后续事件并驱动滚动；
     * 否则放弃本次手势，把事件交回外层的负一屏滚动容器。
     *
     * @param event 当前移动事件
     * @param delta 相对上一次事件的纵向位移
     * @return 事件被消费时返回 true
     */
    private fun beginDrag(
        event: MotionEvent,
        delta: Float,
    ): Boolean {
        val exceeded =
            abs(event.y - downY) > touchSlop || abs(event.x - downX) > touchSlop
        var handled = true
        if (exceeded) {
            handler.removeCallbacks(longPressRunnable)
            val page = host.kernelPage()
            val accepted = page != null && page.canScroll() && page.scrollBy(-delta)
            handled = accepted
            if (accepted) {
                state = State.SCROLLING
                disallowIntercept(true)
                host.invalidateSection()
            } else {
                state = State.IDLE
                disallowIntercept(false)
            }
        }
        return handled
    }

    /**
     * 驱动分区内容滚动。
     *
     * @param delta 相对上一次事件的纵向位移
     * @return 事件被消费时返回 true
     */
    private fun scrollContent(delta: Float): Boolean {
        host.kernelPage()?.scrollBy(-delta)
        host.invalidateSection()
        return true
    }

    private fun handleUp(event: MotionEvent): Boolean {
        handler.removeCallbacks(longPressRunnable)
        return when (state) {
            State.SCROLLING, State.LONG_CONSUMED -> finish()
            State.KEYPAD -> pressPad(event)
            State.PRESSED -> tap(event)
            State.IDLE -> false
        }
    }

    private fun finish(): Boolean {
        val wasScrolling = state == State.SCROLLING
        state = State.IDLE
        disallowIntercept(false)
        if (wasScrolling) host.onScrollFinished()
        return true
    }

    /**
     * 把抬起事件按轻点处理，交内核判定命中并分派动作。
     *
     * @param event 抬起事件
     * @return 事件被消费时返回 true
     */
    private fun tap(event: MotionEvent): Boolean {
        val page = host.kernelPage()
        if (page != null) {
            val action = page.hitTest(event.x, event.y + host.headerOffset())
            if (action != DeskPage.ACTION_NONE) host.onTapAction(action)
            if (page.padOpen()) host.onPadChanged()
        }
        return finish()
    }

    /**
     * 把抬起事件交给价格键盘处理。
     *
     * @param event 抬起事件
     * @return 事件被消费时返回 true
     */
    private fun pressPad(event: MotionEvent): Boolean {
        val page = host.kernelPage()
        if (page != null) {
            val key = page.hitPad(event.x, event.y + host.headerOffset())
            if (key >= 0) {
                page.padPress(key)
                host.onPadChanged()
            }
        }
        return finish()
    }

    private fun handleCancel(): Boolean {
        reset()
        return false
    }

    /** 长按计时到期后执行内核长按动作。 */
    private fun fireLongPress() {
        val page = host.kernelPage()
        if (state == State.PRESSED && page != null) {
            val action = page.longActionAt(downX, downY + host.headerOffset())
            if (action != DeskPage.ACTION_NONE) {
                state = State.LONG_CONSUMED
                host.onLongPressAction(action)
            }
        }
    }

    private fun reset() {
        handler.removeCallbacks(longPressRunnable)
        state = State.IDLE
        disallowIntercept(false)
    }

    private fun disallowIntercept(disallow: Boolean) {
        view.parent?.requestDisallowInterceptTouchEvent(disallow)
    }

    /** 手势状态。 */
    private enum class State {
        IDLE,
        PRESSED,
        SCROLLING,
        KEYPAD,
        LONG_CONSUMED,
    }

    private companion object {
        /** 长按判定时长，与微读墨记的交互一致，单位为毫秒。 */
        const val LONG_PRESS_MS: Long = 550L
    }
}
