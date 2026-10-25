package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.InkDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/** 清空仅清除自定义图标，桌面顺序与布局参数保持不变。 */
internal fun BeautifyScreen.confirmResetDesktop() {
    val context = root?.context ?: return
    InkDialogs.confirm(
        context,
        "清空图标",
        "仅清除全部自定义图标，桌面顺序与布局参数保持不变。",
        confirmLabel = "清除",
        onConfirm = {
            val active = root
            if (active != null) {
                runCatching { AppServices.iconOverrides.clearAll() }
                clearOverrideFiles(active.context)
                selectedImagePath = null
                showingOverrides = false
                showStatus("已清除全部自定义图标")
                SettingsUi.refresh()
                render()
            }
        },
    )
}

/** 删除图标覆盖的私有副本，避免清空后旧文件继续占用存储。 */
internal fun BeautifyScreen.clearOverrideFiles(context: Context) {
    scope.launch(Dispatchers.IO) {
        runCatching { File(context.filesDir, "icon-overrides").deleteRecursively() }
    }
}
