package com.shuiyu.octoberrain

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.RefreshPolicy
import com.shuiyu.octoberrain.core.common.RefreshReason
import com.shuiyu.octoberrain.core.render.RefreshScheduler

/**
 * 读取当前装配的刷新策略。
 *
 * 全局服务表中 refresh 的声明类型为 RefreshRequester，实际装配的是本模块的控制器，
 * 这里统一完成一次类型判断，避免各调用点重复书写强制转换。
 *
 * @return 已装配且实现刷新策略时返回策略对象，服务尚未装配时返回 null。
 */
internal fun currentRefreshPolicy(): RefreshPolicy? = runCatching { AppServices.refresh as? RefreshPolicy }.getOrNull()

/**
 * 整屏刷新控制器：以全屏黑色覆盖层模拟墨水屏闪烁清残影。
 *
 * 闪烁次数按触发场景区分：手动触发与退出应用使用两次闪烁，每次持续 90 毫秒、间隔 120 毫秒；
 * 定时、外观与内容变化使用单次快速闪烁，持续 70 毫秒。
 * 整屏刷新完成后把完成时刻登记到 [RefreshScheduler]，与定时整刷共用同一时间基准；
 * 局部重绘直接调用 invalidate，由窗口系统交给墨水屏驱动做局刷。
 */
object InkRefreshController : RefreshPolicy {
    /** 两次闪烁场景下覆盖层的停留时长，单位毫秒。 */
    private const val FLASH_HOLD_MS = 90L

    /** 两次闪烁之间的间隔时长，单位毫秒。 */
    private const val FLASH_GAP_MS = 120L

    /** 单次快速闪烁的停留时长，单位毫秒。 */
    private const val FAST_FLASH_HOLD_MS = 70L

    private var root: View? = null

    private val handler = Handler(Looper.getMainLooper())

    /** 闪烁动画是否正在执行，用于抑制重复请求。 */
    private var flashing = false

    /**
     * 绑定刷新所作用的根视图，闪烁覆盖层添加在该视图之上。
     *
     * @param view 活动窗口的根视图。
     */
    fun attach(view: View) {
        root = view
    }

    /** 解除绑定并撤销所有挂起的闪烁任务。 */
    fun detach() {
        root = null
        flashing = false
        handler.removeCallbacksAndMessages(null)
    }

    /** 请求一次整屏刷新，按手动触发处理。 */
    override fun requestFullRefresh() {
        requestFullRefresh(RefreshReason.MANUAL)
    }

    /**
     * 按场景请求一次整屏刷新。
     *
     * 闪烁进行中收到的请求会被丢弃，避免覆盖层叠加与闪烁节奏错乱。
     * 闪烁结束后由 [RefreshScheduler.onFullRefreshPerformed] 登记完成时刻。
     *
     * @param reason 触发场景，决定闪烁次数。
     */
    override fun requestFullRefresh(reason: RefreshReason) {
        val host = root ?: return
        if (host !is ViewGroup) return
        if (flashing) return
        flashing = true
        val rounds = if (needsDoubleFlash(reason)) 2 else 1
        flash(host, 1, rounds)
    }

    /** 请求一次局部刷新，直接重绘根视图。 */
    override fun requestPartial() {
        root?.invalidate()
    }

    /**
     * 回到前台时执行一次局部重绘。
     *
     * 整屏刷新由宿主按“从其他应用返回后刷新”配置决定，这里只保证画面内容先重绘一次。
     */
    override fun onForegrounded() {
        requestPartial()
    }

    /**
     * 进入后台时的处理。
     *
     * 后台停留时长由宿主在生命周期回调中记录并判断，本入口保留空实现以符合策略接口。
     */
    override fun onBackgrounded() {
    }

    /** 距离最近一次整屏刷新的毫秒数，与调度器共用同一完成时刻。 */
    override fun millisSinceFullRefresh(): Long = RefreshScheduler.millisSinceFullRefresh()

    /**
     * 判断场景是否需要两次闪烁。
     *
     * @param reason 触发场景。
     * @return 手动触发与退出应用返回 true，其余场景返回 false。
     */
    private fun needsDoubleFlash(reason: RefreshReason): Boolean =
        when (reason) {
            RefreshReason.MANUAL, RefreshReason.APP_EXIT -> true
            RefreshReason.INTERVAL, RefreshReason.APPEARANCE, RefreshReason.CONTENT -> false
        }

    /**
     * 执行一次闪烁并安排后续轮次。
     *
     * @param host 承载覆盖层的根视图。
     * @param round 当前轮次，从 1 开始。
     * @param rounds 总轮次，1 表示单次快速闪烁。
     */
    private fun flash(
        host: ViewGroup,
        round: Int,
        rounds: Int,
    ) {
        val overlay =
            FrameLayout(host.context).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                isClickable = false
                isFocusable = false
            }
        host.addView(overlay)
        val hold = if (rounds == 1) FAST_FLASH_HOLD_MS else FLASH_HOLD_MS
        handler.postDelayed({
            runCatching { host.removeView(overlay) }
            if (round < rounds) {
                handler.postDelayed({ flash(host, round + 1, rounds) }, FLASH_GAP_MS)
            } else {
                flashing = false
                host.invalidate()
                RefreshScheduler.onFullRefreshPerformed()
            }
        }, hold)
    }
}
