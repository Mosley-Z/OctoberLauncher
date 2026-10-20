package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.content.Context
import com.inkread.weekread.kernel.ShelfBook

/**
 * 选记半屏。
 *
 * 版式按微读墨记 `feature/NotePickView.java`：底板占屏高 0.70，标题行 44dp，搜索行 54dp，
 * 行高 44dp，左右内边距 16dp。首行为「全部书籍」，其下为书架书目，可按书名或作者过滤。
 *
 * @param context 创建视图的上下文
 * @param books 可选书目
 */
internal class WereadNotePickSheet(
    context: Context,
    private val books: List<ShelfBook>,
) : WereadPickSheet(context) {
    override val sheetRatio: Float = 0.70f
    override val titleHeightDp: Float = 44f
    override val rowHeightDp: Float = 44f
    override val sheetTitle: String = "选择书籍"
    override val hasSearch: Boolean = true
    override val searchHint: String = "搜索书名"

    override fun populate(keyword: String) {
        rows.add(PickRow("", "全部书籍", "关注所有书的划线"))
        val filtered =
            books.filter { book ->
                keyword.isEmpty() || book.title.contains(keyword) || book.author.contains(keyword)
            }
        filtered.forEach { book ->
            rows.add(PickRow(book.bookId, book.title, book.author))
        }
    }

    /** 展示选记半屏。 */
    companion object {
        /**
         * 展示选记半屏。
         *
         * @param context 创建视图的上下文
         * @param books 可选书目
         * @param selectedBookId 当前筛选的书目编号，空串表示全部书籍
         * @param onPick 选定回调，参数为书目编号，空串表示全部书籍
         */
        fun show(
            context: Context,
            books: List<ShelfBook>,
            selectedBookId: String,
            onPick: (String) -> Unit,
        ) {
            val sheet = WereadNotePickSheet(context, books)
            sheet.onPick = onPick
            sheet.show(selectedBookId)
        }
    }
}
