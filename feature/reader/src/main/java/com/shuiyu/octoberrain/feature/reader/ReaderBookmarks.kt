package com.shuiyu.octoberrain.feature.reader

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.widget.EditText
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.session.BookmarkRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 书签界面所需的宿主能力，避免对话框代码直接持有阅读页全部状态。 */
internal interface BookmarkHost {
    /** 对话框所在上下文。 */
    val context: Context

    /** 当前会话，暂停或释放后为空。 */
    val bookmarkSession: ReaderSession?

    /** 页面是否可见。 */
    val bookmarkVisible: Boolean

    /** 当前会话代次，用于丢弃过期回调。 */
    val bookmarkGeneration: Int

    /** 页面协程作用域。 */
    val bookmarkScope: CoroutineScope

    /** 是否已有进行中的动作。 */
    fun bookmarkBusy(): Boolean

    /** 记录跳转书签的编码，按来源重新打开。 */
    fun setBookmarkCharset(value: String?)

    /** 按书签定位重新打开。 */
    fun openBookmark(locator: ReaderLocator)

    /** 登记进行中的动作，供生命周期统一取消。 */
    fun setBookmarkAction(job: Job?)

    /** 展示异常文案。 */
    fun showBookmarkError(error: Exception)
}

/**
 * 书签列表与编辑对话框。
 *
 * 列出、重命名与删除只修改本地书签，不读取或改写源文件；
 * 跳转沿用阅读页的定位流程，编码随书签记录恢复。
 * 存储层异常在此边界归一为提示，不区分具体异常类型，避免对话框把桌面带崩。
 */
@Suppress("TooGenericExceptionCaught")
internal class ReaderBookmarks(
    private val host: BookmarkHost,
) {
    /** 展示书签列表，空列表时提示而非弹空对话框。 */
    fun show() {
        val reader = host.bookmarkSession ?: return
        if (host.bookmarkBusy()) return
        val mark = host.bookmarkGeneration
        host.setBookmarkAction(
            host.bookmarkScope.launch {
                try {
                    val marks = withContext(Dispatchers.IO) { reader.bookmarkRows() }
                    if (host.bookmarkVisible && mark == host.bookmarkGeneration) {
                        if (marks.isEmpty()) {
                            AppServices.router.toast("暂无书签")
                        } else {
                            AlertDialog
                                .Builder(host.context)
                                .setTitle("书签")
                                .setItems(marks.map { it.label }.toTypedArray()) { _, index ->
                                    actions(marks[index])
                                }.show()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (host.bookmarkVisible && mark == host.bookmarkGeneration) host.showBookmarkError(failure)
                }
            },
        )
    }

    /** 书签条目操作：跳转、重命名与删除，均不修改源文件。 */
    private fun actions(row: BookmarkRow) {
        val items =
            listOf(
                "跳转" to {
                    host.setBookmarkCharset((row.locator as? ReaderLocator.Text)?.charset)
                    host.openBookmark(row.locator)
                },
                "重命名" to { rename(row) },
                "删除" to { delete(row) },
            )
        AlertDialog
            .Builder(host.context)
            .setTitle(row.label)
            .setItems(items.map { it.first }.toTypedArray()) { _, index -> items[index].second() }
            .show()
    }

    /** 重命名书签，预填原名称并限制最多八十个字符。 */
    private fun rename(row: BookmarkRow) {
        val input =
            EditText(host.context).apply {
                setText(row.label)
                setSelection(text.length)
                filters = arrayOf(InputFilter.LengthFilter(80))
            }
        AlertDialog
            .Builder(host.context)
            .setTitle("重命名书签")
            .setView(input)
            .setNegativeButton("返回", null)
            .setPositiveButton("保存") { _, _ ->
                val reader = host.bookmarkSession ?: return@setPositiveButton
                val mark = host.bookmarkGeneration
                host.setBookmarkAction(
                    host.bookmarkScope.launch {
                        try {
                            val renamed =
                                withContext(Dispatchers.IO) {
                                    reader.renameBookmark(row.id, input.text.toString())
                                }
                            if (host.bookmarkVisible && mark == host.bookmarkGeneration) {
                                AppServices.router.toast(if (renamed) "书签已重命名" else "书签重命名失败")
                            }
                        } catch (failure: Exception) {
                            InkLog.w("阅读器", "书签重命名失败：${failure.javaClass.simpleName}")
                            if (host.bookmarkVisible && mark == host.bookmarkGeneration) {
                                AppServices.router.toast("书签重命名失败")
                            }
                        }
                    },
                )
            }.show()
    }

    /** 删除书签前二次确认。 */
    private fun delete(row: BookmarkRow) {
        AlertDialog
            .Builder(host.context)
            .setTitle("删除书签")
            .setMessage(row.label)
            .setNegativeButton("返回", null)
            .setPositiveButton("删除") { _, _ ->
                val reader = host.bookmarkSession ?: return@setPositiveButton
                val mark = host.bookmarkGeneration
                host.setBookmarkAction(
                    host.bookmarkScope.launch {
                        try {
                            val deleted = withContext(Dispatchers.IO) { reader.deleteBookmark(row.id) }
                            if (host.bookmarkVisible && mark == host.bookmarkGeneration) {
                                AppServices.router.toast(if (deleted) "书签已删除" else "书签删除失败")
                            }
                        } catch (failure: Exception) {
                            InkLog.w("阅读器", "书签删除失败：${failure.javaClass.simpleName}")
                            if (host.bookmarkVisible && mark == host.bookmarkGeneration) {
                                AppServices.router.toast("书签删除失败")
                            }
                        }
                    },
                )
            }.show()
    }
}
