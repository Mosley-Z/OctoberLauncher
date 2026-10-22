package com.shuiyu.octoberrain.feature.reader.source

import com.shuiyu.octoberrain.core.common.ReaderFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 外部入口策略的 JVM 单元测试：只覆盖字符串事实，不依赖 Android 运行时。 */
class ExternalEntryPolicyTest {
    // VIEW 使用 intent.data，并保留 MIME 与持久授权结果。
    @Test fun viewUsesDataUriWithTypeAndPersistableGrant() {
        val entry = ExternalEntryPolicy.resolve(facts(dataUri = DOCUMENT, persistable = true))
        val single = entry as? ExternalEntry.Single
        assertNotNull("VIEW 的 data URI 应可打开", single)
        assertEquals(DOCUMENT, single?.uri)
        assertEquals("application/pdf", single?.mime)
        assertTrue(single?.persistable == true)
    }

    // VIEW 缺少 data 时使用唯一的 ClipData 项。
    @Test fun viewFallsBackToFirstClipUri() {
        val entry = ExternalEntryPolicy.resolve(facts(clipUris = listOf(DOCUMENT)))
        val single = entry as? ExternalEntry.Single
        assertNotNull("VIEW 的首个 ClipData 项应可打开", single)
        assertEquals(DOCUMENT, single?.uri)
        assertFalse(single?.persistable == true)
    }

    // 主文档与 ClipData 同时出现时只打开主文档，ClipData 视为附带读授权。
    @Test fun viewKeepsDocumentWithClipGrants() {
        val withFolder =
            ExternalEntryPolicy.resolve(
                facts(dataUri = DOCUMENT, clipUris = listOf(FOLDER)),
            )
        assertEquals(DOCUMENT, (withFolder as? ExternalEntry.Single)?.uri)
        val withOtherDocument =
            ExternalEntryPolicy.resolve(
                facts(dataUri = DOCUMENT, clipUris = listOf(OTHER)),
            )
        assertEquals(
            "ClipData 只作为附带授权，不参与主文档选择",
            DOCUMENT,
            (withOtherDocument as? ExternalEntry.Single)?.uri,
        )
        val duplicated =
            ExternalEntryPolicy.resolve(
                facts(dataUri = DOCUMENT, clipUris = listOf(DOCUMENT, FOLDER)),
            )
        assertEquals(DOCUMENT, (duplicated as? ExternalEntry.Single)?.uri)
    }

    // 缺少主文档时多个 ClipData 项仍按多文件拒绝。
    @Test fun viewRejectsMultipleClipUrisWithoutData() {
        val entry =
            ExternalEntryPolicy.resolve(
                facts(clipUris = listOf(DOCUMENT, OTHER)),
            )
        assertEquals(ReaderFailure.UNSUPPORTED, (entry as? ExternalEntry.Rejected)?.failure)
    }

    // SEND 优先使用 EXTRA_STREAM，缺少 EXTRA_STREAM 时使用首个 ClipData 项。
    @Test fun sendUsesStreamOrFirstClipUri() {
        val stream = ExternalEntryPolicy.resolve(facts(action = ACTION_SEND, streamUri = STREAM))
        assertEquals(STREAM, (stream as? ExternalEntry.Single)?.uri)
        val clip = ExternalEntryPolicy.resolve(facts(action = ACTION_SEND, clipUris = listOf(DOCUMENT)))
        assertEquals(DOCUMENT, (clip as? ExternalEntry.Single)?.uri)
        val duplicated =
            ExternalEntryPolicy.resolve(
                facts(action = ACTION_SEND, streamUri = STREAM, clipUris = listOf(STREAM)),
            )
        assertEquals(STREAM, (duplicated as? ExternalEntry.Single)?.uri)
    }

    // SEND 的主文档为 EXTRA_STREAM，ClipData 中的目录授权不参与选择。
    @Test fun sendKeepsStreamWithClipFolderGrant() {
        val entry =
            ExternalEntryPolicy.resolve(
                facts(action = ACTION_SEND, streamUri = STREAM, clipUris = listOf(FOLDER, OTHER)),
            )
        assertEquals(STREAM, (entry as? ExternalEntry.Single)?.uri)
    }

    // SEND_MULTIPLE 与未知动作一律拒绝。
    @Test fun unsupportedActionsAreRejected() {
        val single = ExternalEntryPolicy.resolve(facts(action = ACTION_SEND_MULTIPLE, streamUri = STREAM))
        assertEquals(ReaderFailure.UNSUPPORTED, (single as? ExternalEntry.Rejected)?.failure)
        val multiple =
            ExternalEntryPolicy.resolve(
                facts(action = ACTION_SEND_MULTIPLE, clipUris = listOf(DOCUMENT, OTHER)),
            )
        assertEquals(ReaderFailure.UNSUPPORTED, (multiple as? ExternalEntry.Rejected)?.failure)
        val unknown = ExternalEntryPolicy.resolve(facts(action = ACTION_MAIN, dataUri = DOCUMENT))
        assertEquals(ReaderFailure.UNSUPPORTED, (unknown as? ExternalEntry.Rejected)?.failure)
    }

    // file、http 与裸路径都不属于可授权的外部入口。
    @Test fun nonContentUrisAreRejected() {
        val uris =
            listOf(
                "file:///sdcard/book.pdf",
                "http://example.com/book.pdf",
                "https://example.com/book.pdf",
                "/sdcard/book.pdf",
                "C:\\books\\book.pdf",
            )
        for (uri in uris) {
            val entry = ExternalEntryPolicy.resolve(facts(dataUri = uri))
            assertEquals(uri, ReaderFailure.PERMISSION, (entry as? ExternalEntry.Rejected)?.failure)
        }
    }

    // 没有候选 URI 或候选 URI 全为空白时按缺少文件处理。
    @Test fun missingUriIsRejected() {
        val empty = ExternalEntryPolicy.resolve(facts())
        assertEquals(ReaderFailure.MISSING, (empty as? ExternalEntry.Rejected)?.failure)
        val blank = ExternalEntryPolicy.resolve(facts(clipUris = listOf("   ")))
        assertEquals(ReaderFailure.MISSING, (blank as? ExternalEntry.Rejected)?.failure)
        val send = ExternalEntryPolicy.resolve(facts(action = ACTION_SEND))
        assertEquals(ReaderFailure.MISSING, (send as? ExternalEntry.Rejected)?.failure)
    }

    // 缺少读取授权时即使 URI 为 content 也拒绝。
    @Test fun readGrantIsRequired() {
        val view = ExternalEntryPolicy.resolve(facts(dataUri = DOCUMENT, readGranted = false))
        assertEquals(ReaderFailure.PERMISSION, (view as? ExternalEntry.Rejected)?.failure)
        val send =
            ExternalEntryPolicy.resolve(
                facts(action = ACTION_SEND, streamUri = STREAM, readGranted = false),
            )
        assertEquals(ReaderFailure.PERMISSION, (send as? ExternalEntry.Rejected)?.failure)
    }

    // 持久授权只在读取授权与持久标志同时存在时保留。
    @Test fun persistableRequiresReadGrant() {
        val persistable = ExternalEntryPolicy.resolve(facts(dataUri = DOCUMENT, persistable = true))
        assertTrue((persistable as? ExternalEntry.Single)?.persistable == true)
        val transient = ExternalEntryPolicy.resolve(facts(dataUri = DOCUMENT, persistable = false))
        assertFalse((transient as? ExternalEntry.Single)?.persistable == true)
        val ungranted =
            ExternalEntryPolicy.resolve(facts(dataUri = DOCUMENT, readGranted = false, persistable = true))
        assertEquals(ReaderFailure.PERMISSION, (ungranted as? ExternalEntry.Rejected)?.failure)
    }

    /**
     * 构造外部入口事实。
     *
     * @param action Intent 动作
     * @param dataUri Intent.data
     * @param streamUri EXTRA_STREAM
     * @param clipUris ClipData 中的 URI
     * @param readGranted 是否带读取授权
     * @param persistable 是否带持久授权
     * @return 与 Android 类型无关的事实对象
     */
    private fun facts(
        action: String? = ACTION_VIEW,
        dataUri: String? = null,
        streamUri: String? = null,
        clipUris: List<String> = emptyList(),
        readGranted: Boolean = true,
        persistable: Boolean = false,
    ): ExternalIntentFacts =
        ExternalIntentFacts(
            action = action,
            dataUri = dataUri,
            streamUri = streamUri,
            clipUris = clipUris,
            type = "application/pdf",
            readGranted = readGranted,
            persistable = persistable,
        )

    private companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val DOCUMENT = "content://com.example.documents/document/1"
        const val OTHER = "content://com.example.documents/document/2"
        const val STREAM = "content://com.example.documents/document/3"
        const val FOLDER = "content://com.example.documents/document"
    }
}
