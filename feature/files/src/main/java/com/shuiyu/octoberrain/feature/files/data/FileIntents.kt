package com.shuiyu.octoberrain.feature.files.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.shuiyu.octoberrain.feature.files.ui.PhotoEntryActivity

/**
 * 文件页与其他组件之间的参数约定。
 *
 * 键名带本应用包名前缀，避免与调用方附加项冲突。查看器所需的图片集合与起始下标
 * 属于进程内数据，改由路由参数传递，跨应用 Intent 只承载单个路径，外部注入通道随之关闭。
 */
object FileIntents {
    /** 单个文件绝对路径的附加项键名。 */
    const val EXTRA_PATH = "com.shuiyu.octoberrain.extra.FILE_PATH"

    /** 查看图片的系统意图，Uri 需已授予读取权限。 */
    fun viewImage(
        uri: Uri,
        mime: String?,
    ): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime?.takeIf { it.isNotEmpty() } ?: "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** 打开单个文件的系统意图，无法确定类型时使用通用二进制流。 */
    fun openFile(
        uri: Uri,
        mime: String?,
    ): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime?.takeIf { it.isNotEmpty() } ?: "application/octet-stream")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** 启动图片查看入口的显式意图，供页面在模块内跳转使用。 */
    fun photoEntry(
        context: Context,
        path: String,
    ): Intent =
        Intent(context, PhotoEntryActivity::class.java).apply {
            putExtra(EXTRA_PATH, path)
        }
}
