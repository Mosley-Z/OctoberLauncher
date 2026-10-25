package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.LauncherConfig

/** v16只恢复旧策略隐藏的文件入口；之后显式重新隐藏不再被迁移覆盖。 */
internal object FilesEntryMigration {
    private const val VERSION = 16

    fun migrate(config: LauncherConfig): LauncherConfig {
        if (config.version >= VERSION) return config
        return config.copy(
            version = VERSION,
            desktop = config.desktop.copy(hiddenSpecials = config.desktop.hiddenSpecials - "special:files"),
        )
    }
}
