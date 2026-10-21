package com.shuiyu.octoberrain.core.common

/**
 * 校验成功后的 PIN 摘要迁移。
 *
 * 摘要登记的轮数与目标轮数不一致时，按同一盐与明文重新派生并写回配置；写回失败时保留原摘要并记录一次警告，
 * 本次解锁结果不受影响。迁移与失败记录清空放在同一次配置事务内，避免多次写盘。
 */
class PinMigration(
    private val config: ConfigStore,
) {
    /**
     * 校验成功后写回摘要与失败记录。
     *
     * @param pin 本次校验通过的 PIN
     * @param saltHex 十六进制盐串
     * @param stored 已保存的摘要串
     * @param targetIterations 目标轮数，由调用方按配置换算
     * @return 配置写入成功返回 true；写入失败返回 false
     */
    fun onVerified(
        pin: String,
        saltHex: String,
        stored: String,
        targetIterations: Int,
    ): Boolean {
        val target = PinHasher.effectiveIterations(targetIterations)
        val migrated = if (PinHasher.needsUpgrade(stored, target)) PinHasher.hash(pin, saltHex, target) else null
        return runCatching {
            config.edit { current ->
                current.copy(
                    lock =
                        current.lock.copy(
                            pinHash = migrated ?: current.lock.pinHash,
                            iterations = target,
                            failedAttempts = 0,
                            lockoutUntilMillis = 0L,
                        ),
                )
            }
        }.onFailure { error ->
            InkLog.w("锁屏", "PIN 摘要迁移写入失败，保留原摘要", error)
        }.isSuccess
    }
}
