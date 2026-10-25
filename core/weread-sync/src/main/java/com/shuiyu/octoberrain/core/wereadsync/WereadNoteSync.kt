package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File

/** 完整分页获取笔记索引，再按书下载划线与想法；逐本保存的正文允许失败后续补。 */
@Suppress("TooManyFunctions")
class WereadNoteSync(
    private val request: WereadRequest,
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store = WereadStore(root)
    private var force = false

    /** 本轮同步未完成的原因，供宿主合并到同步结果中展示。 */
    var warnings: List<String> = emptyList()
        private set

    /** 索引分页失败保留旧索引；单本正文失败保留旧正文，并返回未完成数量。 */
    fun sync(
        force: Boolean = false,
        bookIds: Set<String>? = null,
    ): WereadNoteBatch? {
        this.force = force
        warnings = emptyList()
        val overview = notebooks() ?: return null
        val old = oldIndex().associateBy { it.text("bookId") }
        val books = overview.map { normalizeBook(it, old[it.text("bookId")]) }
        val index =
            buildJsonObject {
                put("v", JsonPrimitive(1))
                put("savedAt", JsonPrimitive(clock()))
                put("books", JsonArray(books))
            }.toString()
        var remaining = 0
        var updated = 0
        val selected = if (bookIds == null) books else books.filter { it.text("bookId") in bookIds }
        for ((position, book) in selected.withIndex()) {
            val result = syncBook(book)
            updated += result.updatedFiles
            if (!result.complete) {
                // 一次离线即可结束本轮网络访问，余下书籍只检查本地缓存，下次从缺失处续补。
                remaining = selected.drop(position).count { !cached(it) }
                warnings = warnings + "笔记正文同步中断，已保存内容保留，下次同步可继续补齐"
                break
            }
        }
        // 正文已逐本保存，完整索引由同步服务统一发布。
        return WereadNoteBatch(index, emptyList(), remaining == 0, remaining, updated)
    }

    /** 任一正文请求或保存失败即返回，避免离线后继续逐书等待网络超时。 */
    @Suppress("ReturnCount") // 每个请求与落盘检查均为中断点，立即返回保证失败后不再发起请求。
    private fun syncBook(book: JsonObject): BodyResult {
        var updated = 0
        val id = book.text("bookId")
        val marksName = "nt_$id.json"
        val previousMarks = read(marksName)
        var chapters = previousMarks?.objects("chapters").orEmpty()
        if (!fresh(previousMarks, "marks", book)) {
            val response = request.request("/book/bookmarklist", obj("bookId" to JsonPrimitive(id)))
            val marks = response?.get("updated") as? JsonArray ?: return BodyResult(updated, false)
            chapters = response.objects("chapters")
            val normalized =
                marks
                    .filterIsInstance<JsonObject>()
                    .map { mark ->
                        withChapter(mark, chapters, "chapterTitle")
                    }.distinctBy { it.text("bookmarkId").ifEmpty { "${it.text("chapterUid")}:${it.text("range")}" } }
            if (!save(marksName, "marks", normalized, book, chapters)) return BodyResult(updated, false)
            updated++
        }
        val ideasName = "ni_$id.json"
        if (!fresh(read(ideasName), "ideas", book)) {
            val ideas = ideas(id) ?: return BodyResult(updated, false)
            val normalized = ideas.map { withChapter(it, chapters, "chapterName") }
            if (!save(ideasName, "ideas", normalized, book)) return BodyResult(updated, false)
            updated++
        }
        return BodyResult(updated, true)
    }

    private fun cached(book: JsonObject): Boolean {
        val id = book.text("bookId")
        return fresh(read("nt_$id.json"), "marks", book) && fresh(read("ni_$id.json"), "ideas", book)
    }

    private data class BodyResult(
        val updatedFiles: Int,
        val complete: Boolean,
    )

    @Suppress("ReturnCount") // 每项分页协议校验失败都保留旧索引并给出具体原因，避免深层嵌套。
    private fun notebooks(): List<JsonObject>? {
        val all = linkedMapOf<String, JsonObject>()
        val cursors = mutableSetOf<Long>()
        var cursor: Long? = null
        do {
            val business =
                buildJsonObject {
                    put("count", JsonPrimitive(PAGE_SIZE))
                    cursor?.let { put("lastSort", JsonPrimitive(it)) }
                }
            val page =
                request.request("/user/notebooks", business)
                    ?: return overviewFailure("分页请求失败，已获取 ${all.size} 本，保留旧索引")
            val entries =
                page["books"] as? JsonArray
                    ?: return overviewFailure("分页缺少书目列表，保留旧索引")
            for (entry in entries) {
                val book = entry as? JsonObject ?: return overviewFailure("书目格式异常，保留旧索引")
                val id = book.text("bookId")
                // 书籍标识会用于文件名，异常标识不得进入正文写入流程。
                if (!validBookId(id)) {
                    return overviewFailure("书籍标识异常，保留旧索引")
                }
                all[id] = book
            }
            val hasMore =
                page.numberOrNull("hasMore")
                    ?: return overviewFailure("分页缺少结束标记，保留旧索引")
            if (hasMore == 0L) return all.values.toList()
            val nextCursor =
                (entries.lastOrNull() as? JsonObject)?.numberOrNull("sort")
                    ?: return overviewFailure("分页缺少下一页游标，保留旧索引")
            if (!cursors.add(nextCursor)) return overviewFailure("分页游标重复，保留旧索引")
            cursor = nextCursor
        } while (true)
    }

    @Suppress("ReturnCount") // 分页与条目校验任一步失败，都不得用不完整想法列表覆盖旧文件。
    private fun ideas(id: String): List<JsonObject>? {
        val all = linkedMapOf<String, JsonObject>()
        val cursors = mutableSetOf<Long>()
        var cursor = 0L
        do {
            val page =
                request.request(
                    "/review/list/mine",
                    obj(
                        "bookid" to JsonPrimitive(id),
                        "synckey" to JsonPrimitive(cursor),
                        "count" to JsonPrimitive(PAGE_SIZE),
                    ),
                ) ?: return null
            val entries = page["reviews"] as? JsonArray ?: return null
            for (entry in entries) {
                val wrapper = entry as? JsonObject ?: return null
                val review = wrapper["review"] as? JsonObject ?: wrapper
                val reviewId = review.text("reviewId").ifBlank { wrapper.text("reviewId") }
                if (reviewId.isBlank()) return null
                all[reviewId] = JsonObject(review + ("reviewId" to JsonPrimitive(reviewId)))
            }
            if ((page.numberOrNull("hasMore") ?: return null) == 0L) return all.values.toList()
            val next = page.numberOrNull("synckey") ?: return null
            if (next == cursor || !cursors.add(next)) return null
            cursor = next
        } while (true)
    }

    private fun overviewFailure(reason: String): List<JsonObject>? {
        warnings = warnings + "笔记索引$reason"
        return null
    }

    private fun validBookId(id: String): Boolean = id.isNotBlank() && id.none { it in "/\\:" }

    private fun normalizeBook(
        source: JsonObject,
        previous: JsonObject?,
    ): JsonObject {
        val nested = source["book"] as? JsonObject
        val fields = previous.orEmpty().toMutableMap()
        fields.putAll(source)
        for (key in listOf("title", "author", "cover", "centPrice", "price", "originalPrice", "free")) {
            (nested?.get(key) ?: source[key] ?: previous?.get(key))?.let { fields[key] = it }
        }
        fields["progress"] = source["readingProgress"] ?: source["progress"] ?: JsonPrimitive(-1)
        fields["priceY"] = nested?.get("price") ?: source["priceY"] ?: previous?.get("priceY") ?: JsonPrimitive(0)
        fields["origY"] = nested?.get("originalPrice") ?: source["origY"] ?: previous?.get("origY") ?: JsonPrimitive(0)
        return JsonObject(fields)
    }

    private fun withChapter(
        item: JsonObject,
        chapters: List<JsonObject>,
        titleKey: String,
    ): JsonObject {
        val chapter = chapters.firstOrNull { it.number("chapterUid") == item.number("chapterUid") }
        val fields = item.toMutableMap()
        if (item.text(titleKey).isBlank()) {
            (chapter?.get("title") ?: item["chapterName"])?.let { fields[titleKey] = it }
        }
        if (item.number("chapterIdx") == 0L) chapter?.get("chapterIdx")?.let { fields["chapterIdx"] = it }
        return JsonObject(fields)
    }

    private fun save(
        name: String,
        key: String,
        items: List<JsonObject>,
        book: JsonObject,
        chapters: List<JsonObject> = emptyList(),
    ): Boolean {
        val body =
            buildJsonObject {
                put("fetchedAt", JsonPrimitive(clock()))
                put("sort", book["sort"] ?: JsonPrimitive(0))
                put(key, JsonArray(items))
                if (key == "marks") put("chapters", JsonArray(chapters))
            }
        return store.writeNoteFiles(listOf(WereadNoteFile(name, body.toString())))
    }

    private fun fresh(
        body: JsonObject?,
        key: String,
        book: JsonObject,
    ): Boolean {
        if (force || body?.get(key) !is JsonArray) return false
        val fetchedAt = body.number("fetchedAt")
        val age = clock() - fetchedAt
        return fetchedAt > 0 && age in 0..BODY_TTL && body.number("sort") == book.number("sort")
    }

    private fun oldIndex(): List<JsonObject> =
        runCatching {
            when (val index = Json.parseToJsonElement(File(root, "nt_index.json").readText())) {
                is JsonArray -> index.filterIsInstance<JsonObject>()
                is JsonObject -> index.objects("books")
                else -> emptyList()
            }
        }.getOrDefault(emptyList())

    private fun read(name: String): JsonObject? =
        runCatching { Json.parseToJsonElement(File(root, name).readText()) as? JsonObject }.getOrNull()

    private fun obj(vararg fields: Pair<String, JsonPrimitive>) = JsonObject(mapOf(*fields))

    private fun JsonObject.objects(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

    private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.numberOrNull(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.number(key: String): Long = numberOrNull(key) ?: 0L

    private companion object {
        const val PAGE_SIZE = 100
        const val BODY_TTL = 6 * 60 * 60 * 1000L
    }
}
