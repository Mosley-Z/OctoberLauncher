package com.shuiyu.octoberrain.feature.reader.source

import com.shuiyu.octoberrain.core.common.ReaderFailure

/**
 * 外部 Intent 中与文档入口判定有关的事实。
 *
 * 全部字段只使用字符串与布尔值，便于在 JVM 单元测试中直接构造，不依赖 Android 类型。
 *
 * @property action Intent 动作
 * @property dataUri Intent.data 的字符串形式
 * @property streamUri EXTRA_STREAM 的字符串形式
 * @property clipUris Intent.clipData 中各项的 URI 字符串
 * @property type Intent 声明的 MIME
 * @property readGranted 是否带有读取 URI 授权标志
 * @property persistable 是否带有持久 URI 授权标志
 */
internal data class ExternalIntentFacts(
    val action: String?,
    val dataUri: String?,
    val streamUri: String?,
    val clipUris: List<String>,
    val type: String?,
    val readGranted: Boolean,
    val persistable: Boolean,
)

/** 外部入口的解析结果。 */
internal sealed interface ExternalEntry {
    /**
     * 可以转发的单个文档。
     *
     * @property uri 已确认 scheme 为 content 的 URI 字符串
     * @property mime Intent 声明的 MIME，可能为空，由调用方继续按文件头判定
     * @property persistable 读取授权与持久授权同时存在时为真
     */
    data class Single(
        val uri: String,
        val mime: String?,
        val persistable: Boolean,
    ) : ExternalEntry

    /**
     * 无法转发的入口。
     *
     * @property failure 可直接展示给用户的失败分类
     */
    data class Rejected(
        val failure: ReaderFailure,
    ) : ExternalEntry
}

/**
 * 外部单文件入口策略。
 *
 * 支持 ACTION_VIEW 与 ACTION_SEND 的单文档场景：VIEW 使用 data，SEND 使用 EXTRA_STREAM。
 * 主文档存在时 ClipData 一律视为附带读授权，不参与文件选择；ACTION_SEND_MULTIPLE 与未知
 * 动作拒绝。缺少主文档时使用首个 ClipData 项，多个 ClipData 项按多文件拒绝。非 content
 * scheme 映射为 PERMISSION，缺少 URI 映射为 MISSING。本策略只处理字符串事实，不访问
 * ContentResolver。
 */
internal object ExternalEntryPolicy {
    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val CONTENT_SCHEME = "content"

    /**
     * 解析外部入口事实。
     *
     * 主文档来自 VIEW 的 data 或 SEND 的 EXTRA_STREAM，存在时 ClipData 只作为附带读授权
     * （例如 Markdown 文档与父目录的图片访问授权），不参与文件选择；多文件的表达方式为
     * ACTION_SEND_MULTIPLE，因此主文档存在时不因 ClipData 项数拒绝。缺少主文档时才使用
     * ClipData 的首个 URI，此时多个 URI 仍按多文件拒绝。
     *
     * @param facts 从 Intent 提取的动作、候选 URI、MIME 与授权标志
     * @return 可转发的单个 content URI，或带具体 [ReaderFailure] 的拒绝结果
     */
    fun resolve(facts: ExternalIntentFacts): ExternalEntry {
        if (facts.action != ACTION_VIEW && facts.action != ACTION_SEND) {
            return ExternalEntry.Rejected(ReaderFailure.UNSUPPORTED)
        }
        val primary = primaryOf(facts)?.takeIf { it.isNotBlank() }
        val clips = facts.clipUris.filter { it.isNotBlank() }.distinct()
        val picks = if (primary != null) listOf(primary) else clips
        val failure =
            when {
                picks.isEmpty() -> ReaderFailure.MISSING
                picks.size > 1 -> ReaderFailure.UNSUPPORTED
                schemeOf(picks.first()) != CONTENT_SCHEME -> ReaderFailure.PERMISSION
                !facts.readGranted -> ReaderFailure.PERMISSION
                else -> null
            }
        return if (failure != null) {
            ExternalEntry.Rejected(failure)
        } else {
            ExternalEntry.Single(
                picks.first(),
                facts.type,
                facts.persistable,
            )
        }
    }

    /**
     * 取得当前动作的主文档 URI。
     *
     * @param facts 外部入口事实
     * @return VIEW 的 data 或 SEND 的 EXTRA_STREAM；其他动作返回 null
     */
    private fun primaryOf(facts: ExternalIntentFacts): String? =
        when (facts.action) {
            ACTION_VIEW -> facts.dataUri
            ACTION_SEND -> facts.streamUri
            else -> null
        }

    /**
     * 提取 URI 的 scheme，用于拒绝 file、http 与裸路径。
     *
     * @param uri 待判定的 URI 字符串
     * @return 规范化的小写 scheme；无 scheme 或格式非法时返回 null
     */
    private fun schemeOf(uri: String): String? {
        val separator = uri.indexOf(':')
        if (separator <= 0) return null
        val scheme = uri.substring(0, separator)
        val valid =
            scheme.first().isLetter() &&
                scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
        return if (valid) scheme.lowercase() else null
    }
}
