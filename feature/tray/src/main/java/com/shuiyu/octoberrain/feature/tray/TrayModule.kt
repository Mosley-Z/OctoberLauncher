package com.shuiyu.octoberrain.feature.tray

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherModule

/** Retains the existing desktop status bar, without a pull-down panel or panel settings. */
class TrayModule : LauncherModule {
    override fun install(context: Context) {
        AppServices.tray = TrayControllerImpl()
    }
}
