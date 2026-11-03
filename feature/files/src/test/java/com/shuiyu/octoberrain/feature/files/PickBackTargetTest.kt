package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.ui.pickBackTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 目标目录选择态返回上级的落点测试。
 *
 * 来源目录不再作为返回边界：非来源目录与来源目录本身都回到上级，
 * 只有根目录或存储卷根才返回 null，由页面取消本次批量动作。
 */
class PickBackTargetTest {
    private val parents =
        mapOf(
            "/storage/emulated/0/Pictures/Download" to "/storage/emulated/0/Pictures",
            "/storage/emulated/0/Pictures" to "/storage/emulated/0",
            "/storage/emulated/0" to null,
        )

    /** 非来源目录返回其上级。 */
    @Test
    fun `非来源目录返回上级`() {
        val target = pickBackTarget("/storage/emulated/0/Pictures/Download") { parents[it] }

        assertEquals("/storage/emulated/0/Pictures", target)
    }

    /** 来源目录本身同样返回上级，不再停留在来源目录。 */
    @Test
    fun `来源目录也返回上级`() {
        val target = pickBackTarget("/storage/emulated/0/Pictures") { parents[it] }

        assertEquals("/storage/emulated/0", target)
    }

    /** 已到存储卷根时没有上级，返回 null 表示取消。 */
    @Test
    fun `到根目录时取消`() {
        val target = pickBackTarget("/storage/emulated/0") { parents[it] }

        assertNull(target)
    }
}
