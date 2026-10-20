package com.shuiyu.octoberrain

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.shuiyu.octoberrain.core.common.FilePicker
import java.lang.ref.WeakReference

/**
 * 基于系统文档选择器的文件选择实现。
 *
 * 宿主 Activity 在创建时挂接本对象，页面通过 AppServices.picker 发起选择请求，
 * 结果经 onActivityResult 回到这里并转发给等待中的回调。设备未提供系统文档选择器时
 * 立即返回 false，由调用方回退到模块内扫描。
 */
object HostFilePicker : FilePicker {
    /** 选择既有文件的请求码。 */
    const val REQUEST_PICK = 0x0F01

    /** 选择保存位置的请求码。 */
    const val REQUEST_SAVE = 0x0F02

    /** 选择目录的请求码。 */
    const val REQUEST_TREE = 0x0F03

    /** 当前挂接的宿主 Activity，弱引用避免占用界面生命周期。 */
    private var activityRef: WeakReference<Activity>? = null

    /** 等待结果的回调，同一时刻只保留一个请求。 */
    private var pending: ((Uri?) -> Unit)? = null

    /** 挂接宿主 Activity。 */
    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /** 解除挂接并清理未完成请求。 */
    fun detach() {
        activityRef = null
        pending = null
    }

    /**
     * 启动文件选择。
     *
     * @param mimeTypes 允许的 MIME 类型，空数组表示任意类型。
     * @param onPicked 结果回调，取消或宿主缺失时传 null。
     * @return 是否成功发起请求，设备缺少对应界面时返回 false。
     */
    override fun pick(
        mimeTypes: Array<String>,
        onPicked: (Uri?) -> Unit,
    ): Boolean {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimeTypes.isEmpty()) "*/*" else mimeTypes.first()
                if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return launch(REQUEST_PICK, intent, onPicked)
    }

    /**
     * 启动保存位置选择。
     *
     * @param fileName 建议文件名。
     * @param mimeType 保存类型。
     * @param onPicked 结果回调。
     * @return 是否成功发起请求。
     */
    override fun save(
        fileName: String,
        mimeType: String,
        onPicked: (Uri?) -> Unit,
    ): Boolean {
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, fileName)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        return launch(REQUEST_SAVE, intent, onPicked)
    }

    /**
     * 启动目录选择。
     *
     * @param onPicked 结果回调。
     * @return 是否成功发起请求。
     */
    override fun pickDirectory(onPicked: (Uri?) -> Unit): Boolean {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        return launch(REQUEST_TREE, intent, onPicked)
    }

    /** 转发宿主 Activity 的 onActivityResult，返回 true 表示本对象已消费。 */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQUEST_PICK && requestCode != REQUEST_SAVE && requestCode != REQUEST_TREE) {
            return false
        }
        val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
        val callback = pending
        pending = null
        callback?.invoke(uri)
        return true
    }

    /**
     * 通过宿主启动 Intent。
     *
     * 先确认系统中存在可处理该 Intent 的界面，再启动请求；无可处理界面或宿主缺失时
     * 立即回调 null 并返回 false，调用方据此回退到模块内扫描。
     */
    private fun launch(
        requestCode: Int,
        intent: Intent,
        onPicked: (Uri?) -> Unit,
    ): Boolean {
        val activity = activityRef?.get() ?: return false
        val resolvable =
            runCatching {
                intent.resolveActivity(activity.packageManager) != null
            }.getOrDefault(false)
        if (!resolvable) return false
        pending = onPicked
        return runCatching {
            activity.startActivityForResult(intent, requestCode)
            true
        }.getOrElse {
            pending = null
            false
        }
    }
}
