package com.shuiyu.octoberrain

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.WereadImporter
import com.shuiyu.octoberrain.core.data.WereadDataGate
import com.shuiyu.octoberrain.core.data.wereadimport.WereadImportStore
import com.shuiyu.octoberrain.core.data.wereadimport.WereadMigrationCoordinator
import com.shuiyu.octoberrain.core.data.wereadimport.WereadMigrationOutcome
import java.io.File
import java.lang.ref.WeakReference
import java.security.SecureRandom
import java.util.UUID

/**
 * 微读墨记数据迁移的宿主实现。
 *
 * 宿主 Activity 创建时挂接本对象；设置页通过 [AppServices.wereadImport] 发起请求，
 * 迁移包由微读墨记的导出组件写入本应用缓存目录下的固定文件，结果经
 * [onActivityResult] 回到这里并交给迁移编排处理。
 *
 * 迁移数据落位到 filesDir/weread，备份目录为 filesDir/weread-backup；
 * 已有数据由落位器备份后替换，相同摘要由迁移编排判定幂等。
 */
object HostWereadImport : WereadImporter {
    /** 迁移请求码，与文件选择器请求码区分。 */
    const val REQUEST_IMPORT = 0x0F10

    /** 导出组件所在包名。 */
    private const val EXPORT_PACKAGE = "com.inkread.weekread"

    /** 导出组件相对包名的类名。 */
    private const val EXPORT_CLASS = "$EXPORT_PACKAGE.bridge.ExportActivity"

    /** 迁移包写入位置，相对缓存目录。 */
    private const val PACK_RELATIVE = "weread-import/weread_pack.bin"

    /** 导出动作，导出端按动作分派到数据导出分支。 */
    private const val ACTION_EXPORT = "$EXPORT_PACKAGE.action.EXPORT"

    /** 目标文件 Uri 字段名。 */
    private const val EXTRA_TARGET_URI = "target_uri"

    /** 迁移密钥字段名。 */
    private const val EXTRA_BRIDGE_KEY = "bridge_key"

    /** 一次性令牌字段名。 */
    private const val EXTRA_BRIDGE_TOKEN = "bridge_token"

    /** 失败码字段名。 */
    private const val EXTRA_ERROR_MESSAGE = "error_message"

    /** 当前挂接的宿主 Activity，弱引用避免占用界面生命周期。 */
    private var activityRef: WeakReference<Activity>? = null

    /** 挂起请求的结束回调，同一时刻只保留一次迁移。 */
    private var pending: ((String) -> Unit)? = null

    /** 挂起请求使用的密钥，与导出端本次会话配对。 */
    private var pendingKey: ByteArray? = null

    /** 挂起请求的目标文件。 */
    private var pendingFile: File? = null

    /** 挂接宿主 Activity。 */
    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /** 解除挂接并清理未完成请求。 */
    fun detach() {
        activityRef = null
        clearPending()
    }

    /**
     * 发起一次迁移。
     *
     * 同步或迁移进行中时拒绝重入；已有数据会先备份再替换；
     * 导出组件未安装时回调说明并返回 false，由设置页提示用户安装。
     * Android 组件准备阶段的异常种类不固定，所有失败都必须释放迁移入口。
     */
    @Suppress("TooGenericExceptionCaught")
    override fun requestImport(onFinished: (String) -> Unit): Boolean {
        val activity = activityRef?.get()
        val reason = rejection(activity)
        if (activity == null || reason != null) {
            onFinished(reason ?: "当前页面无法发起迁移")
            return false
        }
        return if (!WereadDataGate.tryBeginImport()) {
            onFinished("数据同步或迁移正在进行，请稍后重试")
            false
        } else {
            try {
                launch(activity, onFinished)
            } catch (error: Exception) {
                clearPending()
                onFinished("无法发起迁移：${error.message ?: "未知错误"}")
                false
            }
        }
    }

    /**
     * 检查发起条件，返回不能发起的原因。
     *
     * @param activity 宿主 Activity，为空表示页面尚未挂接
     * @return 不能发起时返回说明文本，可以发起时返回 null
     */
    private fun rejection(activity: Activity?): String? =
        when {
            activity == null -> "当前页面无法发起迁移"
            pending != null -> "迁移正在进行，请稍后重试"
            HostWereadSync.isRunning() -> "数据同步正在进行，请稍后重试"
            else -> null
        }

    /**
     * 拉起导出组件并登记挂起状态。
     *
     * @param activity 宿主 Activity
     * @param onFinished 结果回调
     * @return 成功拉起返回 true，组件缺失时返回 false
     */
    private fun launch(
        activity: Activity,
        onFinished: (String) -> Unit,
    ): Boolean {
        val file = File(activity.cacheDir, PACK_RELATIVE)
        file.parentFile?.mkdirs()
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        // 目标 Uri 放在 extra 中，系统不会随 Intent 自动授予权限，需显式授予写入权限
        runCatching {
            activity.grantUriPermission(
                EXPORT_PACKAGE,
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val intent =
            Intent().apply {
                component = ComponentName(EXPORT_PACKAGE, EXPORT_CLASS)
                action = ACTION_EXPORT
                // 导出端以 getStringExtra 读取目标地址，此处必须传字符串形式
                putExtra(EXTRA_TARGET_URI, uri.toString())
                putExtra(EXTRA_BRIDGE_KEY, Base64.encodeToString(key, Base64.NO_WRAP))
                putExtra(EXTRA_BRIDGE_TOKEN, UUID.randomUUID().toString())
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        pending = onFinished
        pendingKey = key
        pendingFile = file
        return runCatching {
            activity.startActivityForResult(intent, REQUEST_IMPORT)
            true
        }.getOrElse { error ->
            InkLog.d("OctoberRain", "weread import launch failed: ${error.message}")
            clearPending()
            onFinished("未找到微读墨记，无法迁移")
            false
        }
    }

    /**
     * 转发宿主 Activity 的 onActivityResult，返回 true 表示本对象已消费。
     *
     * 成功后读取迁移包并交给编排处理，结果以提示形式给出；失败按导出端错误码说明原因。
     */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQUEST_IMPORT) return false
        val callback = pending
        if (callback != null) {
            val message =
                try {
                    settle(resultCode, data)
                } finally {
                    clearPending()
                }
            callback(message)
        }
        return true
    }

    /**
     * 结算本次导出结果，返回可展示的说明文本。
     *
     * @param resultCode 导出端返回的结果码
     * @param data 导出端附加的数据
     */
    private fun settle(
        resultCode: Int,
        data: Intent?,
    ): String {
        if (resultCode != Activity.RESULT_OK) {
            return data?.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "迁移已取消"
        }
        return runImportReady()
    }

    /** 结果成功时执行迁移，会话上下文缺失按取消处理。 */
    private fun runImportReady(): String {
        val key = pendingKey
        val file = pendingFile
        val activity = activityRef?.get()
        return if (key != null && file != null && activity != null) {
            runImport(activity, file, key)
        } else {
            "迁移已取消"
        }
    }

    /**
     * 执行迁移并返回可展示的结果说明。
     *
     * @param activity 宿主 Activity，用于取文件目录与提示
     * @param file 导出端写入的迁移包
     * @param key 本次迁移密钥
     */
    private fun runImport(
        activity: Activity,
        file: File,
        key: ByteArray,
    ): String {
        if (!file.exists() || file.length() <= 0L) return "迁移包为空，请重试"
        val coordinator =
            WereadMigrationCoordinator(
                AppServices.config,
                WereadImportStore(
                    File(activity.filesDir, "weread"),
                    File(activity.filesDir, "weread-backup"),
                ),
            )
        val outcome =
            runCatching { coordinator.import(file.readBytes(), key) }.getOrElse { error ->
                InkLog.d("OctoberRain", "weread import failed: ${error.message}")
                WereadMigrationOutcome.Failure(
                    com.shuiyu.octoberrain.core.data.wereadimport.WereadImportError.WRITE_FAILED,
                    error.message ?: "迁移失败",
                )
            }
        file.delete()
        return when (outcome) {
            is WereadMigrationOutcome.Success ->
                if (outcome.skipped) {
                    "数据已迁移，跳过重复导入"
                } else {
                    HostWereadSync.notifyDataChanged()
                    "迁移完成：书籍 ${outcome.counts.books}，笔记 ${outcome.counts.notes}，账单 ${outcome.counts.bills}"
                }
            is WereadMigrationOutcome.Failure -> "迁移失败：${outcome.message}"
        }
    }

    /** 清理挂起请求状态。 */
    private fun clearPending() {
        WereadDataGate.endImport()
        pending = null
        pendingKey = null
        pendingFile = null
    }
}
