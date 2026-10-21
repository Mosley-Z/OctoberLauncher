package com.shuiyu.octoberrain.core.common

import java.util.concurrent.atomic.AtomicInteger

/**
 * 锁屏解锁代次。
 *
 * 后台校验 PBKDF2 摘要的耗时可达数秒，校验期间熄屏、重新锁定或改写 PIN 都会使锁定状态向前推进，
 * 此时旧校验结果不得再提交。调用方在开始校验时用 [current] 记录单号，提交前用 [commitIfCurrent]
 * 判断代次是否仍有效。
 */
class LockGeneration {
    /** 代次计数器，跨线程读写。 */
    private val counter = AtomicInteger(0)

    /** 读取当前代次。 */
    fun current(): Int = counter.get()

    /** 推进代次，使所有在途校验结果失效。 */
    fun bump() {
        counter.incrementAndGet()
    }

    /**
     * 代次未变时执行提交动作。
     *
     * @param mark 开始校验时记录的单号
     * @param commit 状态提交动作，仅在代次未变时执行
     * @return 执行了提交返回 true；代次已推进返回 false
     */
    fun commitIfCurrent(
        mark: Int,
        commit: () -> Unit,
    ): Boolean {
        if (counter.get() != mark) return false
        commit()
        return true
    }
}
