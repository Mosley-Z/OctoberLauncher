package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets

/** 权限与本地备份的直接入口。 */
internal class ProjectContentScreen : SettingsBaseScreen("权限与备份") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        page.content.addView(
            InkWidgets.row(page.context, "十雨所需权限与授权", "查看") {
                AppServices.router.navigate(Destination.Permissions)
            },
        )
        page.content.addView(InkWidgets.divider(page.context))
        page.content.addView(
            InkWidgets.row(page.context, "备份与恢复", "本地备份") {
                AppServices.router.navigate(Destination.Backup)
            },
        )
    }
}
