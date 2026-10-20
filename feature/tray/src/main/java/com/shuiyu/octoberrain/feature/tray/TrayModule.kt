package com.shuiyu.octoberrain.feature.tray

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherModule
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.ScreenProvider
import com.shuiyu.octoberrain.core.common.ScreenRegistry

/**
 * 托盘模块入口：安装托盘控制器并注册托盘设置页。
 *
 * 由宿主反射加载；安装完成后 [AppServices.tray] 指向本模块实现，
 * 状态栏与托盘覆盖层均通过该实现创建。
 */
class TrayModule : LauncherModule {
    override fun install(context: Context) {
        AppServices.tray = TrayControllerImpl(context)
        ScreenRegistry.register(TrayScreenProvider)
    }
}

/** 托盘与通知设置页工厂。 */
private object TrayScreenProvider : ScreenProvider {
    override fun create(destination: Destination): Screen? =
        when (destination) {
            Destination.TrayPrefs -> TrayPrefsScreen()
            Destination.NotificationPrefs -> NotificationPrefsScreen()
            else -> null
        }
}
