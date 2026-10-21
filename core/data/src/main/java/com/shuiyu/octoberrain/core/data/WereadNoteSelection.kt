package com.shuiyu.octoberrain.core.data

import com.inkread.weekread.kernel.NoteItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * App 与桌面共用的本记抽取状态。默认/想法/选书各存一槽，沿用原版键名和批次文件。
 * 状态单独落盘，避免同时持有的 KernelStore 缓存覆盖抽签历史；首次从迁移偏好读取。
 */
@Suppress("TooManyFunctions")
class WereadNoteSelection(
    private val root: File,
    private val day: () -> String = { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date()) },
    private val random: Random = Random.Default,
) {
    private val library = WereadNoteLibrary(root)

    /** 选记书目，空为全库；桌面也跟随该维度。 */
    fun pickedBook(): String = synchronized(LOCK) { state().text("pick_book") }

    /** 只看想法为 App 专属，桌面始终使用默认或选书槽。 */
    fun ideasOnly(): Boolean = synchronized(LOCK) { state().text("ideas_only") == "true" }

    fun slot(desktop: Boolean = false): Int = synchronized(LOCK) { slot(state(), desktop) }

    /** 选书清除该槽历史，但保留默认池浏览进度。 */
    fun selectBook(bookId: String) =
        synchronized(LOCK) {
            val values = state().toMutableMap()
            values["pick_book"] = JsonPrimitive(bookId)
            if (bookId.isNotEmpty()) values["ideas_only"] = JsonPrimitive(false)
            for (key in listOf("day", "taken", "cur_book", "cur_key", "batch_pos", "hist")) values.remove("${key}_p")
            saveState(JsonObject(values))
            saveBatch(PICK_SLOT, emptyList())
        }

    fun setIdeasOnly(enabled: Boolean) =
        synchronized(LOCK) {
            val values = state().toMutableMap()
            values["ideas_only"] = JsonPrimitive(enabled)
            if (enabled) values["pick_book"] = JsonPrimitive("")
            saveState(JsonObject(values))
        }

    /** 同日稳定，跨天推进；手动下一条立即推进并记录访问历史。 */
    fun current(
        desktop: Boolean = false,
        advance: Boolean = false,
    ): NoteItem? =
        synchronized(LOCK) {
            val state = state()
            val slot = slot(state, desktop)
            val current =
                resolve(state.text(key("cur_book", slot)), state.text(key("cur_key", slot)))
                    ?.takeIf { slot != IDEA_SLOT || it.hasIdea() }
            if (!advance &&
                state.text(key("day", slot)) == day() &&
                current != null
            ) {
                return@synchronized current
            }
            var batch = batch(slot)
            var position = state.number(key("batch_pos", slot)).toInt() + 1
            if (!state.containsKey(key("batch_pos", slot))) position = 0
            var note = batch.getOrNull(position)?.let { resolve(it.first, it.second) }
            if (note == null) {
                batch = newBatch(slot, state.text("pick_book"))
                saveBatch(slot, batch)
                position = 0
                note = batch.firstOrNull()?.let { resolve(it.first, it.second) }
            }
            note ?: return@synchronized null
            val total = total(slot, state)
            val taken = (state.number(key("taken", slot)).toInt() % total.coerceAtLeast(1)) + 1
            val history =
                listOf(note.bookId to note.bookmarkId) +
                    history(state, slot).filterNot {
                        it.first == note.bookId && it.second == note.bookmarkId
                    }
            writeCurrent(state, slot, note, position, taken, history.take(HISTORY_SIZE))
            note
        }

    /** 真正沿访问历史回退，首次无历史时保持当前条，不伪造另一条“上一条”。 */
    fun previous(desktop: Boolean = false): NoteItem? =
        synchronized(LOCK) {
            val state = state()
            val slot = slot(state, desktop)
            val history = history(state, slot)
            val currentPair = state.text(key("cur_book", slot)) to state.text(key("cur_key", slot))
            val earlier = if (history.firstOrNull() == currentPair) history.drop(1) else history
            val previous =
                earlier.firstNotNullOfOrNull { pair ->
                    resolve(pair.first, pair.second)?.takeIf { slot != IDEA_SLOT || it.hasIdea() }
                }
                    ?: return@synchronized current(desktop)
            val rest = history.dropWhile { it.first != previous.bookId || it.second != previous.bookmarkId }
            val position = batch(slot).indexOf(previous.bookId to previous.bookmarkId)
            writeCurrent(
                state,
                slot,
                previous,
                position,
                (state.number(key("taken", slot)).toInt() - 1).coerceAtLeast(1),
                rest,
            )
            previous
        }

    /** 当前累计序号和整个池的条数，批次大小不冒充全库总数。 */
    fun progress(desktop: Boolean = false): IntArray =
        synchronized(LOCK) {
            val state = state()
            val slot = slot(state, desktop)
            val total = total(slot, state)
            intArrayOf(if (total == 0) 0 else state.number(key("taken", slot)).toInt().coerceIn(1, total), total)
        }

    /** 全部笔记书目，包括已不在书架中的历史笔记。 */
    fun books(): List<JsonObject> = library.books()

    private fun total(
        slot: Int,
        state: JsonObject,
    ): Int = library.total(slot == IDEA_SLOT, if (slot == PICK_SLOT) state.text("pick_book") else "")

    private fun slot(
        state: JsonObject,
        desktop: Boolean,
    ): Int =
        when {
            !desktop && state.text("ideas_only") == "true" -> IDEA_SLOT
            state.text("pick_book").isNotEmpty() -> PICK_SLOT
            else -> 0
        }

    private fun resolve(
        book: String,
        id: String,
    ): NoteItem? = if (book.isEmpty() || id.isEmpty()) null else library.items(book).firstOrNull { it.bookmarkId == id }

    private fun newBatch(
        slot: Int,
        picked: String,
    ): List<Pair<String, String>> {
        if (slot == PICK_SLOT) return library.items(picked).map { it.bookId to it.bookmarkId }
        val seen = linkedSetOf<Pair<String, String>>()
        val ideaOffset = random.nextInt(IDEA_EVERY)
        val marks = library.candidates(false)
        val ideasPool = library.candidates(true)
        repeat(BATCH_SIZE) { position ->
            val ideas = slot == IDEA_SLOT || position % IDEA_EVERY == ideaOffset
            val item =
                draw(if (ideas) ideasPool else marks, ideas, seen)
                    ?: if (slot != IDEA_SLOT) draw(if (ideas) marks else ideasPool, !ideas, seen) else null
            if (item != null) seen.add(item)
        }
        return seen.toList()
    }

    private fun draw(
        pool: List<Pair<String, Int>>,
        ideas: Boolean,
        seen: Set<Pair<String, String>>,
    ): Pair<String, String>? {
        val candidates = pool.toMutableList()
        while (candidates.isNotEmpty()) {
            var ticket = random.nextInt(candidates.sumOf { it.second })
            val book =
                candidates
                    .first {
                        ticket -= it.second
                        ticket < 0
                    }.first
            val available = library.items(book).filter { it.hasIdea() == ideas && (book to it.bookmarkId) !in seen }
            if (available.isNotEmpty()) return book to available.random(random).bookmarkId
            candidates.removeAll { it.first == book }
        }
        return null
    }

    private fun writeCurrent(
        state: JsonObject,
        slot: Int,
        note: NoteItem,
        position: Int,
        taken: Int,
        history: List<Pair<String, String>>,
    ) {
        saveState(
            JsonObject(
                state +
                    mapOf(
                        key("cur_book", slot) to JsonPrimitive(note.bookId),
                        key("cur_key", slot) to JsonPrimitive(note.bookmarkId),
                        key("batch_pos", slot) to JsonPrimitive(position),
                        key("taken", slot) to JsonPrimitive(taken),
                        key("day", slot) to JsonPrimitive(day()),
                        key("hist", slot) to JsonPrimitive(pairs(history).toString()),
                    ),
            ),
        )
    }

    private fun state(): JsonObject {
        val file = File(root, STATE_FILE)
        if (file.isFile) return readObject(file)
        val prefs = readObject(File(root, "kernel_prefs.json"))
        return JsonObject(
            listOf("string", "int", "bool")
                .flatMap {
                    (prefs[it] as? JsonObject).orEmpty().entries
                }.filter { it.key in STATE_KEYS }
                .associate { it.key to it.value },
        )
    }

    private fun history(
        state: JsonObject,
        slot: Int,
    ): List<Pair<String, String>> =
        runCatching {
            decode(
                kotlinx.serialization.json.Json
                    .parseToJsonElement(state.text(key("hist", slot))) as JsonArray,
            )
        }.getOrDefault(emptyList())

    private fun batch(slot: Int): List<Pair<String, String>> =
        decode(readObject(File(root, batchFile(slot))).array("items"))

    private fun saveBatch(
        slot: Int,
        values: List<Pair<String, String>>,
    ) = write(File(root, batchFile(slot)), JsonObject(mapOf("items" to pairs(values))))

    private fun saveState(state: JsonObject) = write(File(root, STATE_FILE), state)

    private fun write(
        file: File,
        value: JsonObject,
    ) {
        root.mkdirs()
        val temporary = File(root, file.name + ".tmp")
        temporary.writeText(value.toString())
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun pairs(values: List<Pair<String, String>>): JsonArray =
        JsonArray(
            values.map {
                JsonObject(mapOf("b" to JsonPrimitive(it.first), "k" to JsonPrimitive(it.second)))
            },
        )

    private fun decode(array: JsonArray): List<Pair<String, String>> =
        array
            .mapNotNull {
                (it as? JsonObject)?.let { item -> item.text("b") to item.text("k") }
            }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }

    private fun key(
        base: String,
        slot: Int,
    ): String =
        base +
            when (slot) {
                IDEA_SLOT -> "_i"
                PICK_SLOT -> "_p"
                else -> ""
            }

    private fun batchFile(slot: Int): String =
        "nt_batch${when (slot) {
            IDEA_SLOT -> "_i"
            PICK_SLOT -> "_p"
            else -> ""
        }}.json"

    private companion object {
        val LOCK = Any()
        val STATE_KEYS =
            setOf("pick_book", "ideas_only") +
                listOf("", "_i", "_p").flatMap { suffix ->
                    listOf("day", "taken", "cur_book", "cur_key", "batch_pos", "hist").map { it + suffix }
                }
        const val STATE_FILE = "note_selection.json"
        const val IDEA_SLOT = 1
        const val PICK_SLOT = 2
        const val BATCH_SIZE = 40
        const val IDEA_EVERY = 8
        const val HISTORY_SIZE = 64
    }
}
