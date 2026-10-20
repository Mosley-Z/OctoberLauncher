package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_LOCK_BEHAVIOR
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_LOCK_SECURITY

/**
 * 锁屏与安全页的设置项声明。
 *
 * 启用开关与 PIN 操作由页面以自定义行与动作提供，其余条目为纯配置读写。
 */
internal object LockSpecs {
    val groups: List<SettingsGroupSpec> =
        listOf(
            SettingsGroupSpec(id = GROUP_LOCK_SECURITY, title = "锁屏", subtitle = "四位数字 PIN"),
            SettingsGroupSpec(
                id = GROUP_LOCK_BEHAVIOR,
                title = "行为",
                subtitle = "锁定触发条件",
                notes =
                    listOf(
                        "PIN 只保存在本机配置中，以加盐摘要形式存储，不保存明文；" +
                            "连续输入错误不会锁死设备，返回桌面重新进入即可。",
                    ),
            ),
        )

    val specs: List<SettingSpec> =
        listOf(
            SettingSpec(
                id = "lock.enabled",
                group = GROUP_LOCK_SECURITY,
                title = "启用锁屏",
                hint = "关闭后启动器不再显示 PIN 解锁界面",
                control = SettingControl.Custom(key = "lock.enabled"),
            ),
            SettingSpec(
                id = "lock.pinStatus",
                group = GROUP_LOCK_SECURITY,
                title = "PIN 状态",
                control =
                    SettingControl.Info(read = { config ->
                        if (runCatching {
                                com.shuiyu.octoberrain.core.common.AppServices.lock
                                    .hasPin()
                            }.getOrDefault(false)
                        ) {
                            "已设置"
                        } else {
                            "未设置"
                        }
                    }),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "lock.pinEdit",
                group = GROUP_LOCK_SECURITY,
                title = "修改 PIN",
                hint = "连续输入两次相同数字后生效",
                control =
                    SettingControl.Action(value = {
                        if (runCatching {
                                com.shuiyu.octoberrain.core.common.AppServices.lock
                                    .hasPin()
                            }.getOrDefault(false)
                        ) {
                            "四位数字"
                        } else {
                            null
                        }
                    }),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "lock.pinClear",
                group = GROUP_LOCK_SECURITY,
                title = "清除 PIN",
                hint = "清除后同时关闭锁屏",
                control = SettingControl.Action(),
                effect = SettingEffect.NONE,
            ),
            SettingSpec(
                id = "lock.autoLock",
                group = GROUP_LOCK_BEHAVIOR,
                title = "熄屏自动锁定",
                hint = "屏幕熄灭或进入待机后立即回到锁屏",
                control =
                    SettingControl.Toggle(
                        read = { it.lock.autoLock },
                        write = { config, value -> config.copy(lock = config.lock.copy(autoLock = value)) },
                    ),
            ),
        )
}
