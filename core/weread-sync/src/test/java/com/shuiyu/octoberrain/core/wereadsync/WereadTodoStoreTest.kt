package com.shuiyu.octoberrain.core.wereadsync

import com.shuiyu.octoberrain.core.common.WereadTodo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** 待办存储的读写、排序与持久化。 */
class WereadTodoStoreTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = createTempDir(prefix = "weread-todo")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    /** 新增两条待办后按序号返回，标识自增。 */
    @Test
    fun `新增待办按顺序返回`() {
        val store = WereadTodoStore(root)
        val first = store.add("写读书笔记")
        val second = store.add("整理书架")

        assertEquals(1, first.size)
        assertEquals(listOf("写读书笔记", "整理书架"), second.map { it.content })
        assertEquals(listOf(1L, 2L), second.map { it.id })
    }

    /** 空白内容不产生新条目。 */
    @Test
    fun `空白内容被忽略`() {
        val store = WereadTodoStore(root)
        store.add("   ")
        assertTrue(store.list().isEmpty())
    }

    /** 勾选切换完成状态。 */
    @Test
    fun `勾选切换完成状态`() {
        val store = WereadTodoStore(root)
        val id = store.add("读一章").first().id
        val done = store.toggle(id)
        assertTrue(done.first().done)
        val undone = store.toggle(id)
        assertFalse(undone.first().done)
    }

    /** 删除按标识移除。 */
    @Test
    fun `删除条目`() {
        val store = WereadTodoStore(root)
        store.add("第一条")
        val id = store.add("第二条").last().id
        val left = store.remove(id)
        assertEquals(listOf("第一条"), left.map { it.content })
    }

    /** 上移与下移改变顺序。 */
    @Test
    fun `移动调整顺序`() {
        val store = WereadTodoStore(root)
        store.add("甲")
        val secondId = store.add("乙").last().id
        val movedUp = store.move(secondId, -1)
        assertEquals(listOf("乙", "甲"), movedUp.map { it.content })
        val movedDown = store.move(secondId, 1)
        assertEquals(listOf("甲", "乙"), movedDown.map { it.content })
    }

    /** 修改内容保留其他字段。 */
    @Test
    fun `修改内容与日期`() {
        val store = WereadTodoStore(root)
        val id = store.add("旧内容").first().id
        val updated = store.update(id, "新内容", dateSec = 1_792_339_200L, minuteOfDay = 540)
        val todo = updated.first()
        assertEquals("新内容", todo.content)
        assertEquals(1_792_339_200L, todo.dateSec)
        assertEquals(540, todo.minuteOfDay)
        assertEquals("09:00", todo.timeLabel())
    }

    /** 重新创建存储后仍能读回，说明写入了磁盘。 */
    @Test
    fun `重启后保持数据`() {
        WereadTodoStore(root).add("持久化条目")
        val reopened = WereadTodoStore(root)
        assertEquals(listOf("持久化条目"), reopened.list().map { it.content })
    }

    /** 写入位置是 kernel_prefs 的字符串组 todo_list，且不破坏其他分组。 */
    @Test
    fun `偏好文件结构与内核一致`() {
        val backing = WereadStore(root)
        backing.putInt("menu_top_n", 5)
        backing.putLong("todo_next_id", 7L)
        val store = WereadTodoStore(root)
        store.add("内核可读")

        val document =
            Json.parseToJsonElement(File(root, WereadStore.FILE_PREFS).readText()).jsonObject
        val list =
            document["string"]
                ?.jsonObject
                ?.get("todo_list")
                ?.jsonPrimitive
                ?.content
                .orEmpty()
        assertTrue(list.contains("\"content\":\"内核可读\""))
        assertTrue(list.contains("\"min\":-1"))
        assertEquals(
            "5",
            document["int"]
                ?.jsonObject
                ?.get("menu_top_n")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "8",
            document["long"]
                ?.jsonObject
                ?.get("todo_next_id")
                ?.jsonPrimitive
                ?.content,
        )
    }

    /** 待办条目带日期时间时给出组合文案。 */
    @Test
    fun `日期时间文案组合`() {
        val todo = WereadTodo(dateSec = 1_792_339_200L, minuteOfDay = 870)
        assertTrue(todo.hasDate)
        assertTrue(todo.hasTime)
        assertTrue(todo.whenLabel.endsWith("14:30"))

        val timeOnly = WereadTodo(minuteOfDay = 60)
        assertEquals("01:00", timeOnly.whenLabel)
    }
}
