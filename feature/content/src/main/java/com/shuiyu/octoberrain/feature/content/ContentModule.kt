package com.shuiyu.octoberrain.feature.content

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherModule
import com.shuiyu.octoberrain.core.common.NegativeModuleRegistry
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.ScreenProvider
import com.shuiyu.octoberrain.core.common.ScreenRegistry
import com.shuiyu.octoberrain.feature.content.ui.BookScreen
import com.shuiyu.octoberrain.feature.content.ui.BookshelfScreen
import com.shuiyu.octoberrain.feature.content.ui.KoreaderScreen
import com.shuiyu.octoberrain.feature.content.ui.NoteScreen
import com.shuiyu.octoberrain.feature.content.ui.SearchScreen
import com.shuiyu.octoberrain.feature.content.ui.TodoScreen
import com.shuiyu.octoberrain.feature.content.ui.WereadScreen
import com.shuiyu.octoberrain.feature.content.ui.negative.DeskNegativeModule

/**
 * 内容域模块入口。
 *
 * 由宿主通过反射按固定类名实例化后调用 [install]：先构造内容数据源并替换
 * 应用级 [AppServices.content]，再把书架、微信读书、KOReader、搜索与
 * 最近任务五个页面注册到 [ScreenRegistry]，最后向 [NegativeModuleRegistry]
 * 注册墨台负一屏模块。模块内部不依赖其他 feature 模块。
 */
class ContentModule : LauncherModule {
    /** 安装内容域：替换内容数据源、注册内容页面与墨台负一屏模块。 */
    override fun install(context: Context) {
        val appContext = context.applicationContext ?: context
        val source = ContentSourceImpl(appContext)
        ContentRegistry.install(source)
        AppServices.content = source
        ScreenRegistry.register(ContentScreenProvider)
        // 墨台容器以单个视图承载五个分区，负一屏以该视图铺满页面
        NegativeModuleRegistry.register(DeskNegativeModule())
    }

    /**
     * 页面提供者：按目的地创建内容页面，未知目的地返回 null 交由宿主处理。
     * 内容域注册的页面均为单例目的地；文件页由 feature/files 单独注册。
     */
    private object ContentScreenProvider : ScreenProvider {
        override fun create(destination: Destination): Screen? =
            when (destination) {
                Destination.Bookshelf -> BookshelfScreen()
                Destination.Book -> BookScreen()
                Destination.Note -> NoteScreen()
                Destination.Todo -> TodoScreen()
                Destination.Weread -> WereadScreen()
                is Destination.WereadTab -> WereadScreen(destination.section)
                Destination.Koreader -> KoreaderScreen()
                Destination.Search -> SearchScreen()
                // 最近任务与使用统计页面位于 feature/content 根包，与提供者同包
                Destination.Recents -> RecentsScreen()
                else -> null
            }
    }
}
