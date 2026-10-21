package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import com.inkread.weekread.kernel.desk.DeskHost
import com.shuiyu.octoberrain.core.common.NegativeHost
import com.shuiyu.octoberrain.core.wereadsync.WereadDeskEditsStore
import java.io.File

/**
 * 负一屏墨台分区视图使用的内核宿主。
 *
 * 宿主把十雨桌面的上下文与配置暴露给墨台内核：分区掩码只包含当前模块对应的位，
 * 使内核渲染出的页面仅含本分区；外观选项来自
 * [com.shuiyu.octoberrain.core.common.IntegrationsConfig.wereadMenu]，与桌面
 * 整体卡片共用同一份配置；颜色令牌映射到当前主题的墨水色，使分区绘制与
 * 桌面其余部分保持同一视觉体系。宿主对象的职责是持有协作对象，内核契约的
 * 具体实现在 [WereadDeskHostImpl]。
 *
 * @property host 面向内核的宿主接口实现
 * @param context 视图上下文，用于定位微读数据目录
 * @param screenHost 负一屏宿主，用于请求重绘与页面跳转
 * @param moduleId 当前模块标识，取值为 bill、rank、fp
 * @param onWallpaper 内核请求生成壁纸时的回调
 * @param onFingerprint 内核请求选择指纹书目时的回调
 * @param onSettings 内核请求打开墨台设置态的回调
 */
internal class WereadDeskHost(
    context: Context,
    screenHost: NegativeHost,
    moduleId: String,
    onWallpaper: () -> Unit,
    onFingerprint: () -> Unit,
    onSettings: () -> Unit,
) {
    private val appContext: Context = context.applicationContext ?: context

    /** 墨单外观选项读取器，把内核键名映射到当前配置。 */
    private val menu: WereadDeskMenu =
        WereadDeskMenu(
            fpBooks = { WereadFingerprintBooks.read(appContext) },
            edits = WereadDeskEditsStore(File(appContext.filesDir, "weread")),
        )

    /** 内核宿主接口实现，覆盖分区掩码、顺序、外观、目录、尺寸与跳转六类能力。 */
    val host: DeskHost =
        WereadDeskHostImpl(
            moduleId = moduleId,
            menu = menu,
            appContext = appContext,
            screenHost = screenHost,
            onWallpaper = onWallpaper,
            onFingerprint = onFingerprint,
            onSettings = onSettings,
        )
}
