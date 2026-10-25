package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 页面目标展示标签的回归用例。
 *
 * 标签用于状态栏标题与占位页文案，需保证每个目标均有非空名称，
 * 且不同语义的页面不出现相同名称。
 */
class DestinationLabelsTest {
    /** 覆盖全部页面目标的样列表，包含单例目标与带参数的页面。 */
    private val destinations: List<Destination> =
        listOf(
            Destination.Home,
            Destination.Negative,
            Destination.Settings,
            Destination.SystemSettings,
            Destination.LauncherPrefs,
            Destination.Appearance,
            Destination.DesktopPrefs,
            Destination.TrayPrefs,
            Destination.LockSettings,
            Destination.Permissions,
            Destination.About,
            Destination.Update,
            Destination.WidgetPicker,
            Destination.IconPackPicker,
            Destination.Beautify,
            Destination.BeautifyPreview,
            Destination.IconPicker("com.example.reader"),
            Destination.FontPicker,
            Destination.Bookshelf,
            Destination.BookshelfSettings,
            Destination.Files(),
            Destination.Files("/sdcard/Books"),
            Destination.Weread,
            Destination.ProjectWidgets,
            Destination.ProjectRefresh,
            Destination.ProjectContent,
            Destination.ProjectAbout,
            Destination.Search,
            Destination.AppInfo("com.example.reader"),
            Destination.Backup,
            Destination.Book,
            Destination.Note,
            Destination.NegativeSettings,
            Destination.Todo,
        )

    /** 每个页面目标返回非空标签。 */
    @Test
    fun everyDestinationHasLabel() {
        destinations.forEach { destination ->
            assertTrue(
                "目标 $destination 的标签为空",
                destination.displayLabel().isNotBlank(),
            )
        }
    }

    /** 负一屏与书架使用不同标签，避免状态栏出现重复名称。 */
    @Test
    fun negativePageLabelDiffersFromBookshelf() {
        assertEquals("负一屏", Destination.Negative.displayLabel())
        assertNotEquals(
            Destination.Bookshelf.displayLabel(),
            Destination.Negative.displayLabel(),
        )
    }

    /** 带参数页面的不同实例返回相同标签。 */
    @Test
    fun parameterizedDestinationsKeepSameLabel() {
        assertEquals(
            Destination.IconPicker("com.example.a").displayLabel(),
            Destination.IconPicker("com.example.b").displayLabel(),
        )
        assertEquals(
            Destination.Files().displayLabel(),
            Destination.Files("/sdcard/Books").displayLabel(),
        )
        assertEquals(
            Destination.AppInfo("com.example.a").displayLabel(),
            Destination.AppInfo("com.example.b").displayLabel(),
        )
    }
}
