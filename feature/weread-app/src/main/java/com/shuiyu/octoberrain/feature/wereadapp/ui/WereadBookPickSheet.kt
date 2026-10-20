package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.content.Context
import com.inkread.weekread.kernel.ShelfBook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 选书半屏。
 *
 * 版式按微读墨记 `feature/BookPickView.java`：底板占屏高 0.62，标题行 44dp，行高 46dp，
 * 左右内边距 16dp，不设搜索行。首行为「自动（最近在读）」，其下为书架书目，右侧标注最近
 * 阅读时间。
 *
 * @param context 创建视图的上下文
 * @param books 可选书目
 */
internal class WereadBookPickSheet(
    context: Context,
    private val books: List<ShelfBook>,
) : WereadPickSheet(context) {
    override val sheetRatio: Float = 0.62f
    override val titleHeightDp: Float = 44f
    override val rowHeightDp: Float = 46f
    override val sheetTitle: String = "选择「本书」"
    override val paddingXDp: Float = 16f
    override val emptyHint: String = "书架暂无书目"

    override fun populate(keyword: String) {
        val recent = if (books.firstOrNull()?.finished == true) "已读完" else "最近在读"
        rows.add(PickRow("", "自动（最近在读）", recent))
        books.forEach { book ->
            rows.add(PickRow(book.bookId, book.title, if (book.finished) formatTime(book.finishTime) else ""))
        }
    }

    /** 秒级时间戳转为「MM-dd HH:mm」，非正值返回空串。 */
    private fun formatTime(seconds: Long): String {
        if (seconds <= 0L) return ""
        return runCatching {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(seconds * 1000L))
        }.getOrDefault("")
    }

    /** 展示选书半屏。 */
    companion object {
        /**
         * 展示选书半屏。
         *
         * @param context 创建视图的上下文
         * @param books 可选书目
         * @param selectedBookId 当前书目编号，空串表示自动
         * @param onPick 选定回调，参数为书目编号，空串表示自动
         */
        fun show(
            context: Context,
            books: List<ShelfBook>,
            selectedBookId: String,
            onPick: (String) -> Unit,
        ) {
            val sheet = WereadBookPickSheet(context, books)
            sheet.onPick = onPick
            sheet.show(selectedBookId)
        }
    }
}
