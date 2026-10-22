package com.shuiyu.octoberrain.core.data

import com.inkread.weekread.kernel.NoteItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/** 完整笔记索引与按书读取的内容；只缓存八本，避免墨水屏每帧展开全库。 */
class WereadNoteLibrary(
    private val root: File,
) {
    private val cache = linkedMapOf<String, Pair<String, List<NoteItem>>>()

    private var indexStamp = ""
    private var index: List<JsonObject> = emptyList()

    /** 索引原序就是选记列表顺序，兼容旧版嵌套 book 对象。 */
    fun books(): List<JsonObject> {
        val file = File(root, "nt_index.json")
        val stamp = "${file.lastModified()}:${file.length()}"
        if (stamp != indexStamp) {
            val parsed = runCatching { Json.parseToJsonElement(file.readText()) }.getOrNull()
            val books = (parsed as? JsonArray) ?: (parsed as? JsonObject)?.array("books")
            index = books.orEmpty().mapNotNull { it as? JsonObject }
            indexStamp = stamp
            cache.clear()
        }
        return index
    }

    /** 按书加载划线及想法，将同一 range 或完全相同原文的想法归到划线上。 */
    @Suppress("CyclomaticComplexMethod")
    fun items(bookId: String): List<NoteItem> {
        val marksFile = File(root, "nt_$bookId.json")
        val ideasFile = File(root, "ni_$bookId.json")
        val stamp =
            "${marksFile.lastModified()}:${marksFile.length()}:" +
                "${ideasFile.lastModified()}:${ideasFile.length()}"
        val index = books()
        cache[bookId]?.takeIf { it.first == stamp }?.let { return it.second }
        val metadata = index.firstOrNull { it.text("bookId") == bookId } ?: emptyObject()
        val book = metadata["book"] as? JsonObject ?: metadata
        val marks =
            readObject(marksFile)
                .array("marks")
                .mapNotNull { it as? JsonObject }
                .filter { it.text("markText").isNotBlank() }
                .map { note(bookId, book, it, false) }
                .toMutableList()
        val ideas =
            readObject(ideasFile)
                .array("ideas")
                .mapNotNull { it as? JsonObject }
                .filter { it.text("content").isNotBlank() || it.text("abstract").isNotBlank() }
                .map { note(bookId, book, it, true) }
        for (idea in ideas) {
            val host =
                marks.firstOrNull {
                    !it.hasIdea() &&
                        (idea.chapterUid == 0 || it.chapterUid == 0 || idea.chapterUid == it.chapterUid) &&
                        (
                            (idea.range.isNotEmpty() && it.range == idea.range) ||
                                (idea.markText.isNotEmpty() && normalized(it.markText) == normalized(idea.markText))
                        )
                }
            if (host == null) {
                marks.add(idea)
            } else {
                host.ideaText = idea.ideaText
                host.kind = NoteItem.KIND_IDEA
                host.star = idea.star
                if (host.chapterTitle.isEmpty()) host.chapterTitle = idea.chapterTitle
            }
        }
        val result = marks.filter { it.bookmarkId.isNotEmpty() }
        cache[bookId] = stamp to result
        while (cache.size > BOOK_CACHE_SIZE) cache.remove(cache.keys.first())
        return result
    }

    /**
     * 默认池沿用原版索引 noteCount + reviewCount 口径（含已归到划线上的想法），表示原始记录总量。
     * 抽样只保留 40 条，不将索引总量作为批次寻址上界；选书顺序池则使用合并后的真实条数。
     */
    fun total(
        ideasOnly: Boolean,
        pickedBook: String,
    ): Int =
        if (pickedBook.isNotEmpty()) {
            items(pickedBook).size
        } else {
            books().sumOf { it.number("reviewCount").toInt() + if (ideasOnly) 0 else it.number("noteCount").toInt() }
        }

    /** 抽样层候选，完整索引参与且只抽本地已就绪的书。 */
    fun candidates(ideas: Boolean): List<Pair<String, Int>> =
        books().mapNotNull {
            val id = it.text("bookId")
            val weight = it.number(if (ideas) "reviewCount" else "noteCount").toInt()
            val file = File(root, "${if (ideas) "ni" else "nt"}_$id.json")
            if (id.isNotEmpty() && weight > 0 && file.length() > 0) id to weight else null
        }

    private fun note(
        id: String,
        book: JsonObject,
        raw: JsonObject,
        idea: Boolean,
    ): NoteItem =
        NoteItem().apply {
            bookId = id
            bookmarkId = raw.text(if (idea) "reviewId" else "bookmarkId")
            title = book.text("title")
            author = book.text("author")
            coverUrl = book.text("cover").ifEmpty { book.text("coverUrl") }
            markText = raw.text(if (idea) "abstract" else "markText")
            ideaText = if (idea) raw.text("content") else ""
            kind = if (idea) NoteItem.KIND_IDEA else NoteItem.KIND_MARK
            range = raw.text("range")
            star = (raw["star"] as? JsonPrimitive)?.content?.toIntOrNull() ?: -1
            chapterUid = raw.number("chapterUid").toInt()
            chapterIdx = raw.number("chapterIdx").toInt()
            chapterTitle = raw.text(if (idea) "chapterName" else "chapterTitle")
            createTime = raw.number("createTime")
        }

    private fun normalized(text: String): String = text.filterNot { it.isWhitespace() || it in "….。" }

    private companion object {
        const val BOOK_CACHE_SIZE = 8
    }
}

/** 本记文件解析辅助：损坏或尚未同步的文件按空数据处理。 */
internal fun readObject(file: File): JsonObject =
    runCatching { Json.parseToJsonElement(file.readText()) as? JsonObject }.getOrNull() ?: emptyObject()

internal fun emptyObject(): JsonObject = JsonObject(emptyMap())

internal fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty()

internal fun JsonObject.number(key: String): Long = text(key).toLongOrNull() ?: 0L

internal fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
