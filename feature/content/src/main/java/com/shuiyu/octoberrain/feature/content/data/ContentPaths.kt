package com.shuiyu.octoberrain.feature.content.data

import android.content.Context
import android.os.Environment
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.device.PermissionCenter
import java.io.File

/**
 * 内容域路径与配置访问工具。
 *
 * 所有外部存储路径都从配置中的整合设置推导，配置读取失败时退回到默认值，
 * 保证模块在宿主尚未完成装配时也不抛异常。存储权限状态通过公共权限契约读取。
 */
object ContentPaths {
    private const val TAG = "内容路径"

    /** 读取当前整合设置，读取失败返回默认配置。 */
    fun integrations(): IntegrationsConfig =
        runCatching {
            AppServices.config.state.value.integrations
        }.getOrElse {
            InkLog.w(TAG, "整合配置读取失败，使用默认值：${it.message}")
            IntegrationsConfig()
        }

    /** 外部存储根目录，对应 /storage/emulated/0。 */
    fun externalRoot(): File = Environment.getExternalStorageDirectory()

    /** 书籍根目录，目录名取配置项 booksDir，空值与异常时使用 Books。 */
    fun booksRoot(): File {
        val name = integrations().booksDir.trim().ifBlank { "Books" }
        return File(externalRoot(), name)
    }

    /** 书籍显式封面目录，位于书籍根目录下的 cover 子目录。 */
    fun coverRoot(): File = File(booksRoot(), "cover")

    /** KOReader 数据目录，按常见挂载点依次探测，未找到返回 null。 */
    fun koreaderRoot(): File? {
        val candidates =
            listOf(
                File(externalRoot(), "koreader"),
                File("/sdcard/koreader"),
                File("/storage/emulated/0/koreader"),
            )
        return candidates.firstOrNull { it.isDirectory }
    }

    /** 存储权限是否已授予，读取失败按未授权处理。 */
    fun hasStorageAccess(): Boolean =
        runCatching {
            AppServices.permissions.snapshot()[PermissionCenter.KEY_STORAGE] == true
        }.getOrElse { false }

    /** 返回缓存目录下的指定子目录，不存在时创建。 */
    fun cacheDirectory(
        context: Context,
        name: String,
    ): File =
        File(context.cacheDir, name).apply {
            if (!exists()) mkdirs()
        }

    /** 返回应用私有文件目录下的指定子目录，不存在时创建。 */
    fun filesDirectory(
        context: Context,
        name: String,
    ): File =
        File(context.filesDir, name).apply {
            if (!exists()) mkdirs()
        }
}
