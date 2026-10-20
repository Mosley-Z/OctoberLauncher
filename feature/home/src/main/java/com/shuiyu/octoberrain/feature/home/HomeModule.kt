package com.shuiyu.octoberrain.feature.home

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherModule
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.ScreenProvider
import com.shuiyu.octoberrain.core.common.ScreenRegistry

/**
 * 桌面模块入口。
 *
 * 由应用启动流程反射加载；安装时向页面注册表登记首页与应用信息页的提供者。
 * 模块缺失时其余页面仍可正常工作。
 */
class HomeModule : LauncherModule {
    /** 注册桌面页面提供者、布局快照服务与预览提供者。 */
    override fun install(context: Context) {
        ScreenRegistry.register(provider)
        // 美化模式按页展示应用时依赖该服务，缺失时对应入口自行降级。
        AppServices.layoutProvider = HomeDesktopLayoutProvider
        // 美化模式的缩略图直接复用桌面绘制代码，保证与真实桌面同比例。
        AppServices.previewProvider = HomeDesktopPreviewProvider
    }

    // 单例提供者沿用模块内既有小写命名，重命名会改动符号，按声明抑制类名规则。

    /** 页面提供者：处理首页、负一屏与应用信息页。 */
    @Suppress("ktlint:standard:class-naming")
    private object provider : ScreenProvider {
        override fun create(destination: Destination): Screen? =
            when (destination) {
                Destination.Home -> HomeScreen()
                Destination.Negative -> NegativeScreen()
                is Destination.AppInfo -> AppInfoScreen(destination.packageName)
                else -> null
            }
    }
}
