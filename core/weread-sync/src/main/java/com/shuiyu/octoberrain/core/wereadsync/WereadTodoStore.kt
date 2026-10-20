package com.shuiyu.octoberrain.core.wereadsync

import com.shuiyu.octoberrain.core.common.WereadTodo
import com.shuiyu.octoberrain.core.common.WereadTodoHost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * 待办的本地存储，读写位置与微读墨记内核一致。
 *
 * 数据写 `kernel_prefs.json` 的字符串组 `todo_list` 键，值为 JSON 数组，
 * 条目字段与内核 `com.inkread.weekread.kernel.TodoEntry` 同名：
 * `id`、`content`、`date`、`min`、`done`、`order`。自增标识存长整数组
 * `todo_next_id`，与微读原版 `core/TodoStore.java` 的键名一致。
 *
 * 所有公开方法在内部互斥，界面可在任意线程调用。
 *
 * 函数数量随增删改查与序列化步骤展开而增加，单条读写各自成函数比合并更易定位问题，
 * 因此对该文件的函数数量做局部豁免。
 *
 * @param root 微读数据根目录，通常为 filesDir/weread
 */
@Suppress("TooManyFunctions")
class WereadTodoStore(
    root: File,
) : WereadTodoHost {
    private val store = WereadStore(root)

    override fun list(): List<WereadTodo> = store.withPrefsLock { read() }

    override fun add(
        content: String,
        dateSec: Long,
        minuteOfDay: Int,
    ): List<WereadTodo> =
        store.withPrefsLock {
            val text = content.trim()
            val current = read()
            if (text.isEmpty()) {
                current
            } else {
                write(current + newTodo(current, text, dateSec, minuteOfDay))
                read()
            }
        }

    override fun toggle(id: Long): List<WereadTodo> =
        store.withPrefsLock {
            write(read().map { todo -> if (todo.id == id) todo.copy(done = !todo.done) else todo })
            read()
        }

    override fun remove(id: Long): List<WereadTodo> =
        store.withPrefsLock {
            write(read().filterNot { todo -> todo.id == id })
            read()
        }

    override fun move(
        id: Long,
        delta: Int,
    ): List<WereadTodo> =
        store.withPrefsLock {
            val current = read()
            val index = current.indexOfFirst { todo -> todo.id == id }
            if (index < 0) {
                current
            } else {
                val target = (index + delta).coerceIn(0, current.lastIndex)
                if (target == index) {
                    current
                } else {
                    val reordered = current.toMutableList()
                    reordered.add(target, reordered.removeAt(index))
                    write(reordered.mapIndexed { position, todo -> todo.copy(order = position.toLong()) })
                    read()
                }
            }
        }

    override fun update(
        id: Long,
        content: String,
        dateSec: Long,
        minuteOfDay: Int,
    ): List<WereadTodo> =
        store.withPrefsLock {
            val text = content.trim()
            write(
                read().map { todo ->
                    if (todo.id != id) {
                        todo
                    } else {
                        todo.copy(
                            content = text.ifEmpty { todo.content },
                            dateSec = dateSec,
                            minuteOfDay = minuteOfDay,
                        )
                    }
                },
            )
            read()
        }

    /**
     * 构造一条新待办，标识取自长整数组，序号排在现有条目之后。
     *
     * @param current 当前清单
     * @param content 内容
     * @param dateSec 日期
     * @param minuteOfDay 时间
     * @return 新待办
     */
    private fun newTodo(
        current: List<WereadTodo>,
        content: String,
        dateSec: Long,
        minuteOfDay: Int,
    ): WereadTodo {
        val id = store.getLong(KEY_NEXT_ID) ?: DEFAULT_NEXT_ID
        store.putLong(KEY_NEXT_ID, id + 1L)
        val order = current.size.toLong()
        return WereadTodo(
            id = id,
            content = content,
            dateSec = dateSec,
            minuteOfDay = minuteOfDay,
            done = false,
            order = order,
        )
    }

    /**
     * 读取清单，解析失败的条目跳过。
     *
     * @return 按序号升序排列的清单
     */
    private fun read(): List<WereadTodo> {
        val raw = store.getString(KEY_LIST).orEmpty()
        if (raw.isBlank()) return emptyList()
        val parsed = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonArray
        return parsed
            .orEmpty()
            .mapNotNull { element -> (element as? JsonObject)?.let { todoOf(it) } }
            .sortedBy { todo -> todo.order }
    }

    /**
     * 把一条 JSON 记录转为待办，字段缺失时取默认值。
     *
     * @param node JSON 记录
     * @return 待办
     */
    private fun todoOf(node: JsonObject): WereadTodo =
        WereadTodo(
            id = node.longOf("id"),
            content = node.stringOf("content"),
            dateSec = node.longOf("date"),
            minuteOfDay = node.intOf("min", WereadTodo.NO_TIME),
            done = node["done"]?.jsonPrimitive?.contentOrNull == "true",
            order = node.longOf("order"),
        )

    /**
     * 写回清单，按序号升序序列化。
     *
     * @param todos 清单
     */
    private fun write(todos: List<WereadTodo>) {
        val array =
            buildJsonArray {
                todos.sortedBy { todo -> todo.order }.forEach { todo -> add(todoJson(todo)) }
            }
        store.putString(KEY_LIST, array.toString())
    }

    /**
     * 待办转 JSON 记录，键名与内核一致。
     *
     * @param todo 待办
     * @return JSON 对象
     */
    private fun todoJson(todo: WereadTodo): JsonObject =
        JsonObject(
            mapOf(
                "id" to JsonPrimitive(todo.id),
                "content" to JsonPrimitive(todo.content),
                "date" to JsonPrimitive(todo.dateSec),
                "min" to JsonPrimitive(todo.minuteOfDay),
                "done" to JsonPrimitive(todo.done),
                "order" to JsonPrimitive(todo.order),
            ),
        )

    /** 读取长整数字段。 */
    private fun JsonObject.longOf(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: 0L

    /** 读取整数字段。 */
    private fun JsonObject.intOf(
        key: String,
        fallback: Int,
    ): Int = this[key]?.jsonPrimitive?.longOrNull?.toInt() ?: fallback

    /** 读取字符串字段。 */
    private fun JsonObject.stringOf(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    companion object {
        /** 清单键名，与微读内核一致。 */
        const val KEY_LIST: String = "todo_list"

        /** 自增标识键名，与微读内核一致。 */
        const val KEY_NEXT_ID: String = "todo_next_id"

        /** 首个自增标识的取值。 */
        const val DEFAULT_NEXT_ID: Long = 1L
    }
}
