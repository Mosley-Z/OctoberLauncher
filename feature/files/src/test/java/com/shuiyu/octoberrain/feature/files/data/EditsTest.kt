package com.shuiyu.octoberrain.feature.files.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 旋转另存规则测试：核对角度循环、命名、输出目录与键名约定。 */
class EditsTest {
    /** 角度按 90 度递增，四次回到原方向。 */
    @Test
    fun `角度循环四次回到原方向`() {
        assertEquals(90, Edits.nextAngle(0))
        assertEquals(180, Edits.nextAngle(1))
        assertEquals(270, Edits.nextAngle(2))
        assertEquals(0, Edits.nextAngle(3))
        assertEquals(90, Edits.nextAngle(4))
    }

    /** 负数次数按零处理，不出现负角度。 */
    @Test
    fun `负数次数按零处理`() {
        assertEquals(90, Edits.nextAngle(-1))
        assertEquals(90, Edits.nextAngle(-4))
    }

    /** 输出文件名为原名加编辑标记与时间戳，扩展名统一为 PNG。 */
    @Test
    fun `输出文件名格式`() {
        val name = Edits.outputName("photo.jpg", 0L)
        assertTrue(name.startsWith("photo-$EDIT_NAME_TAG-"))
        assertTrue(name.endsWith(EDIT_OUTPUT_EXTENSION))
        assertTrue(Regex("photo-编-\\d{8}-\\d{6}\\.png").matches(name))
    }

    /** 无扩展名的原名原样保留，空白名回退为 image。 */
    @Test
    fun `无扩展名与空白名`() {
        assertTrue(Edits.outputName("photo", 0L).startsWith("photo-$EDIT_NAME_TAG-"))
        assertTrue(Edits.outputName("", 0L).startsWith("image-$EDIT_NAME_TAG-"))
    }

    /** 缓存来源写入编辑输出目录，外部来源写入源文件目录。 */
    @Test
    fun `输出目录按来源判定`() {
        val cacheDir = File("build/tmp/cache")
        val editRoot = File("build/tmp/Pictures/十雨桌面")
        val cached = File(cacheDir, "viewer_1_photo.jpg")
        val external = File("build/tmp/Pictures/photo.jpg")
        assertEquals(editRoot, Edits.targetDirectory(cached, cacheDir, editRoot))
        assertEquals(external.parentFile, Edits.targetDirectory(external, cacheDir, editRoot))
    }

    /** 源文件无父目录时回退到编辑输出目录。 */
    @Test
    fun `无父目录时回退`() {
        val cacheDir = File("build/tmp/cache")
        val editRoot = File("build/tmp/Pictures/十雨桌面")
        assertEquals(editRoot, Edits.targetDirectory(File("photo.jpg"), cacheDir, editRoot))
    }

    /** 旋转计数键名格式固定，供编辑会话读取。 */
    @Test
    fun `旋转计数键名`() {
        val path = "/storage/emulated/0/Pictures/a.jpg"
        assertEquals("edited_rotations:$path", ROTATION_KEY_PREFIX + path)
        assertEquals(".png", EDIT_OUTPUT_EXTENSION)
        assertEquals("编", EDIT_NAME_TAG)
    }
}
