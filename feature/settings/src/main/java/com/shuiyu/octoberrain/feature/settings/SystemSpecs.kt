package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_SYSTEM_CONNECTION
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_SYSTEM_DEVICE
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_SYSTEM_DISPLAY
import com.shuiyu.octoberrain.feature.settings.SettingsCatalog.GROUP_SYSTEM_SYSTEM

/**
 * 系统设置页的设置项声明。
 *
 * 全部为跳转动作，不写配置；点击行为由页面按条目标识提供。
 */
internal object SystemSpecs {
    val groups: List<SettingsGroupSpec> =
        listOf(
            SettingsGroupSpec(id = GROUP_SYSTEM_CONNECTION, title = "连接", subtitle = "网络与外部设备"),
            SettingsGroupSpec(id = GROUP_SYSTEM_DISPLAY, title = "显示与声音", subtitle = "屏幕、亮度与音量"),
            SettingsGroupSpec(id = GROUP_SYSTEM_DEVICE, title = "设备", subtitle = "电量、存储与应用"),
            SettingsGroupSpec(
                id = GROUP_SYSTEM_SYSTEM,
                title = "系统",
                subtitle = "时间、语言与开发者选项",
                notes =
                    listOf(
                        "条目直接跳转系统设置子页，避免部分定制系统设置首页的排版缺陷；缺少对应页面时退回系统设置首页。",
                    ),
            ),
        )

    val specs: List<SettingSpec> =
        listOf(
            systemAction("system.wifi", GROUP_SYSTEM_CONNECTION, "WLAN", "无线网络开关与已保存网络"),
            systemAction("system.bluetooth", GROUP_SYSTEM_CONNECTION, "蓝牙", "配对设备与蓝牙开关"),
            systemAction("system.mobile", GROUP_SYSTEM_CONNECTION, "移动网络", "数据开关与运营商设置"),
            systemAction("system.display.page", GROUP_SYSTEM_DISPLAY, "显示", "亮度、休眠与字体大小"),
            systemAction("system.sound", GROUP_SYSTEM_DISPLAY, "声音", "媒体音量与提示音"),
            systemAction("system.displaySize", GROUP_SYSTEM_DISPLAY, "显示大小与文字", "界面缩放与字号"),
            systemAction("system.battery", GROUP_SYSTEM_DEVICE, "电池", "耗电统计与省电模式"),
            systemAction("system.storage", GROUP_SYSTEM_DEVICE, "存储空间", "内部存储占用与清理"),
            systemAction("system.apps", GROUP_SYSTEM_DEVICE, "应用管理", "查看、停用与卸载应用"),
            systemAction("system.permissions", GROUP_SYSTEM_DEVICE, "应用权限", "按权限查看已授权应用"),
            systemAction("system.date", GROUP_SYSTEM_SYSTEM, "日期与时间", "自动时间、时区与格式"),
            systemAction("system.locale", GROUP_SYSTEM_SYSTEM, "语言与输入法", "系统语言与默认输入法"),
            systemAction("system.developer", GROUP_SYSTEM_SYSTEM, "开发者选项", "调试与动画缩放"),
            systemAction("system.update", GROUP_SYSTEM_SYSTEM, "系统更新", "版本信息与系统升级"),
        )
}
