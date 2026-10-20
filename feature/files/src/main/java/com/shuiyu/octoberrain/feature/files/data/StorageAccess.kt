package com.shuiyu.octoberrain.feature.files.data

import android.content.Context
import android.os.Environment
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.device.PermissionCenter
import java.io.File

/**
 * 存储卷与文件访问授权入口。
 *
 * 浏览页需要列举共享存储中的任意目录，Android 11 起依赖「所有文件访问权限」，
 * 权限状态统一从公共权限契约读取，不重复实现判定；目录口径与内容模块保持一致，
 * 书籍目录名称取整合配置中的 booksDir。
 *
 * 参数 context 为任意上下文，内部使用应用上下文推导外置私有目录。
 */
class StorageAccess(
    context: Context,
) {
    private val appContext: Context = context.applicationContext ?: context

    /** 当前是否已具备浏览共享存储的权限，读取失败按未授权处理。 */
    fun hasAccess(): Boolean =
        runCatching {
            AppServices.permissions.snapshot()[PermissionCenter.KEY_STORAGE] == true
        }.getOrElse {
            InkLog.w(TAG, "存储权限状态读取失败：${it.message}")
            false
        }

    /** 主共享存储根目录，形如 /storage/emulated/0。 */
    fun externalRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    /**
     * 列出可浏览的存储卷根目录。
     *
     * 首个元素固定为主共享存储；可移除存储卡的根目录由外置私有目录反推得到，
     * 逐个确认可读，避免把尚未挂载的卷位展示出来。
     */
    fun volumes(): List<String> {
        val roots = linkedSetOf<String>()
        Environment.getExternalStorageDirectory()?.absolutePath?.let(roots::add)
        appContext.getExternalFilesDirs(null).orEmpty().forEach { directory ->
            val path = directory?.absolutePath ?: return@forEach
            val index = path.indexOf("/Android/")
            if (index <= 0) return@forEach
            val root = File(path.substring(0, index))
            if (root.isDirectory && root.canRead()) roots.add(root.absolutePath)
        }
        return roots.toList()
    }

    /** 判断目录当前是否可列举，用于区分未挂载与空目录。 */
    fun isReadable(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val directory = File(path)
        return directory.isDirectory && directory.canRead()
    }

    /** 卷根目录的显示名称，主存储显示为内部存储，其余显示为存储卡。 */
    fun volumeName(root: String?): String =
        when {
            root.isNullOrEmpty() -> "未知存储"
            root == externalRoot() -> "内部存储"
            else -> "存储卡"
        }

    /** 书籍根目录，名称取整合配置中的 booksDir，空值与读取失败时使用 Books。 */
    fun booksRoot(): File {
        val name =
            runCatching {
                AppServices.config.state.value.integrations.booksDir
                    .trim()
            }.getOrElse {
                InkLog.w(TAG, "书籍目录配置读取失败，使用默认值：${it.message}")
                ""
            }
        return File(externalRoot(), name.ifBlank { DEFAULT_BOOKS_DIR })
    }

    /** 图片根目录，位于主共享存储下的 Pictures。 */
    fun picturesRoot(): File = File(externalRoot(), PICTURES_DIR)

    /** 编辑结果输出目录，位于 Pictures/十雨桌面，后续编辑流程使用。 */
    fun editOutputRoot(): File = File(picturesRoot(), EDIT_OUTPUT_DIR)

    private companion object {
        private const val TAG = "文件存储"
        private const val DEFAULT_BOOKS_DIR = "Books"
        private const val PICTURES_DIR = "Pictures"
        private const val EDIT_OUTPUT_DIR = "十雨桌面"
    }
}
