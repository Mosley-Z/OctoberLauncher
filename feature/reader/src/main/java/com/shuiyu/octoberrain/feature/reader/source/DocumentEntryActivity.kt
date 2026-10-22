package com.shuiyu.octoberrain.feature.reader.source

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import com.shuiyu.octoberrain.core.common.ReaderFailure

/**
 * 外部文档入口：只接收带读取授权的 content URI，并转交同 UID 的宿主打开。
 *
 * 支持 ACTION_VIEW 与 ACTION_SEND 的单文件投递；ACTION_SEND 只读取 EXTRA_STREAM 或
 * ClipData，不把 EXTRA_TEXT 正文当作文件。拒绝场景在结束前以 Toast 说明具体原因。
 */
class DocumentEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (val entry = ExternalEntryPolicy.resolve(intent.toFacts())) {
            is ExternalEntry.Single -> forward(entry)
            is ExternalEntry.Rejected -> showRejection(entry.failure)
        }
        finish()
    }

    /**
     * 把当前 Intent 转换为与 Android 类型无关的事实对象。
     *
     * @return 动作、候选 URI、MIME 与授权标志
     */
    private fun Intent.toFacts(): ExternalIntentFacts {
        val clipUris =
            clipData?.let { clip ->
                (0 until clip.itemCount).mapNotNull { index -> clip.getItemAt(index).uri?.toString() }
            } ?: emptyList()
        return ExternalIntentFacts(
            action = action,
            dataUri = data?.toString(),
            streamUri = streamUri(),
            clipUris = clipUris,
            type = type,
            readGranted = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
            persistable = flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0,
        )
    }

    /**
     * 读取 EXTRA_STREAM 中的 URI。
     *
     * 只有 SEND 与 SEND_MULTIPLE 携带 EXTRA_STREAM，其余动作不读取该参数，避免对无关数据做
     * 类型转换。EXTRA_STREAM 可能是 ArrayList、Intent 或其他 Parcelable，类型不符时按缺少
     * URI 处理，异常在内部消化，不向调用方抛出。
     *
     * @return EXTRA_STREAM 的 URI 字符串，未提供或类型不符时为 null
     */
    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): String? {
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
        val value: Any? =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getParcelableExtra(Intent.EXTRA_STREAM, Parcelable::class.java)
                } else {
                    getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }.getOrNull()
        return (value as? Uri)?.toString()
    }

    /**
     * 取得持久授权后按既有约定向宿主转发 ACTION_VIEW。
     *
     * 只有读取授权与持久授权同时存在时才尝试 takePersistableUriPermission；提供方拒绝时
     * 保持临时授权继续打开，不影响本次阅读。
     *
     * @param entry 已确认的单个文档
     */
    private fun forward(entry: ExternalEntry.Single) {
        // 只记录 scheme、MIME 与授权状态，不记录路径与查询参数，避免日志泄露文件名或令牌。
        val scheme = Uri.parse(entry.uri).scheme
        Log.i(TAG, "forward scheme=$scheme mime=${entry.mime?.take(MAX_LOG_MIME)} persistable=${entry.persistable}")
        val uri = Uri.parse(entry.uri)
        if (entry.persistable) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val forwarded =
            Intent(Intent.ACTION_VIEW)
                .setClassName(packageName, MAIN_ACTIVITY)
                .setDataAndType(uri, entry.mime)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        (intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),
                ).apply { clipData = intent.clipData }
        runCatching { startActivity(forwarded) }.onFailure {
            Toast.makeText(this, "无法打开文档，请从文件管理器重试", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 按失败原因提示用户。
     *
     * @param failure 策略给出的失败分类
     */
    private fun showRejection(failure: ReaderFailure) {
        Log.i(TAG, "reject=${failure.name}")
        val message =
            when (failure) {
                ReaderFailure.MISSING -> "文件不存在或未提供文件"
                ReaderFailure.PERMISSION -> "缺少读取授权"
                ReaderFailure.UNSUPPORTED -> "一次只能打开一个文件"
                else -> "无法打开文件"
            }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "DocumentEntry"
        const val MAIN_ACTIVITY = "com.shuiyu.octoberrain.MainActivity"
        const val MAX_LOG_MIME = 64
    }
}
