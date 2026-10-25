package com.shuiyu.octoberrain.feature.files.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.feature.files.data.FileItem
import java.io.File

/** APK只在用户选择后交给系统安装器或现有结构阅读器，不保存默认动作。 */
internal object ApkOpenActions {
    fun choose(
        context: Context,
        item: FileItem,
        viewStructure: () -> Unit,
    ) {
        InkDialogs.choose(context, item.name, listOf("安装", "查看结构")) { action ->
            when (action) {
                0 -> install(context, File(item.path))
                1 -> viewStructure()
            }
        }
    }

    private fun install(
        context: Context,
        file: File,
    ) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, FileItem.APK_MIME)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            InkDialogs.toast(context, "无法打开系统安装界面")
        }
    }
}
