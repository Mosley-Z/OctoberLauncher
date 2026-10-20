package com.shuiyu.octoberrain.feature.files.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.device.DeviceRuntime
import com.shuiyu.octoberrain.feature.files.data.FileItem
import com.shuiyu.octoberrain.feature.files.data.FilePrefs
import com.shuiyu.octoberrain.feature.files.data.StorageAccess
import java.io.File
import java.util.Locale

/**
 * 系统文件选择请求入口。
 *
 * 供其他应用以 GET_CONTENT 或 OPEN_DOCUMENT 请求文件。选择页复用文件页的分页容器，
 * 逐行列出目录与可接收的文件；选中文件后把内容以 [FileProvider] 的 Uri 回传，
 * 并按调用方包名授予读取权限。请求方未限定类型时接受全部文件。
 */
class PickEntryActivity : Activity() {
    private lateinit var storage: StorageAccess
    private lateinit var prefs: FilePrefs
    private var pane: PickPane? = null
    private val acceptedTypes: List<String> by lazy { collectMimeTypes() }

    /**
     * 建立选择页。
     *
     * 参数 savedInstanceState 为系统恢复状态，本入口不保留额外状态。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = StorageAccess(this)
        prefs = FilePrefs(this)
        if (!storage.hasAccess()) {
            Toast.makeText(this, "需要存储访问权限", Toast.LENGTH_SHORT).show()
            cancel()
            return
        }
        val textScale =
            runCatching { AppServices.config.state.value.appearance.textScale }
                .getOrDefault(1f)
        val tokens = FileTokens(profile = DeviceRuntime.profile, textScale = textScale)
        val root =
            File(prefs.lastPath.takeIf { storage.isReadable(it) } ?: storage.externalRoot())
        val created =
            PickPane(
                context = this,
                tokens = tokens,
                prefs = prefs,
                root = root,
                accepts = ::accepts,
                onPick = ::finishWith,
                onCancel = ::cancel,
            )
        pane = created
        InkLog.d(TAG, "文件选择请求类型：${acceptedTypes.joinToString()}")
        setContentView(
            created,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val current = pane
        if (current == null) {
            cancel()
            return
        }
        current.goUp()
    }

    /** 合并请求类型与 EXTRA_MIME_TYPES，空列表表示接受全部文件。 */
    private fun collectMimeTypes(): List<String> {
        val types = linkedSetOf<String>()
        intent?.type?.takeIf { it.isNotBlank() }?.let(types::add)
        intent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.forEach { type ->
            if (type.isNotBlank()) types.add(type)
        }
        return types.toList()
    }

    /** 判断文件名对应的 MIME 是否被请求方接受。 */
    private fun accepts(name: String): Boolean {
        if (acceptedTypes.isEmpty() || acceptedTypes.contains("*/*")) return true
        val mime = mimeOf(name)
        return acceptedTypes.any { pattern ->
            when {
                pattern.endsWith("/*") -> mime.startsWith(pattern.removeSuffix("*"))
                pattern == mime -> true
                else -> false
            }
        }
    }

    /** 回传选中文件的 Uri，并授予调用方读取权限。 */
    private fun finishWith(item: FileItem) {
        val file = File(item.path)
        val uri: Uri =
            runCatching { FileProvider.getUriForFile(this, "$packageName.files", file) }
                .getOrElse {
                    InkLog.w(TAG, "生成分享地址失败：${it.message}")
                    Toast.makeText(this, "无法分享该文件", Toast.LENGTH_SHORT).show()
                    return
                }
        val result =
            Intent().apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val caller = callingPackage
        if (caller != null) {
            runCatching { grantUriPermission(caller, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        setResult(RESULT_OK, result)
        finish()
    }

    /** 返回取消结果。 */
    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun mimeOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "jpg", "jpeg" -> "image/jpeg"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }

    private companion object {
        private const val TAG = "文件选择入口"
    }
}
