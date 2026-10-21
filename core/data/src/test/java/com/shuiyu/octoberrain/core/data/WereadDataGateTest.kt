package com.shuiyu.octoberrain.core.data

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WereadDataGateTest {
    @After
    fun release() {
        WereadDataGate.endSync()
        WereadDataGate.endImport()
    }

    @Test
    fun `运行同步时不受理迁移`() {
        assertTrue(WereadDataGate.tryBeginSync())
        assertFalse(WereadDataGate.tryBeginImport())
        WereadDataGate.endImport()
        assertFalse(WereadDataGate.tryBeginSync())
        WereadDataGate.endSync()
        assertTrue(WereadDataGate.tryBeginImport())
    }

    @Test
    fun `等待导出时不受理同步和第二次导入`() {
        assertTrue(WereadDataGate.tryBeginImport())
        assertFalse(WereadDataGate.tryBeginSync())
        assertFalse(WereadDataGate.tryBeginImport())
        WereadDataGate.endSync()
        assertFalse(WereadDataGate.tryBeginSync())
        WereadDataGate.endImport()
        assertTrue(WereadDataGate.tryBeginSync())
    }
}
