package com.shuiyu.octoberrain.feature.files.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 编辑页入口来源映射的纯 JVM 测试。
 *
 * 覆盖文件列表、查看器、外部打开与未指定四种输入，确保外部入口不会被误判为可覆盖。
 */
class EditSourceTest {
    /** 文件列表路由映射为文件列表来源。 */
    @Test
    fun `文件列表路由映射为文件列表`() {
        assertEquals(EditSource.FILES, EditSource.fromRoute("files"))
    }

    /** 查看器路由、未指定与未知标识都映射为查看器来源。 */
    @Test
    fun `查看器与未指定来源映射为查看器`() {
        assertEquals(EditSource.VIEWER, EditSource.fromRoute("viewer"))
        assertEquals(EditSource.VIEWER, EditSource.fromRoute(null))
        assertEquals(EditSource.VIEWER, EditSource.fromRoute("unknown"))
    }

    /** 外部打开路由映射为外部来源，编辑页据此只提供另存。 */
    @Test
    fun `外部打开路由映射为外部来源`() {
        assertEquals(EditSource.EXTERNAL, EditSource.fromRoute("external"))
    }
}
