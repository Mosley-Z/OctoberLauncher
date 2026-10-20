package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.net.Uri
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import java.io.File

// 外观页图片目录与缩略图缓存的动作。
//
// 这些动作需要页面上下文与重建回调，页面本身只保留一行委托调用，
// 以控制页面类的函数数量；动作与页面声明表通过设置项标识对应。

/** 更换图片目录：系统目录选择器优先，不可用时回退到手工输入绝对路径。 */
internal fun changeImageDirectory(
    context: Context,
    onChanged: () -> Unit,
) {
    if (AppServices.hasPicker()) {
        val started =
            runCatching {
                AppServices.picker.pickDirectory { uri: Uri? ->
                    val directory = uri?.let { ImageDirectoryStore.directoryOf(it) }
                    if (directory == null) {
                        SettingsUi.toast(context, "无法读取该目录")
                    } else {
                        saveImageDirectory(context, directory, onChanged)
                    }
                }
            }.getOrDefault(false)
        if (started) return
    }
    promptImageDirectory(context, onChanged)
}

/** 手工输入图片目录的绝对路径，校验目录存在后保存。 */
internal fun promptImageDirectory(
    context: Context,
    onChanged: () -> Unit,
) {
    InkDialogs.input(
        context,
        "图片目录",
        initial = ImageDirectoryStore.load(context).absolutePath,
        hint = "绝对路径，例如 /storage/emulated/0/Pictures",
    ) { text ->
        val directory = File(text.trim())
        if (!directory.isDirectory) {
            SettingsUi.toast(context, "目录不存在")
        } else {
            saveImageDirectory(context, directory, onChanged)
        }
        true
    }
}

/** 保存图片目录并让缩略图与内容视图失效。 */
private fun saveImageDirectory(
    context: Context,
    directory: File,
    onChanged: () -> Unit,
) {
    ImageDirectoryStore.save(context, directory)
    ThumbCacheRegistry.clearAll(context)
    RefreshScheduler.notifyContentChanged()
    SettingsUi.toast(context, "图片目录已更新")
    onChanged()
}

/** 清理全部缩略图缓存，提示释放的字节数并重建该行摘要。 */
internal fun clearThumbCache(
    context: Context,
    onChanged: () -> Unit,
) {
    val freed = runCatching { ThumbCacheRegistry.clearAll(context) }.getOrDefault(0L)
    SettingsUi.toast(context, "已清理缩略图缓存 ${formatCacheBytes(freed)}")
    onChanged()
}
