package com.shuiyu.octoberrain

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.LockConfig
import com.shuiyu.octoberrain.core.common.LockGate
import com.shuiyu.octoberrain.core.common.LockGeneration
import com.shuiyu.octoberrain.core.common.PinHasher
import com.shuiyu.octoberrain.core.common.PinLockPolicy
import com.shuiyu.octoberrain.core.common.PinMigration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 锁屏实现：PIN 摘要校验、失败退避与锁定态恢复。
 *
 * 摘要算法为 PBKDF2WithHmacSHA256，轮数取自配置并统一按默认值兜底；盐为 16 字节随机数，
 * 以十六进制保存。旧版单次 SHA-256 摘要仍可校验，首次校验成功后自动升级为新格式。
 * 连续失败达到五次后按指数退避延时，失败次数与锁定截止时间写入配置，进程重启后继续生效。
 */
class LockGateImpl(
    private val context: Context,
    private val config: ConfigStore,
) : LockGate {
    /** 锁定状态流，界面可订阅。 */
    private val _state =
        MutableStateFlow(
            config.state.value.lock.let { lock ->
                lock.enabled && lock.autoLock && !lock.pinHash.isNullOrBlank()
            },
        )

    /** 锁定状态流，界面可订阅。 */
    val state: StateFlow<Boolean> = _state.asStateFlow()

    private var observer: ((Boolean) -> Unit)? = null

    /** PIN 校验用的后台作用域，摘要迭代在此执行，避免阻塞主线程输入派发。 */
    private val verifyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 主线程回调器，校验结果回到主线程更新界面。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 解锁代次，校验期间熄屏、重新锁定或改密时用于作废旧结果。 */
    private val generation = LockGeneration()

    /** 校验成功后的摘要迁移与失败记录写回。 */
    private val pinMigration = PinMigration(config)

    override val isLocked: Boolean
        get() = _state.value

    override fun hasPin(): Boolean =
        !config.state.value.lock.pinHash
            .isNullOrBlank()

    override fun lock() {
        val lockConfig = config.state.value.lock
        if (lockConfig.enabled && hasPin()) {
            generation.bump()
            _state.value = true
            observer?.invoke(true)
        }
    }

    override fun unlock(pin: String): Boolean = verifyPin(pin) == LockVerifyResult.PASSED

    /**
     * 校验 PIN 并提交结果。
     *
     * 校验开始前记录解锁代次，校验返回后仅当代次未变时写入失败次数或解除锁定；
     * 代次已变说明期间发生了熄屏、重新锁定或改密，本次结果作废，界面不再提示错误。
     *
     * @param pin 待校验的四位密码
     * @return 校验通过、密码错误或结果作废
     */
    private fun verifyPin(pin: String): LockVerifyResult {
        val lockConfig = config.state.value.lock
        val salt = lockConfig.salt
        val stored = lockConfig.pinHash
        val now = System.currentTimeMillis()
        return if (!hasPin()) {
            LockVerifyResult.PASSED
        } else if (salt == null ||
            stored == null ||
            PinLockPolicy.remainingLockoutSeconds(lockConfig.lockoutUntilMillis, now) > 0L
        ) {
            LockVerifyResult.FAILED
        } else {
            val mark = generation.current()
            // 临时测量：PBKDF2 耗时与轮数，用于判断解锁时延的分布。
            val verifyStartedAt = System.nanoTime()
            val ok = runCatching { PinHasher.verify(pin, salt, stored) }.getOrDefault(false)
            val verifyMs = (System.nanoTime() - verifyStartedAt) / 1_000_000L
            InkLog.d("LockTiming", "TEMP unlock verify=${verifyMs}ms rounds=${PinHasher.iterationsOf(stored)}")
            val committed =
                generation.commitIfCurrent(mark) {
                    if (ok) {
                        applySuccess(pin, salt, lockConfig)
                    } else {
                        val attempts = lockConfig.failedAttempts + 1
                        val backoff = PinLockPolicy.backoffMillis(attempts)
                        val until = if (backoff > 0L) now + backoff else lockConfig.lockoutUntilMillis
                        config.edit { current ->
                            current.copy(
                                lock =
                                    current.lock.copy(
                                        failedAttempts = attempts,
                                        lockoutUntilMillis = until,
                                    ),
                            )
                        }
                    }
                }
            when {
                !committed -> {
                    InkLog.d("LockTiming", "TEMP unlock stale mark=$mark")
                    LockVerifyResult.CANCELLED
                }
                ok -> LockVerifyResult.PASSED
                else -> LockVerifyResult.FAILED
            }
        }
    }

    override fun setPin(pin: String) {
        generation.bump()
        val salt = PinHasher.newSalt()
        val iterations = PinHasher.effectiveIterations(config.state.value.lock.iterations)
        // 临时测量：摘要计算与配置写入的耗时。
        val hashStartedAt = System.nanoTime()
        val hash = PinHasher.hash(pin, salt, iterations)
        val hashMs = (System.nanoTime() - hashStartedAt) / 1_000_000L
        InkLog.d("LockTiming", "TEMP setPin hash=${hashMs}ms rounds=$iterations")
        val editStartedAt = System.nanoTime()
        config.edit { current ->
            current.copy(
                lock =
                    current.lock.copy(
                        pinHash = hash,
                        salt = salt,
                        iterations = iterations,
                        enabled = true,
                        failedAttempts = 0,
                        lockoutUntilMillis = 0L,
                    ),
            )
        }
        val editMs = (System.nanoTime() - editStartedAt) / 1_000_000L
        InkLog.d("LockTiming", "TEMP setPin edit=${editMs}ms")
        _state.value = false
        generation.bump()
    }

    override fun clearPin() {
        generation.bump()
        config.edit { current ->
            current.copy(
                lock =
                    current.lock.copy(
                        pinHash = null,
                        salt = null,
                        enabled = false,
                        failedAttempts = 0,
                        lockoutUntilMillis = 0L,
                    ),
            )
        }
        _state.value = false
        observer?.invoke(false)
    }

    override fun createOverlay(
        context: Context,
        onEmergencyReset: () -> Unit,
    ): View? {
        val lockConfig = config.state.value.lock
        if (!lockConfig.enabled || !hasPin()) return null
        return LockOverlayView(
            context = context,
            verify = { pin, onResult ->
                // PBKDF2 迭代耗时在低端设备上可达数秒，必须在后台线程执行，
                // 校验结果回到主线程再驱动界面状态。
                verifyScope.launch {
                    val enteredAt = System.nanoTime()
                    InkLog.d("LockTiming", "TEMP unlock coroutine-enter")
                    val result = runCatching { verifyPin(pin) }.getOrDefault(LockVerifyResult.FAILED)
                    mainHandler.post {
                        val mainMs = (System.nanoTime() - enteredAt) / 1_000_000L
                        InkLog.d("LockTiming", "TEMP unlock main-callback=${mainMs}ms")
                        onResult(result)
                    }
                }
            },
            onUnlocked = { InkLog.d("LockTiming", "TEMP unlock onUnlocked") },
            remainingAttempts = {
                PinLockPolicy.remainingAttempts(config.state.value.lock.failedAttempts)
            },
            lockoutSeconds = {
                PinLockPolicy.remainingLockoutSeconds(
                    config.state.value.lock.lockoutUntilMillis,
                    System.currentTimeMillis(),
                )
            },
            emergencyResetEnabled = {
                config.state.value.lock.emergencyResetEnabled
            },
            onEmergencyReset = onEmergencyReset,
        )
    }

    override fun onScreenOff() {
        val lockConfig = config.state.value.lock
        if (lockConfig.enabled && lockConfig.autoLock) {
            generation.bump()
            lock()
        }
    }

    /** 安装锁状态监听，界面用来切换覆盖层。 */
    fun observe(listener: ((Boolean) -> Unit)?) {
        observer = listener
    }

    /**
     * 校验成功后的状态处理：迁移摘要、清空失败记录并解除锁定。
     *
     * @param pin 本次校验通过的 PIN
     * @param salt 当前盐串
     * @param lockConfig 校验开始时的锁屏配置
     */
    private fun applySuccess(
        pin: String,
        salt: String,
        lockConfig: LockConfig,
    ) {
        val target = PinHasher.effectiveIterations(lockConfig.iterations)
        // 临时测量：摘要迁移与配置写入的耗时。
        val commitStartedAt = System.nanoTime()
        pinMigration.onVerified(pin, salt, lockConfig.pinHash.orEmpty(), target)
        val commitMs = (System.nanoTime() - commitStartedAt) / 1_000_000L
        InkLog.d("LockTiming", "TEMP unlock commit=${commitMs}ms target=$target")
        generation.bump()
        _state.value = false
        observer?.invoke(false)
    }
}
