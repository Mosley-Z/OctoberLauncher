package com.shuiyu.octoberrain.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证输入框提交结果如何决定关闭对话框并回调完成。 */
class InkDialogsSubmitTest {
    /** 没有提交校验时沿用原有行为：关闭对话框并把文本交给完成回调。 */
    @Test fun withoutSubmitClosesAndDeliversText() {
        var dismissed = 0
        var delivered: String? = null
        val closed =
            submitInput("新名称", submit = null, onDone = { delivered = it }, dismiss = { dismissed++ })
        assertTrue(closed)
        assertEquals(1, dismissed)
        assertEquals("新名称", delivered)
    }

    /** 提交校验返回真时关闭对话框并回调完成。 */
    @Test fun acceptedSubmitClosesAndDeliversText() {
        var dismissed = 0
        var delivered: String? = null
        val closed =
            submitInput("新名称", submit = { true }, onDone = { delivered = it }, dismiss = { dismissed++ })
        assertTrue(closed)
        assertEquals(1, dismissed)
        assertEquals("新名称", delivered)
    }

    /** 提交校验返回假时保留对话框与输入内容，不回调完成。 */
    @Test fun rejectedSubmitKeepsDialogOpen() {
        var dismissed = 0
        var delivered: String? = null
        val closed =
            submitInput("新名称", submit = { false }, onDone = { delivered = it }, dismiss = { dismissed++ })
        assertFalse(closed)
        assertEquals(0, dismissed)
        assertNull(delivered)
    }

    /** 提交校验收到的就是去空白后的文本，供调用方直接写配置。 */
    @Test fun submitReceivesTrimmedText() {
        var received: String? = null
        submitInput(
            "已裁剪",
            submit = {
                received = it
                true
            },
            onDone = null,
            dismiss = {},
        )
        assertEquals("已裁剪", received)
    }

    /** 没有完成回调时仍可正常关闭，不产生空指针。 */
    @Test fun missingOnDoneStillCloses() {
        var dismissed = 0
        val closed = submitInput("新名称", submit = { true }, onDone = null, dismiss = { dismissed++ })
        assertTrue(closed)
        assertEquals(1, dismissed)
    }
}
