package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LockGeneration] 的时序测试：校验期间的锁定动作使旧结果失效。 */
class LockGenerationTest {
    @Test
    fun `校验期间重新锁定后旧结果不提交`() {
        val lock = FakeLock()
        val mark = lock.beginVerify()

        lock.lock()

        assertFalse(lock.finishVerify(mark, verified = true))
        assertTrue(lock.locked)
    }

    @Test
    fun `校验期间熄屏后旧结果不提交`() {
        val lock = FakeLock()
        val mark = lock.beginVerify()

        lock.screenOff()

        assertFalse(lock.finishVerify(mark, verified = true))
        assertTrue(lock.locked)
    }

    @Test
    fun `校验期间改密后旧结果不提交`() {
        val lock = FakeLock()
        val mark = lock.beginVerify()

        lock.setPin()

        assertFalse(lock.finishVerify(mark, verified = true))
        assertTrue(lock.locked)
    }

    @Test
    fun `代次未变时校验通过即解除锁定`() {
        val lock = FakeLock()
        val mark = lock.beginVerify()

        assertTrue(lock.finishVerify(mark, verified = true))
        assertFalse(lock.locked)
    }

    @Test
    fun `代次未变时校验失败保持锁定`() {
        val lock = FakeLock()
        val mark = lock.beginVerify()

        assertTrue(lock.finishVerify(mark, verified = false))
        assertTrue(lock.locked)
    }

    /** 以 [LockGeneration] 模拟锁屏的校验时序。 */
    private class FakeLock {
        private val generation = LockGeneration()

        /** 当前是否锁定。 */
        var locked = true

        /** 开始一次后台校验，返回开始时的代次。 */
        fun beginVerify(): Int = generation.current()

        /** 模拟立即上锁。 */
        fun lock() {
            generation.bump()
            locked = true
        }

        /** 模拟熄屏自动上锁。 */
        fun screenOff() {
            generation.bump()
            locked = true
        }

        /** 模拟改写 PIN。 */
        fun setPin() {
            generation.bump()
        }

        /**
         * 提交后台校验结果。
         *
         * @param mark 开始校验时记录的单号
         * @param verified 校验是否通过
         * @return 结果被接受返回 true
         */
        fun finishVerify(
            mark: Int,
            verified: Boolean,
        ): Boolean = generation.commitIfCurrent(mark) { locked = !verified }
    }
}
