package com.shuiyu.octoberrain.core.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

/** 壁纸替换事务的顺序与异常分支测试。 */
class WallpaperSwapTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("wallpaper-swap").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `替换成功后写入新文件并删除旧文件`() {
        val old = fileOf("wallpaper_1.png", "old")
        var updated: String? = null
        val swap = swap(updated = { updated = it })
        val result = swap.execute("wallpaper", 2L, old.absolutePath)
        assertTrue(result)
        val target = File(directory, "wallpaper_2.png")
        assertEquals("new", target.readText())
        assertFalse(old.exists())
        assertEquals(target.absolutePath, updated)
    }

    @Test
    fun `来源无法读取时保留旧文件`() {
        val old = fileOf("wallpaper_1.png", "old")
        val result = swap(source = null).execute("wallpaper", 2L, old.absolutePath)
        assertFalse(result)
        assertTrue(old.exists())
        assertFalse(File(directory, "wallpaper_2.png").exists())
    }

    @Test
    fun `图片校验失败时清理新文件并保留旧文件`() {
        val old = fileOf("wallpaper_1.png", "old")
        val result = swap(valid = false).execute("wallpaper", 2L, old.absolutePath)
        assertFalse(result)
        assertTrue(old.exists())
        assertFalse(File(directory, "wallpaper_2.png").exists())
    }

    @Test
    fun `配置更新失败时清理新文件并保留旧文件`() {
        val old = fileOf("wallpaper_1.png", "old")
        val result = swap(accepted = false).execute("wallpaper", 2L, old.absolutePath)
        assertFalse(result)
        assertTrue(old.exists())
        assertFalse(File(directory, "wallpaper_2.png").exists())
    }

    @Test
    fun `目标目录不可写时保留旧文件`() {
        val old = fileOf("wallpaper_1.png", "old")
        val missing = File(directory, "missing/deeper")
        val swap =
            WallpaperSwap(
                directory = missing,
                openSource = { ByteArrayInputStream("new".toByteArray()) },
                isValidImage = { true },
                updateConfig = { true },
            )
        val result = swap.execute("wallpaper", 2L, old.absolutePath)
        assertFalse(result)
        assertTrue(old.exists())
        assertFalse(missing.exists())
    }

    @Test
    fun `没有旧壁纸时也能成功写入`() {
        val result = swap().execute("wallpaper", 2L, null)
        assertTrue(result)
        assertEquals("new", File(directory, "wallpaper_2.png").readText())
    }

    @Test
    fun `旧路径为空字符串时跳过删除`() {
        val result = swap().execute("wallpaper", 2L, "")
        assertTrue(result)
    }

    @Test
    fun `旧文件不存在时不报错`() {
        val result = swap().execute("wallpaper", 2L, File(directory, "absent.png").absolutePath)
        assertTrue(result)
    }

    @Test
    fun `时间戳冲突时追加序号`() {
        fileOf("wallpaper_2.png", "existing")
        val result = swap().execute("wallpaper", 2L, null)
        assertTrue(result)
        assertEquals("existing", File(directory, "wallpaper_2.png").readText())
        assertEquals("new", File(directory, "wallpaper_2_2.png").readText())
    }

    @Test
    fun `桌面与锁屏前缀互不覆盖`() {
        swap().execute("wallpaper", 3L, null)
        swap().execute("lock", 3L, null)
        assertTrue(File(directory, "wallpaper_3.png").exists())
        assertTrue(File(directory, "lock_3.png").exists())
    }

    @Test
    fun `保存结果按校验回调区分失败原因`() {
        assertEquals(
            LockWallpaperResult.APPLIED,
            lockWallpaperResultOf(applied = true, validated = true),
        )
        assertEquals(
            LockWallpaperResult.APPLIED,
            lockWallpaperResultOf(applied = true, validated = null),
        )
        assertEquals(
            LockWallpaperResult.NOT_IMAGE,
            lockWallpaperResultOf(applied = false, validated = false),
        )
        assertEquals(
            LockWallpaperResult.CONFIG_FAILED,
            lockWallpaperResultOf(applied = false, validated = true),
        )
        assertEquals(
            LockWallpaperResult.READ_FAILED,
            lockWallpaperResultOf(applied = false, validated = null),
        )
    }

    /** 构造测试事务，来源内容固定为 new，校验与配置结果可切换。 */
    private fun swap(
        source: ByteArrayInputStream? = ByteArrayInputStream("new".toByteArray()),
        valid: Boolean = true,
        accepted: Boolean = true,
        updated: (String) -> Unit = {},
    ): WallpaperSwap =
        WallpaperSwap(
            directory = directory,
            openSource = { source },
            isValidImage = { valid },
            updateConfig = { path ->
                if (accepted) updated(path)
                accepted
            },
        )

    /** 在测试目录下写入指定文件。 */
    private fun fileOf(
        name: String,
        content: String,
    ): File = File(directory, name).apply { writeText(content) }
}
