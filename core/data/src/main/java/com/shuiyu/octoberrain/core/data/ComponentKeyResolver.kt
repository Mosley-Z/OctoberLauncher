package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.EntryKeys
import com.shuiyu.octoberrain.core.common.InstalledApp

/**
 * 旧包级入口键的组件键解析。
 *
 * 配置迁移阶段无法读取应用目录，`app:<包名>` 形式的旧键必须保留到目录快照就绪后
 * 才能解析。本对象在内存配置上把所有旧键一次性改写为组件键，写回成功后才记录
 * [DesktopConfig.componentKeyMigrated]，失败时保持原快照，重启后重试；解析本身幂等。
 *
 * 解析结论：包名下恰有一个可启动 Activity 时改写为组件键；存在多个候选时保留原键
 * 并在 [DesktopConfig.legacyComponentDecisions] 记录 `pending`；该包名未安装时同样保留原键。
 *
 * 放在 core:data 而非 core:common 的原因：解析依赖应用目录快照，两个触发点
 * （目录刷新与备份导入）都在 core:data，放在本模块可直接复用同一实现，
 * 也避免 core:common 反向依赖数据层。
 */
object ComponentKeyResolver {
    /** 多候选旧键的结论标记。 */
    const val PENDING: String = "pending"

    /**
     * 在给定桌面配置上解析旧包级入口键，返回解析后的副本。
     *
     * 参数 desktop 为当前桌面配置；参数 apps 为当前应用目录快照。
     * 已标记完成的配置原样返回，因此可重复调用。
     */
    fun resolve(
        desktop: DesktopConfig,
        apps: List<InstalledApp>,
    ): DesktopConfig {
        if (desktop.componentKeyMigrated) return desktop
        val candidates =
            apps
                .filter { !it.activityName.isNullOrBlank() }
                .groupBy { it.packageName }
        val decisions = mutableMapOf<String, String>()

        /** 把单个入口键解析为组件键，无法确定时保留原键。 */
        fun resolveKey(key: String): String {
            val packageName = EntryKeys.legacyPackageOf(key) ?: return key
            val matches = candidates[packageName].orEmpty()
            return when (matches.size) {
                1 -> {
                    val activityName = matches.single().activityName.orEmpty()
                    EntryKeys.appKey(packageName, activityName)
                }

                0 -> key
                else -> {
                    decisions[key] = PENDING
                    key
                }
            }
        }

        val resolved =
            desktop.copy(
                order = desktop.order.map(::resolveKey).distinct(),
                favoriteEntryKeys = desktop.favoriteEntryKeys.map(::resolveKey).distinct(),
                folders =
                    desktop.folders.map { folder ->
                        folder.copy(memberKeys = folder.memberKeys.map(::resolveKey).distinct())
                    },
                legacyComponentDecisions =
                    if (decisions.isEmpty()) {
                        desktop.legacyComponentDecisions
                    } else {
                        desktop.legacyComponentDecisions + decisions
                    },
                componentKeyMigrated = true,
            )
        return if (resolved == desktop) desktop else resolved
    }

    /**
     * 目录快照就绪后执行一次解析并写回配置。
     *
     * 参数 store 为配置仓库；参数 apps 为应用目录快照。
     * 配置已完成、目录为空或写回失败时返回假且不写标记；写回成功返回真。
     */
    suspend fun runOnce(
        store: ConfigStore,
        apps: List<InstalledApp>,
    ): Boolean {
        if (!shouldRun(store, apps)) return false
        // 写回失败时配置保持原快照，标记仍为假，调用方下一轮可重试
        runCatching {
            store.edit { config -> config.copy(desktop = resolve(config.desktop, apps)) }
        }
        return store.state.value.desktop.componentKeyMigrated
    }

    /**
     * 判断本次是否需要解析并写回。
     *
     * 已完成解析或目录为空时跳过；目录为空表示尚未枚举完成，此时解析会把全部旧键
     * 判成未安装，需保留到下次执行。
     */
    private fun shouldRun(
        store: ConfigStore,
        apps: List<InstalledApp>,
    ): Boolean = !store.state.value.desktop.componentKeyMigrated && apps.isNotEmpty()
}
