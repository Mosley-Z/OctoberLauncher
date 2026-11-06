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
    private var bodyWrites = 0
    private var force = false
    private var plan: WereadNoteRefreshPlan? = null
    private var progress: (String) -> Unit = {}

    internal fun incremental(
        shelf: List<WereadShelfBook>,
        onProgress: (String) -> Unit,
    ): WereadNoteBatch? {
        plan = WereadNoteRefreshPlan(root, shelf, clock)
        progress = onProgress
        return sync(force = true)
    }

    /** 本轮同步未完成的原因，供宿主合并到同步结果中展示。 */
    var warnings: List<String> = emptyList()
        private set

    /** 索引分页失败保留旧索引；单本正文失败保留旧正文，并返回未完成数量。 */
    fun sync(
        force: Boolean = false,
        bookIds: Set<String>? = null,
    ): WereadNoteBatch? {
        val started = System.nanoTime()
        bodyWrites = 0
        this.force = force
        warnings = emptyList()
        progress("正在同步笔记索引")
        val overview = notebooks() ?: return null
        val old = oldIndex().associateBy { it.text("bookId") }
        val books = overview.map { normalizeBook(it, old[it.text("bookId")]) }
        val index =
            buildJsonObject {
                put("v", JsonPrimitive(1))
                put("savedAt", JsonPrimitive(clock()))
                put("books", JsonArray(books))
            }.toString()
        return if (plan != null &&
            !store.writeNotes(index, emptyList())
        ) {
            null
        } else {
            syncBooks(index, books, bookIds, started)
        }
    }

    private fun syncBooks(
        index: String,
        books: List<JsonObject>,
        bookIds: Set<String>?,
        started: Long,
    ): WereadNoteBatch {
        var remaining = 0
        var updated = 0
        val selected =
            plan?.select(books) ?: if (bookIds == null) books else books.filter { it.text("bookId") in bookIds }
        for ((position, book) in selected.asSequence().takeWhile { plan?.shouldStart(it) != false }.withIndex()) {
            if (position == 0) progress("正在检查笔记正文")
            val result = syncBook(book)
            updated += result.updatedFiles
            if (!result.complete || plan?.completed(book) == false) {
                // 全局故障立即结束；手动增量中的单书异常不阻塞其余候选。
                plan?.failed(book)
                remaining++
                warnings = (warnings + "笔记正文未完成，已保存内容保留，下次同步可继续补齐").distinct()
                if (plan == null || request.stopAfterFailure()) {
                    remaining += selected.size - position - 1
                    break
                }
            } else if (position == 0 || position == selected.lastIndex) {
                progress("笔记已检查 ${position + 1}/${selected.size}")
            }
        }
        remaining += plan?.deferredCount ?: 0
        if (plan?.flush() == false) {
            warnings = warnings + "笔记检查状态未保存，下次重新检查"
            remaining++
        }
        com.shuiyu.octoberrain.core.common.InkLog.d(
            "WereadNoteMetrics",
            "indexed=${books.size} candidates=${selected.size} bodyWrites=$bodyWrites remaining=$remaining " +
                "elapsedMs=${(System.nanoTime() - started) / 1_000_000}",
        )
        // 正文已逐本保存，完整索引由同步服务统一发布。
        return WereadNoteBatch(index, emptyList(), remaining == 0, remaining, updated)
    }

    /** 任一正文请求或保存失败即返回，避免离线后继续逐书等待网络超时。 */
    @Suppress("ReturnCount") // 每个请求与落盘检查均为中断点，立即返回保证失败后不再发起请求。
    private fun syncBook(book: JsonObject): BodyResult {
        var updated = 0
        val initialWrites = bodyWrites
        val id = book.text("bookId")
        val marksName = "nt_$id.json"
        val previousMarks = read(marksName)
        var chapters = previousMarks?.objects("chapters").orEmpty()
        if (!fresh(previousMarks, "marks", book)) {
            val response = request.request("/book/bookmarklist", obj("bookId" to JsonPrimitive(id)))
            val marks = response?.get("updated") as? JsonArray ?: return BodyResult(updated, false)
            if (marks.any { it !is JsonObject }) return BodyResult(updated, false)
            chapters = response.objects("chapters")
            val normalized =
                marks
                    .filterIsInstance<JsonObject>()
                    .map { mark ->
                        withChapter(mark, chapters, "chapterTitle")
                    }.distinctBy { it.text("bookmarkId").ifEmpty { "${it.text("chapterUid")}:${it.text("range")}" } }
            if (!save(marksName, "marks", normalized, book, chapters)) return BodyResult(updated, false)
            updated = bodyWrites - initialWrites
        }
        val ideasName = "ni_$id.json"
        if (!fresh(read(ideasName), "ideas", book)) {
            val ideas = ideas(id) ?: return BodyResult(updated, false)
            val normalized = ideas.map { withChapter(it, chapters, "chapterName") }
            if (!save(ideasName, "ideas", normalized, book)) return BodyResult(updated, false)
            updated = bodyWrites - initialWrites
        }
        return BodyResult(updated, true)
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
        markUnknownVersions(source, previous, fields)
        for (key in listOf("title", "author", "cover", "centPrice", "price", "originalPrice", "free")) {
            (nested?.get(key) ?: source[key] ?: previous?.get(key))?.let { fields[key] = it }
        }
        fields["progress"] = source["readingProgress"] ?: source["progress"] ?: JsonPrimitive(-1)
        fields["priceY"] = nested?.get("price") ?: source["priceY"] ?: previous?.get("priceY") ?: JsonPrimitive(0)
        fields["origY"] = nested?.get("originalPrice") ?: source["origY"] ?: previous?.get("origY") ?: JsonPrimitive(0)
        return JsonObject(fields)
    }

    private fun markUnknownVersions(
        source: JsonObject,
        previous: JsonObject?,
        fields: MutableMap<String, kotlinx.serialization.json.JsonElement>,
    ) {
        val unknown = listOf("sort", "noteCount", "reviewCount").filter { source.numberOrNull(it) == null }
        fields["unknownVersions"] = JsonArray(unknown.map(::JsonPrimitive))
        for (key in unknown) {
            val old = previous?.get(key)?.takeIf { previous.numberOrNull(key) != null }
            if (old == null) fields.remove(key) else fields[key] = old
        }
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
        val previous = read(name)
        val comparable = listOf(key, "chapters")
        if (plan != null && previous != null && comparable.all { previous[it] == body[it] }) return true
        val saved = store.writeNoteFiles(listOf(WereadNoteFile(name, body.toString())))
        if (saved) bodyWrites++
        return saved
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
