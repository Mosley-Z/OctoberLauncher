package com.shuiyu.octoberrain.feature.reader.search

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.widget.EditText
import com.shuiyu.octoberrain.core.common.AppServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal interface SearchHost {
    val context: Context
    val searchScope: CoroutineScope
    val searchGeneration: Int
    val searchVisible: Boolean

    fun searchBusy(): Boolean

    fun setSearchBusy(value: Boolean)

    fun setSearchJob(job: Job)

    fun cancelSearchJob()

    suspend fun scanSearch(
        query: String,
        offset: Int,
    ): SearchPage

    suspend fun showSearchHit(
        hit: SearchHit,
        query: String,
    )

    fun showSearchError(error: Exception)
}

/** 一个查询会话只保留64条结果；跨窗口重新扫描，计数和已加载DOM无关。 */
@Suppress("TooGenericExceptionCaught") // 异步UI边界统一显示错误，取消原样传播。
internal class ReaderSearchUi(
    private val host: SearchHost,
) {
    private var query = ""
    private var page: SearchPage? = null
    private var result: AlertDialog? = null
    private var ordinal = 0

    fun close() {
        result?.dismiss()
        result = null
        page = null
    }

    fun show() {
        if (host.searchBusy()) return
        val input =
            EditText(host.context).apply {
                setSingleLine(true)
                filters = arrayOf(InputFilter.LengthFilter(256))
                setText(query)
            }
        AlertDialog
            .Builder(host.context)
            .setTitle("全文搜索")
            .setMessage("检索全文；表格按匹配单元格计数")
            .setView(input)
            .setNegativeButton("返回", null)
            .setPositiveButton("查找") { _, _ ->
                val value = input.text.toString()
                if (value.isNotEmpty()) {
                    query = value
                    page = null
                    move(0)
                }
            }.show()
    }

    private fun move(target: Int) {
        val mark = host.searchGeneration
        val word = query
        host.setSearchBusy(true)
        val progress =
            AlertDialog
                .Builder(host.context)
                .setMessage("正在分块统计全文结果…")
                .setNegativeButton("取消") { _, _ -> host.cancelSearchJob() }
                .setOnCancelListener { host.cancelSearchJob() }
                .show()
        host.setSearchJob(
            host.searchScope.launch {
                try {
                    val cached = page?.takeIf { target in it.offset until it.offset + it.hits.size }
                    val found =
                        cached ?: host.scanSearch(word, target / SearchCollector.PAGE_SIZE * SearchCollector.PAGE_SIZE)
                    if (current(mark)) display(found, target, word)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (current(mark)) host.showSearchError(failure)
                } finally {
                    progress.dismiss()
                    if (current(mark)) host.setSearchBusy(false)
                }
            },
        )
    }

    private suspend fun display(
        found: SearchPage,
        target: Int,
        word: String,
    ) {
        val hit = found.hits.getOrNull(target - found.offset)
        if (hit == null) {
            AppServices.router.toast("全文没有匹配结果")
        } else {
            host.showSearchHit(hit, word)
            page = found
            ordinal = target
            showResult(hit, found.total)
        }
    }

    private fun showResult(
        hit: SearchHit,
        total: Int,
    ) {
        result?.dismiss()
        val dialog =
            AlertDialog
                .Builder(host.context)
                .setTitle("第${ordinal + 1}/${total}条结果")
                .setMessage(hit.snippet)
                .setNeutralButton("关闭", null)
                .setNegativeButton("上一个", null)
                .setPositiveButton("下一个", null)
                .create()
        result = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                isEnabled = ordinal > 0
                setOnClickListener {
                    dialog.dismiss()
                    move(ordinal - 1)
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                isEnabled = ordinal + 1 < total
                setOnClickListener {
                    dialog.dismiss()
                    move(ordinal + 1)
                }
            }
        }
        dialog.show()
    }

    private fun current(mark: Int): Boolean = host.searchVisible && mark == host.searchGeneration
}
