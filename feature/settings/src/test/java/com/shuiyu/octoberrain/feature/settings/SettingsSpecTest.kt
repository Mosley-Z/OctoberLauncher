package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.IconShape
import com.shuiyu.octoberrain.core.common.LauncherConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 声明表自身约束的单元测试。
 *
 * 覆盖标识唯一性、分组引用完整性、选项数量与取值换算，
 * 用于防止后续追加设置项时出现重复标识或悬空分组。
 */
class SettingsSpecTest {
    @Test
    fun `全部设置项标识唯一`() {
        val ids = SettingsCatalog.specs.map { it.id }
        assertEquals("设置项标识存在重复：$ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `每个设置项都归属已声明的分组`() {
        val groups = SettingsCatalog.groups.map { it.id }.toSet()
        SettingsCatalog.specs.forEach { spec ->
            assertTrue("设置项 ${spec.id} 的分组 ${spec.group} 未声明", spec.group in groups)
        }
    }

    @Test
    fun `每个分组至少有一个设置项`() {
        SettingsCatalog.groups.forEach { group ->
            assertTrue("分组 ${group.id} 没有设置项", SettingsCatalog.specsOf(group.id).isNotEmpty())
        }
    }

    @Test
    fun `选项类控件的候选文本非空且与取值一一对应`() {
        SettingsCatalog.specs.forEach { spec ->
            when (val control = spec.control) {
                is SettingControl.Choice -> assertOptions(spec.id, control.options)
                is SettingControl.Stepper -> assertOptions(spec.id, control.options)
                is SettingControl.Slider -> assertTrue("${spec.id} 的滑条档位为空", control.values.isNotEmpty())
                else -> Unit
            }
        }
    }

    @Test
    fun `不显示条件在默认配置下成立`() {
        val config = LauncherConfig()
        SettingsCatalog.specs.forEach { spec ->
            assertTrue("${spec.id} 在默认配置下不可见", spec.visible(config))
        }
    }

    @Test
    fun `自动刷新间隔的档位与取值可互相换算`() {
        val control = choiceOf("tray.refreshInterval")
        assertEquals(5, control.options.size)
        val base = LauncherConfig()
        // 默认间隔为十分钟，对应候选表第三项
        assertEquals(2, control.read(base))
        val off = control.write(base, 0)
        assertEquals(0, off.tray.autoRefreshMinutes)
        assertEquals(0, control.read(off))
        val half = control.write(base, 3)
        assertEquals(15, half.tray.autoRefreshMinutes)
        assertEquals(3, control.read(half))
    }

    @Test
    fun `状态栏高度档位换算到配置值`() {
        val control = choiceOf("appearance.statusBar")
        val base = LauncherConfig()
        val auto = control.write(base, 0)
        assertEquals(0, auto.appearance.statusBarHeightDp)
        val size28 = control.write(base, 1)
        assertEquals(28, size28.appearance.statusBarHeightDp)
        assertEquals(1, control.read(size28))
    }

    @Test
    fun `锁屏应急清除开关默认开启并可写入配置`() {
        val spec = SettingsCatalog.specOf("lock.emergencyReset")
        assertNotNull(spec)
        assertEquals(SettingsCatalog.GROUP_LOCK_SECURITY, spec?.group)
        val toggle = spec?.control as SettingControl.Toggle
        val base = LauncherConfig()
        assertTrue(toggle.read(base))
        val off = toggle.write(base, false)
        assertEquals(false, off.lock.emergencyResetEnabled)
        assertEquals(false, toggle.read(off))
    }

    @Test
    fun `图标形状与二值化档位写入外观配置`() {
        val shape = choiceOf("appearance.iconShape")
        val contrast = choiceOf("appearance.iconContrast")
        val base = LauncherConfig()
        assertEquals(IconShape.PILL, shape.write(base, 0).appearance.iconShape)
        assertEquals(IconShape.SQUARE, shape.write(base, 2).appearance.iconShape)
        assertEquals(1, shape.read(LauncherConfig(appearance = base.appearance.copy(iconShape = IconShape.ROUNDED))))
        assertEquals(3, contrast.write(base, 3).appearance.iconContrast)
        assertEquals(3, contrast.read(LauncherConfig(appearance = base.appearance.copy(iconContrast = 3))))
    }

    @Test
    fun `字号缩放滑条读写同一字段`() {
        val spec = SettingsCatalog.specOf("appearance.text.scale")
        assertNotNull(spec)
        val slider = spec?.control as SettingControl.Slider
        val base = LauncherConfig()
        assertEquals(1.0f, slider.read(base), 0.001f)
        val scaled = slider.write(base, 1.15f)
        assertEquals(1.15f, scaled.appearance.textScale, 0.001f)
        assertEquals(1.15f, slider.read(scaled), 0.001f)
    }

    /** 书架行数与书源已移交负一屏墨台设置，设置页不再声明该控件。 */
    @Test
    fun `书架行数不再由设置页声明`() {
        assertEquals(null, SettingsCatalog.specOf("bookshelf.rows.count"))
    }

    @Test
    fun `设置项可按标识检索且分组顺序稳定`() {
        assertEquals(
            listOf(
                SettingsCatalog.GROUP_APPEARANCE_WALLPAPER,
                SettingsCatalog.GROUP_APPEARANCE_THEME,
                SettingsCatalog.GROUP_APPEARANCE_FONTS,
                SettingsCatalog.GROUP_APPEARANCE_TEXT,
                SettingsCatalog.GROUP_APPEARANCE_IMAGE,
                SettingsCatalog.GROUP_APPEARANCE_DISPLAY,
            ),
            SettingsCatalog.appearanceGroups.map { it.id },
        )
        assertNotNull(SettingsCatalog.specOf("appearance.wallpaper.pick"))
        assertEquals(null, SettingsCatalog.specOf("appearance.unknown"))
    }

    @Test
    fun `图片与缓存分组包含四项设置`() {
        val ids = SettingsCatalog.specsOf(SettingsCatalog.GROUP_APPEARANCE_IMAGE).map { it.id }
        assertEquals(
            listOf(
                "appearance.image.directory",
                "appearance.image.directory.pick",
                "appearance.thumbnail.quality",
                "appearance.thumbnail.dither",
                "appearance.image.cache.size",
                "appearance.image.cache.clear",
            ),
            ids,
        )
    }

    @Test
    fun `缩略图质量档位写入配置并可读回`() {
        val control = choiceOf("appearance.thumbnail.quality")
        val base = LauncherConfig()
        assertEquals(1, control.read(base))
        val low = control.write(base, 0)
        assertEquals(0, low.appearance.thumbnailQuality)
        assertEquals(0, control.read(low))
        val sharp = control.write(base, 2)
        assertEquals(2, sharp.appearance.thumbnailQuality)
        assertEquals(2, control.read(sharp))
    }

    @Test
    fun `缩略图抖动开关写入配置并可读回`() {
        val control = SettingsCatalog.specOf("appearance.thumbnail.dither")?.control as SettingControl.Toggle
        val base = LauncherConfig()
        assertTrue(control.read(base))
        val off = control.write(base, false)
        assertEquals(false, off.appearance.thumbnailDither)
        assertEquals(false, control.read(off))
    }

    @Test
    fun `缩略图质量越界值回退默认档`() {
        val config = LauncherConfig(appearance = LauncherConfig().appearance.copy(thumbnailQuality = 9))
        assertEquals(1, config.appearance.effectiveThumbnailQuality)
        val negative = LauncherConfig(appearance = LauncherConfig().appearance.copy(thumbnailQuality = -3))
        assertEquals(1, negative.appearance.effectiveThumbnailQuality)
    }

    @Test
    fun `缓存占用文本按量级格式化`() {
        assertEquals("0.0 KB", formatCacheBytes(0L))
        assertEquals("1.0 KB", formatCacheBytes(1024L))
        assertEquals("1.0 MB", formatCacheBytes(1024L * 1024L))
        assertEquals("2.5 MB", formatCacheBytes((2.5 * 1024 * 1024).toLong()))
        assertEquals("0.0 KB", formatCacheBytes(-1L))
    }

    /** 断言选项文本非空且数量与取值一致。 */
    private fun assertOptions(
        id: String,
        options: List<SettingOption>,
    ) {
        assertTrue("$id 的候选为空", options.isNotEmpty())
        options.forEach { option ->
            assertTrue("$id 的候选文本为空", option.label.isNotBlank())
        }
        assertEquals("$id 的候选值重复", options.size, options.map { it.value }.toSet().size)
    }

    /** 取选项类控件，供换算用例复用。 */
    private fun choiceOf(id: String): SettingControl.Choice =
        SettingsCatalog.specOf(id)?.control as SettingControl.Choice
}
