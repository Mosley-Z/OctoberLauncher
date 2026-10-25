package com.shuiyu.octoberrain.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.shuiyu.octoberrain.core.common.AppServices

/** 桌面菜单与应用信息共用系统卸载确认入口。 */
internal object AppUninstall {
    fun request(
        context: Context,
        packageName: String,
    ) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            AppServices.router.toast("无法打开卸载界面")
        }
    }
}
