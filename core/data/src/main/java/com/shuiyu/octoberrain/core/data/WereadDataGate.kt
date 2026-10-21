package com.shuiyu.octoberrain.core.data

/** 同步与迁移共享数据目录的互斥入口；仅登记任务，不持锁执行网络或文件操作。 */
object WereadDataGate {
    private enum class Operation {
        SYNC,
        IMPORT,
    }

    private var active: Operation? = null

    @Synchronized
    fun tryBeginSync(): Boolean = begin(Operation.SYNC)

    @Synchronized
    fun endSync() {
        if (active == Operation.SYNC) active = null
    }

    @Synchronized
    fun tryBeginImport(): Boolean = begin(Operation.IMPORT)

    @Synchronized
    fun endImport() {
        if (active == Operation.IMPORT) active = null
    }

    private fun begin(operation: Operation): Boolean {
        if (active != null) return false
        active = operation
        return true
    }
}
