package com.shuiyu.octoberrain.feature.reader

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherModule
import com.shuiyu.octoberrain.core.common.ReaderGateway
import com.shuiyu.octoberrain.core.common.RecentDocument
import com.shuiyu.octoberrain.core.common.RecentGateway
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.ScreenProvider
import com.shuiyu.octoberrain.core.common.ScreenRegistry
import com.shuiyu.octoberrain.feature.reader.session.ReadingRepository
import com.shuiyu.octoberrain.feature.reader.session.ReadingStateRules

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
        AppServices.recent =
            RecentGateway { limit ->
                // 查询失败时返回空列表，书架入口按无记录处理，不阻塞页面渲染。
                runCatching {
                    ReadingRepository.recent(context, limit).map { record ->
                        RecentDocument(
                            name = ReadingStateRules.displayName(record.ref),
                            ref = record.ref,
                            updated = record.updated,
                        )
                    }
                }.getOrDefault(emptyList())
            }
    }
}
