package com.shuiyu.octoberrain.feature.files.ui

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.feature.files.data.FileEntryMetadata
import com.shuiyu.octoberrain.feature.files.data.FileItem
import com.shuiyu.octoberrain.feature.files.data.FileListing
import com.shuiyu.octoberrain.feature.files.data.deleteFileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 列表与宫格共用详情、删除确认以及后台文件IO。 */
internal object FileEntryActions {
    fun details(
        context: Context,
        scope: CoroutineScope,
        item: FileItem,
    ) {
        scope.launch {
            val info =
                withContext(Dispatchers.IO) {
                    runCatching { FileEntryMetadata.read(File(item.path)) }.getOrNull()
                }
            if (info == null) {
                AppServices.router.toast("文件不存在或无法读取详情")
            } else {
                InkDialogs.choose(context, if (info.directory) "文件夹详情" else "文件详情", lines(info, item.mime)) { }
            }
        }
    }

    private fun lines(
        info: FileEntryMetadata,
        mime: String,
    ): List<String> =
        listOf(
            "名称：${info.name}",
            "位置：${info.path}",
            "类型：${if (info.directory) "文件夹" else mime.ifBlank { "普通文件" }}",
            if (info.directory) "大小：不含目录内容统计" else "大小：${FileListing.formatSize(info.bytes)}（${info.bytes} 字节）",
            "修改时间：${FileListing.formatTime(info.modified).ifBlank { "未知" }}",
            "可读：${if (info.readable) "是" else "否" }；可写：${if (info.writable) "是" else "否" }",
        )

    fun delete(
        context: Context,
        scope: CoroutineScope,
        item: FileItem,
        onDeleted: () -> Unit,
    ) {
        if (item.dir) return
        InkDialogs.confirm(
            context,
            "删除文件",
            "确定删除“${item.name}”？删除后无法恢复。",
            confirmLabel = "删除",
            onConfirm = {
                scope.launch {
                    val deleted =
                        withContext(Dispatchers.IO) {
                            runCatching { deleteFileEntry(File(item.path)) }.getOrDefault(false)
                        }
                    if (deleted) {
                        onDeleted()
                        AppServices.router.toast("文件已删除")
                    } else {
                        AppServices.router.toast("删除失败，请检查文件是否存在及存储写入权限")
                    }
                }
            },
        )
    }
}
