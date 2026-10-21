package com.shuiyu.octoberrain

import android.view.View

/**
 * 删除键长按计时器：按住达到阈值后回调一次，按住期间按固定间隔回报进度。
 *
 * 只有删除键参与计时，计时通过宿主的 postDelayed 排队执行；抬起、取消或视图分离时
 * 移除待执行回调并把进度复位为零。达到阈值后置位触发标记，宿主在随后的抬起事件中
 * 消费该标记，避免同一次按压既触发应急清除又执行退格。
 *
 * @param host 计时所依附的视图，负责排队与移除回调
 * @param onProgress 进度回调，取值为 0 至 1，0 表示未按压
 * @param onReached 按住达到阈值时的回调，在主线程执行
 * @param holdMillis 触发阈值，默认十秒
 */
internal class DeleteHoldTracker(
    private val host: View,
    private val onProgress: (Float) -> Unit,
    private val onReached: () -> Unit,
    private val holdMillis: Long = HOLD_MILLIS,
) {
    private var elapsedMillis = 0L
    private var running = false
    private var reached = false

    /** 计时推进器：每次递增固定步长，达到阈值后回调并停止排队。 */
    private val ticker =
        object : Runnable {
            override fun run() {
                if (!running) return
                elapsedMillis += STEP_MILLIS
                if (elapsedMillis >= holdMillis) {
                    running = false
                    reached = true
                    onProgress(0f)
                    onReached()
                } else {
                    onProgress(elapsedMillis.toFloat() / holdMillis)
                    host.postDelayed(this, STEP_MILLIS)
                }
            }
        }

    /** 视图分离监听器：覆盖层被移除时终止计时，避免回调在重新挂载后补发。 */
    private val detachWatcher =
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) = cancel()
        }

    init {
        host.addOnAttachStateChangeListener(detachWatcher)
    }

    /** 开始计时：已在计时时忽略重复的按下事件，进度与触发标记立即复位。 */
    fun start() {
        if (running) return
        running = true
        elapsedMillis = 0L
        reached = false
        onProgress(0f)
        host.postDelayed(ticker, STEP_MILLIS)
    }

    /**
     * 终止计时并移除待执行回调，进度复位为零。
     *
     * 触发标记由 [takeReached] 消费，此处保留该标记，供抬起事件判定按键是否分发。
     */
    fun cancel() {
        val active = running || elapsedMillis > 0L
        running = false
        elapsedMillis = 0L
        host.removeCallbacks(ticker)
        if (active) onProgress(0f)
    }

    /** 取走并清除本次按压的触发标记，供抬起事件判断是否跳过按键分发。 */
    fun takeReached(): Boolean {
        val value = reached
        reached = false
        return value
    }

    private companion object {
        /** 触发阈值：按住十秒。 */
        const val HOLD_MILLIS = 10_000L

        /** 进度回报间隔，十秒内共回报一百次。 */
        const val STEP_MILLIS = 100L
    }
}
