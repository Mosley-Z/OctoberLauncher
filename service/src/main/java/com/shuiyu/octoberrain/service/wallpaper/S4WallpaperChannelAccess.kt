package com.shuiyu.octoberrain.service.wallpaper

import android.accessibilityservice.AccessibilityService
import com.shuiyu.octoberrain.core.common.AppServices

/**
 * 壁纸通道与无障碍服务的连接点。
 *
 * 通道实现由应用层装配到 [AppServices.wallpaper]；无障碍服务在连接与断开时通过本对象
 * 传递服务实例，避免通道持有 Context 之外的全局状态。通道未装配时两个方法都不动作。
 */
internal object S4WallpaperChannelAccess {
    /** 把已连接的无障碍服务交给壁纸通道。 */
    fun attach(service: AccessibilityService) {
        (AppServices.wallpaper as? S4WallpaperChannel)?.attach(service)
    }

    /** 通知壁纸通道无障碍服务已断开。 */
    fun detach() {
        (AppServices.wallpaper as? S4WallpaperChannel)?.detach()
    }
}
