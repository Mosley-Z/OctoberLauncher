package com.shuiyu.octoberrain.core.common

/**
 * PIN 失败计数与退避时长的纯逻辑计算。
 *
 * 连续失败达到 [MAX_ATTEMPTS] 后按指数退避延时，退避截止时间由调用方持久化；
 * 本对象不依赖 Android 框架，可直接在本地 JVM 测试。
 */
object PinLockPolicy {
    /** 连续失败达到该次数后开始退避。 */
    const val MAX_ATTEMPTS = 5

    private const val BASE_BACKOFF_MILLIS = 30_000L
    private const val MAX_BACKOFF_MILLIS = 900_000L
    private const val MILLIS_PER_SECOND = 1000L
    private const val ROUNDING_MILLIS = 999L

    /**
     * 计算当前连续失败次数下剩余的尝试次数。
     *
     * @param failedAttempts 已累计的连续失败次数
     * @return 未进入退避时可尝试的次数，达到上限后返回 0
     */
    fun remainingAttempts(failedAttempts: Int): Int = (MAX_ATTEMPTS - failedAttempts.coerceAtLeast(0)).coerceAtLeast(0)

    /**
     * 计算退避时长。
     *
     * 前四次失败不延时；第五次起以 30 秒为基数按 2 的幂递增，上限 15 分钟。
     *
     * @param failedAttempts 已累计的连续失败次数
     * @return 本次失败后需要等待的毫秒数，无需退避时返回 0
     */
    fun backoffMillis(failedAttempts: Int): Long {
        if (failedAttempts < MAX_ATTEMPTS) return 0L
        val exponent = (failedAttempts - MAX_ATTEMPTS).coerceIn(0, 5)
        return (BASE_BACKOFF_MILLIS shl exponent).coerceAtMost(MAX_BACKOFF_MILLIS)
    }

    /**
     * 计算锁定剩余秒数。
     *
     * @param lockoutUntilMillis 锁定截止时间，单位为毫秒时间戳
     * @param nowMillis 当前时间，单位为毫秒时间戳
     * @return 向上取整的剩余秒数，未在锁定中返回 0
     */
    fun remainingLockoutSeconds(
        lockoutUntilMillis: Long,
        nowMillis: Long,
    ): Long {
        val remaining = (lockoutUntilMillis - nowMillis).coerceAtLeast(0L)
        return if (remaining == 0L) 0L else (remaining + ROUNDING_MILLIS) / MILLIS_PER_SECOND
    }
}
