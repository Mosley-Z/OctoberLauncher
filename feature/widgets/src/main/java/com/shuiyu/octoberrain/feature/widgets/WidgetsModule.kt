package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import com.shuiyu.octoberrain.core.common.LauncherModule
import com.shuiyu.octoberrain.core.common.ScreenRegistry
import com.shuiyu.octoberrain.core.widget.WidgetRegistry

/**
 * 组件模块入口：注册全部组件、组件库页面与钟面样式页。
 *
 * 模块由 Application 通过类名反射加载，安装过程不依赖宿主 Activity。
 */
class WidgetsModule : LauncherModule {
    override fun install(context: Context) {
        WidgetCatalog.providers.forEach { WidgetRegistry.register(it) }
        ScreenRegistry.register(WidgetPickerScreen.Provider)
        ScreenRegistry.register(ClockStyleScreen.Provider)
    }
}
