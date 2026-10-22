package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.DesktopConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 旧配置取消隐藏或保留排序键，也不能恢复第二个书架入口。 */
class DesktopEntriesTest {
    @Test
    fun legacyVisibleShelfKeyDoesNotCreateShelfEntry() {
        val desktop =
            DesktopConfig(hiddenSpecials = emptySet(), order = listOf("special:bookshelf", "special:settings"))
        val keys = DesktopEntries.build(emptyList(), desktop).map { it.key }
        assertFalse("special:bookshelf" in keys)
        assertTrue("special:settings" in keys)
        assertTrue("special:files" in keys)
    }
}
