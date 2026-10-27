package com.shuiyu.octoberrain.core.wereadsync

/** 自动任务最多发起12次请求、20秒后不再发请求；单次连接/读取各不超过5秒。 */
class AutomaticSyncBudget(
    private val isActive: () -> Boolean,
    private val clock: () -> Long,
) {
    private val started = clock()
    private var requests = 0
    var stopped: Boolean = false
        private set

    fun nextTimeoutMillis(): Int? {
        val remaining = 20_000L - (clock() - started)
        val exhausted = requests >= 12 || remaining < 2L
        if (stopped || !isActive() || exhausted) {
            stopped = true
            return null
        }
        requests++
        return minOf(5_000L, remaining / 2).toInt()
    }
}
