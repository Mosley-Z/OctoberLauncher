package com.shuiyu.octoberrain.feature.files.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EditSession] 的纯 JVM 测试。
 *
 * 覆盖操作栈、撤销重做、深度上限、旋转计数持久化与裁剪矩形的入栈夹取。
 * 位图重放依赖 Android 类型，放在真机验收覆盖，本测试只验证不依赖图形栈的状态逻辑。
 */
class EditSessionTest {
    /** 追加操作后可通过导出快照读取。 */
    @Test
    fun `追加操作并导出快照`() {
        val session = newSession()
        assertTrue(session.push(EditOp.Filter(2)))
        assertTrue(session.push(EditOp.Contrast(0)))
        assertEquals(listOf(EditOp.Filter(2), EditOp.Contrast(0)), session.exportOps())
        assertEquals(2, session.size)
    }

    /** 撤销把操作移入重做栈，重做再移回。 */
    @Test
    fun `撤销与重做`() {
        val session = newSession()
        session.push(EditOp.Mirror)
        assertEquals(EditOp.Mirror, session.undo())
        assertFalse(session.canUndo)
        assertTrue(session.canRedo)
        assertEquals(EditOp.Mirror, session.redo())
        assertTrue(session.canUndo)
        assertFalse(session.canRedo)
    }

    /** 新增操作清空重做栈。 */
    @Test
    fun `新增操作清空重做`() {
        val session = newSession()
        session.push(EditOp.Filter(1))
        session.undo()
        session.push(EditOp.Contrast(2))
        assertFalse(session.canRedo)
    }

    /** 操作栈达到深度上限后拒绝新增。 */
    @Test
    fun `栈深度上限`() {
        val session = newSession()
        repeat(EDIT_STACK_DEPTH) { assertTrue(session.push(EditOp.Rotate(1))) }
        assertFalse(session.push(EditOp.Mirror))
        assertEquals(EDIT_STACK_DEPTH, session.size)
    }

    /** 旋转操作累计次数并写回存储。 */
    @Test
    fun `旋转计数累计并持久化`() {
        val store = InMemoryRotationStore()
        val session = EditSession(PATH, store)
        session.push(EditOp.Rotate(1))
        assertEquals(1, session.rotationCount)
        assertEquals(1, store.count(PATH))
        session.undo()
        assertEquals(0, session.rotationCount)
        assertEquals(0, store.count(PATH))
        session.redo()
        assertEquals(1, store.count(PATH))
    }

    /** 旋转四次不改变累计次数。 */
    @Test
    fun `旋转四次不改变次数`() {
        val session = newSession()
        session.push(EditOp.Rotate(4))
        assertEquals(0, session.rotationCount)
    }

    /** 负数旋转按逆时针归一。 */
    @Test
    fun `负数旋转归一`() {
        val session = newSession()
        session.push(EditOp.Rotate(-1))
        assertEquals(3, session.rotationCount)
    }

    /** 重置清空两个栈并恢复进入时的旋转次数。 */
    @Test
    fun `重置恢复初始状态`() {
        val store = InMemoryRotationStore()
        store.save(PATH, 2)
        val session = EditSession(PATH, store)
        session.push(EditOp.Rotate(1))
        session.undo()
        session.reset()
        assertEquals(0, session.size)
        assertFalse(session.canUndo)
        assertFalse(session.canRedo)
        assertEquals(2, session.rotationCount)
        assertEquals(2, store.count(PATH))
    }

    /** 裁剪矩形入栈前夹取到 0 与 1 之间。 */
    @Test
    fun `裁剪矩形入栈夹取`() {
        val session = newSession()
        session.push(EditOp.Crop(CropRect(-1f, 2f, 3f, 4f)))
        val op = session.exportOps().first()
        assertEquals(EditOp.Crop(CropRect(0f, 1f, 1f, 1f)), op)
    }

    /** 导出快照与内部列表相互独立。 */
    @Test
    fun `导出快照独立于内部列表`() {
        val session = newSession()
        session.push(EditOp.Mirror)
        val snapshot = session.exportOps()
        session.push(EditOp.Filter(3))
        assertEquals(1, snapshot.size)
        assertEquals(2, session.exportOps().size)
    }

    /** 空栈撤销与重做返回 null。 */
    @Test
    fun `空栈撤销重做`() {
        val session = newSession()
        assertNull(session.undo())
        assertNull(session.redo())
        assertEquals(0, session.rotationCount)
    }

    /** 初始旋转次数从存储读取。 */
    @Test
    fun `初始旋转次数读取`() {
        val store = InMemoryRotationStore()
        store.save(PATH, 3)
        val session = EditSession(PATH, store)
        assertEquals(3, session.rotationCount)
        assertNotNull(session.exportOps())
    }

    /** 构造一个使用内存存储的会话。 */
    private fun newSession(): EditSession = EditSession(PATH, InMemoryRotationStore())

    private companion object {
        private const val PATH = "/storage/emulated/0/Pictures/demo.png"
    }
}
