package com.shuiyu.octoberrain

import android.content.Context
import android.view.View
import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.LockConfig
import com.shuiyu.octoberrain.core.common.LockGate
import com.shuiyu.octoberrain.core.common.PinHasher
import com.shuiyu.octoberrain.core.common.PinLockPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    override val isLocked: Boolean
        get() = _state.value

    override fun hasPin(): Boolean =
        !config.state.value.lock.pinHash
            .isNullOrBlank()

    override fun lock() {
        val lockConfig = config.state.value.lock
        if (lockConfig.enabled && hasPin()) {
            _state.value = true
            observer?.invoke(true)
        }
    }

    override fun unlock(pin: String): Boolean {
        if (!hasPin()) return true
        val lockConfig = config.state.value.lock
        val salt = lockConfig.salt ?: return false
        val stored = lockConfig.pinHash ?: return false
        val now = System.currentTimeMillis()
        if (PinLockPolicy.remainingLockoutSeconds(lockConfig.lockoutUntilMillis, now) > 0L) {
            return false
        }
        val ok = runCatching { PinHasher.verify(pin, salt, stored) }.getOrDefault(false)
        if (ok) {
            applySuccess(pin, salt, lockConfig)
        } else {
            applyFailure(lockConfig, now)
        }
        return ok
    }

    override fun setPin(pin: String) {
        val salt = PinHasher.newSalt()
        val iterations = PinHasher.effectiveIterations(config.state.value.lock.iterations)
        val hash = PinHasher.hash(pin, salt, iterations)
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
        _state.value = false
    }

    override fun clearPin() {
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

    override fun createOverlay(context: Context): View? {
        val lockConfig = config.state.value.lock
        if (!lockConfig.enabled || !hasPin()) return null
        return LockOverlayView(
            context = context,
            verify = { pin -> unlock(pin) },
            onUnlocked = {},
            remainingAttempts = {
                PinLockPolicy.remainingAttempts(config.state.value.lock.failedAttempts)
            },
            lockoutSeconds = {
                PinLockPolicy.remainingLockoutSeconds(
                    config.state.value.lock.lockoutUntilMillis,
                    System.currentTimeMillis(),
                )
            },
        )
    }

    override fun onScreenOff() {
        val lockConfig = config.state.value.lock
        if (lockConfig.enabled && lockConfig.autoLock) lock()
    }

    /** 安装锁状态监听，界面用来切换覆盖层。 */
    fun observe(listener: ((Boolean) -> Unit)?) {
        observer = listener
    }

    /**
     * 校验成功后的状态处理：清空失败记录，必要时把旧摘要升级为新格式。
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
        val upgrade = PinHasher.needsUpgrade(lockConfig.pinHash.orEmpty(), target)
        val upgradedHash = if (upgrade) PinHasher.hash(pin, salt, target) else null
        config.edit { current ->
            current.copy(
                lock =
                    current.lock.copy(
                        pinHash = upgradedHash ?: current.lock.pinHash,
                        iterations = target,
                        failedAttempts = 0,
                        lockoutUntilMillis = 0L,
                    ),
            )
        }
        _state.value = false
        observer?.invoke(false)
    }

    /**
     * 校验失败后的状态处理：累加失败次数，达到阈值时写入退避截止时间。
     *
     * @param lockConfig 校验开始时的锁屏配置
     * @param now 本次校验的毫秒时间戳
     */
    private fun applyFailure(
        lockConfig: LockConfig,
        now: Long,
    ) {
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
