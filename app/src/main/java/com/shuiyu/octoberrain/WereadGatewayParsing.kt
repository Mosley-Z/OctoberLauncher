package com.shuiyu.octoberrain

import com.shuiyu.octoberrain.core.wereadsync.WereadAssetBook
import com.shuiyu.octoberrain.core.wereadsync.WereadNoteBatch
import com.shuiyu.octoberrain.core.wereadsync.WereadNoteFile
import com.shuiyu.octoberrain.core.wereadsync.WereadShelfBook
import org.json.JSONArray
import org.json.JSONObject

/**
 * 网关响应到同步模型的解析。
 *
 * 解析只做字段映射与容错，不发起网络请求；原始文本结构不合法时返回 null，
 * 调用方据此跳过该项并保留既有数据。
 *
 * 函数数量随网关各类响应（书架、统计、资产、笔记概览、划线）分别展开，
 * 拆成多个对象反而割裂同一份字段映射规则，因此对函数数量度量局部豁免。
 */
@Suppress("TooManyFunctions")
internal object WereadGatewayParsing {
    /**
     * 解析书架响应。
     *
     * @param raw 网关返回的原始 JSON 文本
     * @return 书架条目，缺少 books 数组时返回 null
     */
    fun parseShelf(raw: String?): List<WereadShelfBook>? =
        booksOf(raw)?.let { books -> items(books).mapNotNull { bookOf(it) } }

    /**
     * 解析阅读资产响应。
     *
     * @param raw 网关返回的原始 JSON 文本
     * @return 资产条目，缺少 books 数组时返回 null
     */
    fun parseAssets(raw: String?): List<WereadAssetBook>? =
        booksOf(raw)?.let { books -> items(books).mapNotNull { assetOf(it) } }

    /**
     * 解析笔记概览。
     *
     * @param raw 网关返回的原始 JSON 文本
     * @return 索引文本与逐篇文件，缺少 books 数组时返回 null
     */
    fun parseNotes(raw: String?): WereadNoteBatch? =
        raw?.let { text ->
            booksOf(text)?.let { books ->
                WereadNoteBatch(indexJson = text, notes = items(books).mapNotNull { noteFile(it) })
            }
        }

    /** 读取 books 数组，文本不合法或缺少数组时返回 null。 */
    private fun booksOf(raw: String?): JSONArray? =
        raw?.let { text -> runCatching { JSONObject(text) }.getOrNull() }?.optJSONArray("books")

    /** 把数组元素转为对象列表，跳过非对象元素。 */
    private fun items(books: JSONArray): List<JSONObject> =
        (0 until books.length()).mapNotNull { index -> books.optJSONObject(index) }

    /** 把单篇笔记转为落盘文件，书目标识缺失时返回 null。 */
    private fun noteFile(item: JSONObject): WereadNoteFile? {
        val bookId = item.optString("bookId")
        return if (bookId.isBlank()) null else WereadNoteFile("nt_$bookId.json", item.toString())
    }

    /**
     * 从笔记概览里取需要补划线的书目标识。
     *
     * 概览接口只给书目与计数，逐条划线要按书再查；只取笔记数大于零的书，
     * 并按上限截断，避免一次同步发过多请求。
     *
     * @param raw 笔记概览原始文本
     * @param limit 最多返回的书目数
     * @return 书目标识列表，文本不合法时为空表
     */
    fun noteBookIds(
        raw: String?,
        limit: Int,
    ): List<String> =
        booksOf(raw)
            ?.let { books ->
                items(books)
                    .filter { it.optInt("noteCount", 0) > 0 }
                    .map { it.optString("bookId") }
                    .filter { it.isNotBlank() }
                    .take(limit)
            }.orEmpty()

    /**
     * 把书目元数据与划线合并成内核可读的逐书文件。
     *
     * 内核从 `nt_<bookId>.json` 的 `marks` 数组读划线原文，字段名与网关响应一致，
     * 因此这里只做裁剪而不改写键名。
     *
     * @param book 笔记概览里的书对象
     * @param marks 划线数组，可为空
     * @return 落盘文件，书目标识缺失时返回 null
     */
    fun noteFileWithMarks(
        book: JSONObject,
        marks: JSONArray?,
    ): WereadNoteFile? {
        val bookId = book.optString("bookId")
        if (bookId.isBlank()) return null
        val payload = JSONObject(book.toString())
        if (marks != null) payload.put("marks", marks)
        return WereadNoteFile("nt_$bookId.json", payload.toString())
    }

    /** 从笔记概览文本里取出书对象，供补划线时复用元数据。 */
    fun noteBooks(raw: String?): List<JSONObject> = booksOf(raw)?.let { items(it) }.orEmpty()

    /**
     * 解析划线响应。
     *
     * @param raw 划线接口返回的原始文本
     * @return 划线数组，缺少 updated 字段时返回 null
     */
    fun parseMarks(raw: String?): JSONArray? =
        raw?.let { text -> runCatching { JSONObject(text) }.getOrNull() }?.optJSONArray("updated")

    /** 把单本书映射为同步模型，书目标识缺失时返回 null。 */
    private fun bookOf(item: JSONObject): WereadShelfBook? {
        val bookId = item.optString("bookId").ifBlank { item.optString("book_id") }
        return if (bookId.isBlank()) {
            null
        } else {
            val finishTime = item.optLong("finishTime", 0L)
            WereadShelfBook(
                bookId = bookId,
                title = item.optString("title").ifBlank { "未命名书籍" },
                author = item.optString("author"),
                coverUrl = item.optString("cover"),
                readingSeconds = item.optInt("readingTime", item.optInt("readTimeSec", 0)),
                progress = percentOf(item.optInt("progress", 0)),
                finished = finishTime > 0L || item.optInt("finishReading", 0) == 1,
                finishTimeMillis = millisOf(finishTime),
            )
        }
    }

    /** 把单条阅读记录映射为资产模型，价格由本地清单补录。 */
    private fun assetOf(item: JSONObject): WereadAssetBook? {
        val bookId = item.optString("bookId").ifBlank { item.optString("id") }
        return if (bookId.isBlank()) {
            null
        } else {
            WereadAssetBook(
                bookId = bookId,
                title = item.optString("title").ifBlank { "未命名书籍" },
                author = item.optString("author"),
                readingSeconds = item.optInt("readTime", item.optInt("readTimeSec", 0)),
                priceFen = 0,
                paidFen = 0,
                progress = percentOf(item.optInt("progress", 0)),
                finished = item.optLong("finishTime", 0L) > 0L,
            )
        }
    }

    /** 进度可能是百分比或千分比，超过一百时按千分比换算。 */
    private fun percentOf(raw: Int): Int = if (raw > 100) raw / 10 else raw

    /** 完成时刻兼容秒与毫秒两种单位。 */
    private fun millisOf(raw: Long): Long = if (raw in 1L..99_999_999_999L) raw * 1000 else raw
}
