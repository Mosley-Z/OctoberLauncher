package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.widget.WidgetHitRegion
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 手势对象读取状态与回投动作的端口。
 *
 * 组件以匿名子类覆盖需要的方法，未覆盖的成员保持空实现，避免手势逻辑与组件实现互相依赖。
 */
internal abstract class WereadBoardGestureHost {
    /** 当前形态。 */
    open val form: WereadBoardForm
        get() = WereadBoardForm.WEEK

    /** 轮播状态。 */
    open val carousel: WereadBoardCarousel?
        get() = null

    /** 最近一帧的版式参数。 */
    open val frame: WereadBoardFrame?
        get() = null

    /** 画布缩放系数。 */
    open val scale: Float
        get() = 0f

    /** 最近一帧的底部按钮几何。 */
    open val chrome: WereadBoardChrome?
        get() = null

    /** 请求重绘。 */
    open fun requestDraw() = Unit

    /** 启动落位动画。 */
    open fun settle(target: Int) = Unit

    /** 切到下一个形态。 */
    open fun switchForm() = Unit

    /** 刷新数据。 */
    open fun refresh(): Boolean = false

    /** 打开当前中央书目。 */
    open fun open() = Unit

    /** 分发内核命中区域的动作。 */
    open fun dispatch(action: String): Boolean = false

    /** 查询组件内命中区域。 */
    open fun hitRegion(
        x: Float,
        y: Float,
    ): WidgetHitRegion? = null

    /** 把纵向滚动量交给内核。 */
    open fun kernelScroll(step: Int): Boolean = false
}

/**
 * 卡片的触摸判定与点击分发。
 *
 * 本书形态只在起点位于封面带内、横向位移超过阈值且明显大于纵向位移时消费移动，
 * 横向拖动按中央封面宽度换算为轮播位置；其余形态把纵向拖动交给内核滚动，
 * 横向移动不消费，交由桌面处理翻页。点击优先命中底部按钮，其次封面层与内核区域。
 *
 * @param host 状态与动作的提供方
 * @param slop 判定拖动所需的最小位移，单位像素
 */
internal class WereadBoardGestures(
    private val host: WereadBoardGestureHost,
    private val slop: Float,
) {
    /** 是否已进入横向拖动。 */
    private var dragging: Boolean = false

    /** 按下点横坐标。 */
    private var pressX: Float = 0f

    /** 按下点纵坐标。 */
    private var pressY: Float = 0f

    /** 上一次移动的横坐标。 */
    private var lastX: Float = 0f

    private var bookDrag: Float = 0f

    /** 触摸序列是否已按下且尚未抬起。 */
    private var touchActive: Boolean = false

    /** 是否已越过拖动阈值进入纵向滚动。 */
    private var touchScrolling: Boolean = false

    /** 按下点的组件内纵坐标。 */
    private var touchDownY: Float = 0f

    /** 最近一次移动点的组件内纵坐标。 */
    private var touchLastY: Float = 0f

    /**
     * 处理按下：记录起点，内核形态直接消费以便接收后续拖动。
     *
     * @param x 组件局部横坐标
     * @param y 组件局部纵坐标
     * @return 是否消费
     */
    fun down(
        x: Float,
        y: Float,
    ): Boolean {
        pressX = x
        pressY = y
        lastX = x
        dragging = false
        bookDrag = 0f
        touchActive = true
        touchScrolling = false
        touchDownY = y
        touchLastY = y
        return host.form != WereadBoardForm.BOOK
    }

    /**
     * 处理移动。
     *
     * @param x 组件局部横坐标
     * @param y 组件局部纵坐标
     * @return 是否消费
     */
    fun move(
        x: Float,
        y: Float,
    ): Boolean =
        if (host.form == WereadBoardForm.BOOK) {
            moveBook(x, y)
        } else {
            moveKernel(y)
        }

    /**
     * 处理抬起。
     *
     * @return 是否消费
     */
    fun up(): Boolean =
        if (host.form == WereadBoardForm.BOOK) {
            endBookDrag()
        } else {
            endKernelTouch()
        }

    /**
     * 处理取消。
     *
     * @return 是否消费
     */
    fun cancel(): Boolean =
        if (host.form == WereadBoardForm.BOOK) {
            val wasDragging = dragging
            dragging = false
            host.carousel?.clearDrag()
            host.requestDraw()
            wasDragging
        } else {
            endKernelTouch()
        }

    /**
     * 本书形态的横向拖动。
     *
     * @param x 组件局部横坐标
     * @param y 组件局部纵坐标
     * @return 是否消费
     */
    private fun moveBook(
        x: Float,
        y: Float,
    ): Boolean {
        val layout = host.frame ?: return false
        val consumed = startDrag(layout, x, y)
        if (consumed) {
            bookDrag += -(x - lastX) / layout.coverWidth.coerceAtLeast(1f)
            lastX = x
        }
        return consumed
    }

    /**
     * 内核形态的纵向滚动：越过阈值后按位移换算为逻辑像素。
     *
     * @param y 组件局部纵坐标
     * @return 是否消费
     */
    private fun moveKernel(y: Float): Boolean {
        val scale = host.scale
        val active = touchActive && scale > 0f && (touchScrolling || abs(y - touchDownY) >= slop * SCROLL_SLOP_RATIO)
        val step =
            if (active) {
                touchScrolling = true
                val delta = ((y - touchLastY) / scale).roundToInt()
                touchLastY = y
                delta
            } else {
                0
            }
        return active && step != 0 && host.kernelScroll(step)
    }

    /**
     * 判定本次移动是否进入横向拖动。
     *
     * @param layout 当前版式
     * @param x 组件局部横坐标
     * @param y 组件局部纵坐标
     * @return 是否处于拖动状态
     */
    private fun startDrag(
        layout: WereadBoardFrame,
        x: Float,
        y: Float,
    ): Boolean {
        val dx = x - pressX
        val dy = y - pressY
        val horizontal = abs(dx) > slop && abs(dx) > abs(dy) * HORIZONTAL_BIAS
        return when {
            dragging -> true
            !horizontal || layout.coverWidth <= 0f || !layout.coverArea.contains(pressX, pressY) -> false
            else -> {
                dragging = true
                lastX = pressX
                true
            }
        }
    }

    /**
     * 本书形态抬起：按拖动方向落位，未拖动时交回宿主触发点击。
     *
     * @return 是否消费
     */
    private fun endBookDrag(): Boolean {
        val carousel = host.carousel
        val started = dragging
        dragging = false
        if (!started || carousel == null) return false
        host.settle(carousel.selectedIndex + swipeDirectionOf(bookDrag))
        return true
    }

    /**
     * 内核形态抬起或取消：复位状态并返回是否滚动过。
     *
     * @return 滚动过时返回 true
     */
    private fun endKernelTouch(): Boolean {
        val scrolled = touchScrolling
        touchActive = false
        touchScrolling = false
        touchDownY = 0f
        touchLastY = 0f
        return scrolled
    }

    /** 手势相关的常量。 */
    companion object {
        /** 横向位移需超过纵向位移的倍数才判定为拖动。 */
        const val HORIZONTAL_BIAS: Float = 1.2f

        /** 纵向滚动阈值相对拖动阈值的倍数。 */
        const val SCROLL_SLOP_RATIO: Float = 3f

        /** 触发换书所需的最小拖动量，单位为「一本」。 */
        const val SWIPE_TRIGGER: Float = 0.30f
    }
}

/**
 * 按拖动偏移给出换书方向。
 *
 * @param drag 拖动偏移，正值表示向下一本
 * @return 换书方向，零表示回弹
 */
internal fun swipeDirectionOf(drag: Float): Int =
    when {
        drag > WereadBoardGestures.SWIPE_TRIGGER -> 1
        drag < -WereadBoardGestures.SWIPE_TRIGGER -> -1
        else -> 0
    }
