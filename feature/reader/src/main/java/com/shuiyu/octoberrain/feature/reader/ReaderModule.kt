package com.shuiyu.octoberrain.feature.reader

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherModule
import com.shuiyu.octoberrain.core.common.ReaderGateway
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.ScreenProvider
import com.shuiyu.octoberrain.core.common.ScreenRegistry

/** 阅读器通过common契约注册，文件与内容feature无需依赖此模块。 */
class ReaderModule : LauncherModule {
    override fun install(context: Context) {
        ScreenRegistry.register(
            object : ScreenProvider {
                override fun create(destination: Destination): Screen? =
                    (destination as? Destination.DocumentReader)?.let { ReaderScreen(it.ref) }
            },
        )
        AppServices.reader =
            ReaderGateway { ref ->
                if (AppServices.hasRouter()) {
                    AppServices.router.navigate(Destination.DocumentReader(ref))
                    true
                } else {
                    false
                }
            }
    }
}
