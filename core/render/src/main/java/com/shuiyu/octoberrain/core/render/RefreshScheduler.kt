package com.shuiyu.octoberrain.core.render

import android.os.Handler
import android.os.Looper
import com.shuiyu.octoberrain.core.common.RefreshPolicy
import com.shuiyu.octoberrain.core.common.RefreshReason
import com.shuiyu.octoberrain.core.common.RefreshRequester
import java.util.concurrent.TimeUnit

/**
 * 刷新调度：整屏刷新时机的唯一出口。
 *
 * 墨水屏的残影随刷新次数累积，常规做法是定时做一次整屏闪烁清除残影。
 * 定时到点、退出应用、外观变化与手动操作都经由本对象转发给已装配的刷新入口，
 * 整屏刷新完成时刻只在 [onFullRefreshPerformed] 中记录，保证各类触发共用同一时间基准，
 * 避免出现两套计时器导致短时间内重复整屏刷新。
 */
object RefreshScheduler {
    /** 默认定时整屏刷新间隔，单位分钟。 */
    private const val DEFAULT_INTERVAL_MINUTES = 0

    private val handler = Handler(Looper.getMainLooper())

    /** 接收刷新请求的入口，应用启动时装配。 */
    private var requester: RefreshRequester? = null

    /** 自动整屏刷新间隔，单位分钟，0 表示关闭。 */
    private var intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES

    private var active = false

    /** 最近一次整屏刷新完成的时刻，0 表示尚未完成过整屏刷新。 */
    private var lastFullAt: Long = 0L

    /** 已完成全刷的版本，阅读翻页计数据此跟随定时/手动全刷重置。 */
    var fullRefreshVersion: Long = 0L
        private set

    private val fullRefreshTask = Runnable { requestFullRefresh(RefreshReason.INTERVAL) }

    private val partialTask =
        Runnable {
            requester?.requestPartial()
        }

    /**
     * 装配刷新入口并启动定时整屏刷新。
     *
     * @param requester 接收刷新请求的入口，通常为 InkRefreshController。
     * @param intervalMinutes 自动整屏刷新间隔，单位分钟，0 表示关闭。
     */
    fun attach(
        requester: RefreshRequester,
        intervalMinutes: Int,
    ) {
        this.requester = requester
        updateInterval(intervalMinutes)
    }

    /**
     * 更新整屏刷新间隔。
     *
     * 调用后撤销原定时任务，并按新间隔重新计时；间隔为 0 时只撤销任务，不再投递。
     *
     * @param minutes 新的间隔分钟数，小于 0 时按 0 处理。
     */
    fun updateInterval(minutes: Int) {
        intervalMinutes = minutes.coerceAtLeast(0)
        handler.removeCallbacks(fullRefreshTask)
        if (active && intervalMinutes > 0) {
            handler.postDelayed(fullRefreshTask, TimeUnit.MINUTES.toMillis(intervalMinutes.toLong()))
        }
    }

    /** 停止全部定时任务并解除装配，Activity 销毁时调用。 */
    fun detach() {
        handler.removeCallbacks(fullRefreshTask)
        handler.removeCallbacks(partialTask)
        requester = null
        active = false
    }

    /** 后台撤销定时；恢复后从当前时刻计时，不补刷后台积压。 */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        updateInterval(intervalMinutes)
        if (!active) handler.removeCallbacks(partialTask)
    }

    /** 内容变化后请求局部重绘，同一帧内的多次调用会合并。 */
    fun notifyContentChanged() {
        handler.removeCallbacks(partialTask)
        if (active) handler.post(partialTask)
    }

    /**
     * 按场景请求一次整屏刷新。
     *
     * 刷新入口实现 [RefreshPolicy] 时，由策略层完成重复请求抑制与闪烁样式选择；
     * 未实现时退化为无参请求，并立即记录完成时刻与重新计时。
     *
     * @param reason 触发场景，缺省为手动触发。
     */
    fun requestFullRefresh(reason: RefreshReason = RefreshReason.MANUAL) {
        if (!active) return
        val target = requester ?: return
        if (target is RefreshPolicy) {
            target.requestFullRefresh(reason)
        } else {
            target.requestFullRefresh()
            onFullRefreshPerformed()
        }
    }

    /**
     * 记录一次整屏刷新完成，并重置定时整屏刷新的计时。
     *
     * 由刷新入口在闪烁动画结束后调用。整屏刷新进行期间到点的定时任务已被撤销，
     * 本次完成后按当前间隔重新投递，使各类触发共享同一完成时刻。
     */
    fun onFullRefreshPerformed() {
        fullRefreshVersion++
        lastFullAt = System.currentTimeMillis()
        handler.removeCallbacks(fullRefreshTask)
        if (active && intervalMinutes > 0) {
            handler.postDelayed(fullRefreshTask, TimeUnit.MINUTES.toMillis(intervalMinutes.toLong()))
        }
    }

    /**
     * 最近一次整屏刷新距今的毫秒数，供设置页展示。
     *
     * @return 尚未完成过整屏刷新时返回 [Long.MAX_VALUE]。
     */
    fun millisSinceFullRefresh(): Long =
        if (lastFullAt == 0L) Long.MAX_VALUE else System.currentTimeMillis() - lastFullAt
}
