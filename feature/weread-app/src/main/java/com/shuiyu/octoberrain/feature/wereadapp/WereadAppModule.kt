package com.shuiyu.octoberrain.feature.wereadapp

import android.content.Context
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherModule
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.ScreenProvider
import com.shuiyu.octoberrain.core.common.ScreenRegistry

/**
 * 设置内微读墨记页面模块。
 *
 * 由宿主按固定类名反射实例化后调用 [install]：把 [Destination.WereadApp]
 * 对应的页面注册到 [ScreenRegistry]。模块只依赖 core 模块与内核，
 * 页面内部的数据读取走微读数据目录，不依赖其他 feature 模块。
 */
class WereadAppModule : LauncherModule {
    /** 安装微读墨记页面：注册页面提供者。 */
    override fun install(context: Context) {
        ScreenRegistry.register(WereadAppScreenProvider)
    }

    /** 页面提供者：只处理微读墨记目的地，其余目的地返回 null 交由宿主处理。 */
    private object WereadAppScreenProvider : ScreenProvider {
        override fun create(destination: Destination): Screen? =
            when (destination) {
                Destination.WereadApp -> WereadAppScreen()
                Destination.WereadSettings -> WereadAppScreen(openSettings = true)
                else -> null
            }
    }
}
