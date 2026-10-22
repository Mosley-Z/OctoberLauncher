package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.LauncherConfig

/** 锁屏、通知和托盘设置不触发桌面组件重载。 */
internal fun homeConfigChanged(
    previous: LauncherConfig?,
    next: LauncherConfig,
): Boolean =
    previous == null ||
        previous.desktop != next.desktop ||
        previous.appearance != next.appearance ||
        previous.integrations != next.integrations
