package com.shuiyu.octoberrain.feature.reader.source

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DocumentRef
import java.io.File

/** 仅用户明确点击时交给其他应用；排除本入口，避免不支持格式循环打开自己。 */
internal fun openExternalDocument(
    context: Context,
    ref: DocumentRef,
) {
    val result =
        runCatching {
            val uri =
                when (ref) {
                    is DocumentRef.LocalPath ->
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.files",
                            File(ref.path),
                        )
                    is DocumentRef.ContentUri -> Uri.parse(ref.uri)
                    is DocumentRef.ArchiveEntry -> throw IllegalArgumentException("归档条目尚未准备")
                }
            val view =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser =
                Intent.createChooser(view, "使用其他应用打开").putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(context, DocumentEntryActivity::class.java)),
                )
            context.startActivity(chooser)
        }
    if (result.isFailure) AppServices.router.toast("无法向其他应用授权此文件，请从文件管理器重新选择")
}
