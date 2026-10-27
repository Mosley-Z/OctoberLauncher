package com.shuiyu.octoberrain

/** 完整同步忙碌时合并为下一轮；领取和释放在同一把锁内，避免完成瞬间漏掉请求。 */
internal class WereadSyncQueue {
    private var running = false
    private val pending = mutableListOf<(Boolean, String) -> Unit>()

    @Synchronized
    fun isRunning(): Boolean = running

    /** 返回 true 时由调用方启动任务，否则回调交给下一轮完整同步。 */
    @Synchronized
    fun submitFull(callback: (Boolean, String) -> Unit): Boolean {
        if (running) {
            pending += callback
            return false
        }
        running = true
        return true
    }

    /** 卡片局部刷新仍在忙碌时拒绝，不扩大请求范围。 */
    @Synchronized
    fun tryStartRefresh(): Boolean {
        if (running) return false
        running = true
        return true
    }

    /** 有待处理请求时保持占用，确保下一轮开始前不会插入并发任务。 */
    @Synchronized
    fun finish(): List<(Boolean, String) -> Unit> {
        val callbacks = pending.toList()
        pending.clear()
        running = callbacks.isNotEmpty()
        return callbacks
    }
}
