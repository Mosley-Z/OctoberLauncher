package com.shuiyu.octoberrain.feature.settings

import android.content.Intent
import android.provider.Settings
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkPage

/**
 * 系统设置分组页。
 *
 * 条目全部由 [SettingsCatalog.systemGroups] 的声明生成，每条只携带目标系统设置页的 Intent action。
 * 部分定制系统的设置首页排版异常，本页把常用条目拆出，优先启动对应的系统设置子页，
 * 缺失时退回系统设置首页。
 */
class SettingsSystemScreen : SettingsBaseScreen("系统设置") {
    /**
     * 系统设置条目目标。
     *
     * @param label 条目名称，用于启动失败时的提示。
     * @param action 目标系统设置页的 Intent action。
     */
    private data class SystemTarget(
        val label: String,
        val action: String,
    )

    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        SettingsSpecRenderer.renderPage(
            page = page,
            groups = SettingsCatalog.systemGroups,
            config = config,
            sink = settingEffectSink(),
            actions = systemActions(),
        )
    }

    /** 系统设置页的动作表，键为设置项标识。 */
    private fun systemActions(): Map<String, () -> Unit> {
        val actions = LinkedHashMap<String, () -> Unit>()
        SYSTEM_TARGETS.forEach { (id, target) ->
            actions[id] = { open(target) }
        }
        return actions
    }

    /**
     * 打开系统设置页。
     *
     * 参数 target 为条目目标；先按 action 查找可处理界面，
     * 不可用时退回 [Settings.ACTION_SETTINGS]。启动失败时给出提示。
     */
    private fun open(target: SystemTarget) {
        val context = contextOrNull() ?: return
        val intent = Intent(target.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = intent.resolveActivity(context.packageManager)
        val launch =
            if (resolved != null) {
                intent
            } else {
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val launched = runCatching { context.startActivity(launch) }.isSuccess
        if (!launched) {
            InkDialogs.toast(context, "${target.label}页面无法打开")
        }
    }

    private companion object {
        /** 设置项标识到系统设置页的映射。 */
        val SYSTEM_TARGETS: Map<String, SystemTarget> =
            mapOf(
                "system.wifi" to SystemTarget("WLAN", Settings.ACTION_WIFI_SETTINGS),
                "system.bluetooth" to SystemTarget("蓝牙", Settings.ACTION_BLUETOOTH_SETTINGS),
                "system.mobile" to SystemTarget("移动网络", Settings.ACTION_DATA_ROAMING_SETTINGS),
                "system.display.page" to SystemTarget("显示", Settings.ACTION_DISPLAY_SETTINGS),
                "system.sound" to SystemTarget("声音", Settings.ACTION_SOUND_SETTINGS),
                "system.displaySize" to SystemTarget("显示大小与文字", Settings.ACTION_DISPLAY_SETTINGS),
                "system.battery" to SystemTarget("电池", Intent.ACTION_POWER_USAGE_SUMMARY),
                "system.storage" to SystemTarget("存储空间", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                "system.apps" to SystemTarget("应用管理", Settings.ACTION_APPLICATION_SETTINGS),
                "system.permissions" to SystemTarget("应用权限", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                "system.date" to SystemTarget("日期与时间", Settings.ACTION_DATE_SETTINGS),
                "system.locale" to SystemTarget("语言与输入法", Settings.ACTION_LOCALE_SETTINGS),
                "system.developer" to SystemTarget("开发者选项", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                "system.update" to SystemTarget("系统更新", Settings.ACTION_DEVICE_INFO_SETTINGS),
            )
    }
}
