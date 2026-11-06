package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File

/** 手动刷新独立成功基线；其他同步发布索引不能掩盖尚未检查的正文。 */
internal class WereadNoteRefreshPlan(
    private val root: File,
    shelf: List<WereadShelfBook>,
    private val clock: () -> Long,
) {
    private val store = WereadStore(root)
    private val readings = shelf.associate { it.bookId to it.readUpdateTime }
    private val recent = shelf.filter { it.readUpdateTime > 0 }.maxByOrNull { it.readUpdateTime }?.bookId
    private val baseline = readPlan(root, "note_checks.json").toMutableMap()
    private val started = System.nanoTime()
    var deferredCount: Int = 0
        private set
    private var rotation: String? = null

    fun select(books: List<JsonObject>): List<JsonObject> {
        deferredCount =
            books.count { id(it) != recent && (!valid(it) || checked(it).number("retryAt") > 0) && !due(it) }
        val required =
            books
                .filter { id(it) == recent || ((!valid(it) || checked(it).number("retryAt") > 0) && due(it)) }
                .sortedByDescending { id(it) == recent }
        val requiredIds = required.map(::id).toSet()
        rotation =
            books
                .filterNot {
                    id(it) in requiredIds
                }.filter { valid(it) && checked(it).number("retryAt") == 0L && due(it) }
                .minByOrNull { checked(it).number("checkedAt") }
                ?.let(::id)
        return required + books.filter { id(it) == rotation }
    }

    fun shouldStart(book: JsonObject): Boolean =
        id(book) != rotation || (System.nanoTime() - started) / 1_000_000 < ROTATION_BUDGET_MS

    fun completed(book: JsonObject): Boolean {
        baseline[id(book)] = JsonObject(version(book) + fingerprints(book) + ("checkedAt" to JsonPrimitive(clock())))
        return true
    }

    fun failed(book: JsonObject) {
        baseline[id(book)] = JsonObject(checked(book) + ("retryAt" to JsonPrimitive(clock() + RETRY_DELAY_MS)))
    }

    fun flush(): Boolean =
        store.writeNoteFiles(listOf(WereadNoteFile("note_checks.json", JsonObject(baseline).toString())))

    private fun due(book: JsonObject): Boolean = checked(book).number("retryAt") <= clock()

    private fun valid(book: JsonObject): Boolean =
        (book["unknownVersions"] as? JsonArray).isNullOrEmpty() &&
            listOf("sort", "noteCount", "reviewCount").all { book[it] is JsonPrimitive } &&
            version(book).all { (key, value) -> checked(book)[key] == value } &&
            fingerprints(book).all { (key, value) -> checked(book)[key] == value } &&
            listOf("nt", "ni").all { File(root, "${it}_${id(book)}.json").length() > 0 }

    private fun fingerprints(book: JsonObject): Map<String, JsonPrimitive> =
        listOf("nt", "ni").associateWith {
            val file = File(root, "${it}_${id(book)}.json")
            JsonPrimitive("${file.lastModified()}:${file.length()}")
        }

    private fun version(book: JsonObject): Map<String, kotlinx.serialization.json.JsonElement> =
        listOf("sort", "noteCount", "reviewCount").associateWith { book[it] ?: JsonPrimitive(-1) } +
            ("readUpdateTime" to JsonPrimitive(readings[id(book)] ?: 0))

    private fun checked(book: JsonObject): JsonObject = baseline[id(book)] as? JsonObject ?: JsonObject(emptyMap())

    private companion object {
        const val RETRY_DELAY_MS = 30 * 60 * 1000L
        const val ROTATION_BUDGET_MS = 15_000L
    }
}

private fun id(book: JsonObject): String = (book["bookId"] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.number(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0

private fun readPlan(
    root: File,
    name: String,
): JsonObject =
    runCatching { Json.parseToJsonElement(File(root, name).readText()) as? JsonObject }.getOrNull()
        ?: JsonObject(emptyMap())
