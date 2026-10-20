package com.shuiyu.octoberrain.feature.files

import android.content.Context
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherModule
import com.shuiyu.octoberrain.core.common.NegativeModuleRegistry
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.ScreenProvider
import com.shuiyu.octoberrain.core.common.ScreenRegistry
import com.shuiyu.octoberrain.feature.files.ui.FilesScreen
import com.shuiyu.octoberrain.feature.files.ui.album.AlbumScreen
import com.shuiyu.octoberrain.feature.files.ui.edit.EditScreen
import com.shuiyu.octoberrain.feature.files.ui.edit.EditSource
import com.shuiyu.octoberrain.feature.files.ui.negative.FilesNegativeModule
import com.shuiyu.octoberrain.feature.files.ui.viewer.PhotoScreen

/**
 * 文件域模块入口。
 *
 * 由宿主通过反射按固定类名实例化后调用 [install]，把文件页注册到 [ScreenRegistry]。
 * 文件页目的地从内容模块迁出后由本模块独占处理，内容模块不再注册该目的地。
 */
class FilesModule : LauncherModule {
    /** 安装文件域：注册文件页工厂与负一屏卡片。 */
    override fun install(context: Context) {
        ScreenRegistry.register(FileScreenProvider)
        registerNegative()
    }

    /**
     * 注册负一屏「最近图片」卡片。
     *
     * 文件页工厂在同一次安装中先行注册，卡片内的「全部图片」入口因此总能解析到
     * [Destination.Files]；宿主容器对模块按标识查找，重复安装时按标识幂等跳过。
     */
    private fun registerNegative() {
        if (NegativeModuleRegistry.get(FilesNegativeModule.ID) == null) {
            NegativeModuleRegistry.register(FilesNegativeModule())
        }
    }

    /** 页面提供者：处理文件、相册、图片查看与图片编辑目的地，其余目的地返回 null 交由宿主处理。 */
    private object FileScreenProvider : ScreenProvider {
        /** 文件列表入口标识。 */
        private const val SOURCE_FILES = "files"

        override fun create(destination: Destination): Screen? =
            when (destination) {
                is Destination.Files -> FilesScreen(destination.path)
                is Destination.PhotoViewer -> PhotoScreen(destination.paths, destination.index)
                is Destination.PhotoEditor -> EditScreen(destination.path, editSourceOf(destination.source))
                is Destination.Album -> AlbumScreen()
                else -> null
            }

        /** 按来源标识映射编辑页入口来源，未指定时按查看器处理。 */
        private fun editSourceOf(source: String?): EditSource =
            if (source == SOURCE_FILES) EditSource.FILES else EditSource.VIEWER
    }
}
