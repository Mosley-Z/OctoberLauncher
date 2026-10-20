package com.shuiyu.octoberrain.feature.files.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.TimeZone

/**
 * [EditExport] 的纯 JVM 测试。
 *
 * 覆盖长边档位、时间戳与文件名规则、重名追加以及无目录时的退化行为。
 * 写入 MediaStore 与编码依赖 Android 框架，放在真机验收覆盖。
 */
class EditExportTest {
    /** 内存档位低于阈值使用小图长边。 */
    @Test
    fun `低内存档取小图长边`() {
        assertEquals(1600, EditExport.targetEdge(128))
        assertEquals(1600, EditExport.targetEdge(191))
    }

    /** 内存档位达到阈值使用大图长边。 */
    @Test
    fun `高内存档取大图长边`() {
        assertEquals(2400, EditExport.targetEdge(192))
        assertEquals(2400, EditExport.targetEdge(512))
    }

    /** 时间戳按固定时区输出到秒。 */
    @Test
    fun `时间戳格式`() {
        assertEquals("19700101-000000", EditExport.stamp(0L, UTC))
        assertEquals("19700102-000000", EditExport.stamp(86_400_000L, UTC))
    }

    /** 文件名由原名、编辑标记与时间戳拼成。 */
    @Test
    fun `导出文件名规则`() {
        assertEquals("photo-编-19700101-000000.png", EditExport.displayName("photo", 0L, UTC))
    }

    /** 目录内已有同名文件时追加序号。 */
    @Test
    fun `重名追加序号`() {
        val dir = Files.createTempDirectory("edit-export").toFile()
        try {
            File(dir, "photo-编-19700101-000000.png").createNewFile()
            assertEquals("photo-编-19700101-000000_2.png", EditExport.uniqueName(dir, "photo", 0L, UTC))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 目录内无冲突时保持原名。 */
    @Test
    fun `无冲突保持原名`() {
        val dir = Files.createTempDirectory("edit-export").toFile()
        try {
            assertEquals(
                "photo-编-19700101-000000.png",
                EditExport.uniqueName(dir, "photo", 0L, UTC),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 没有可用目录时直接返回首选名。 */
    @Test
    fun `无目录返回首选名`() {
        assertEquals("photo-编-19700101-000000.png", EditExport.uniqueName(null, "photo", 0L, UTC))
    }

    /** 导出常量与决策文档一致。 */
    @Test
    fun `导出常量与目录名`() {
        assertEquals(192, EditExport.MEMORY_THRESHOLD_MB)
        assertEquals(1600, EditExport.TARGET_EDGE_LOW)
        assertEquals(2400, EditExport.TARGET_EDGE_HIGH)
        assertEquals("十雨桌面", EXPORT_DIRECTORY)
    }

    private companion object {
        private val UTC: TimeZone = TimeZone.getTimeZone("GMT")
    }
}
