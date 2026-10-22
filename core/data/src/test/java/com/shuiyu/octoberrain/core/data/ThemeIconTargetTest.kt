package com.shuiyu.octoberrain.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 主题图标跨设备映射的单元测试。
 *
 * 覆盖四种场景：同设备包名全部命中、跨设备按应用序号补位、
 * 部分命中时补位目标不重复占用、不在顺序表中的包名不参与补位。
 */
class ThemeIconTargetTest {
    /** 主题保存时的顺序表，前四项为带图标的包名。 */
    private val themeOrder =
        listOf(
            "app:com.a.one",
            "app:com.a.two",
            "app:com.a.three",
            "app:com.a.four",
            "app:com.a.five",
            "special:settings",
        )

    /** 同一台设备应用主题时，包名全部直接命中。 */
    @Test
    fun `同设备按包名直接命中`() {
        val targets =
            resolveThemeIconTargets(
                sourcePackages = listOf("com.a.one", "com.a.two"),
                themeOrder = themeOrder,
                deviceOrder = themeOrder,
            )

        assertEquals(mapOf("com.a.one" to "com.a.one", "com.a.two" to "com.a.two"), targets)
    }

    /** 换到另一台设备后，缺失的包名按应用序号补位。 */
    @Test
    fun `跨设备按应用序号补位`() {
        val deviceOrder =
            listOf(
                "app:com.b.alpha",
                "app:com.b.beta",
                "app:com.b.gamma",
                "app:com.b.delta",
                "special:settings",
            )
        val targets =
            resolveThemeIconTargets(
                sourcePackages = listOf("com.a.two", "com.a.four"),
                themeOrder = themeOrder,
                deviceOrder = deviceOrder,
            )

        // com.a.two 是主题第 2 个应用、com.a.four 是第 4 个，对应设备第 2 与第 4 个应用。
        assertEquals(
            mapOf("com.a.two" to "com.b.beta", "com.a.four" to "com.b.delta"),
            targets,
        )
    }

    /** special 条目在设备顺序中排在前面时不占用应用序号。 */
    @Test
    fun `special 条目不占用应用序号`() {
        val deviceOrder =
            listOf(
                "app:com.b.alpha",
                "special:settings",
                "special:files",
                "app:com.b.beta",
                "app:com.b.gamma",
            )
        val targets =
            resolveThemeIconTargets(
                sourcePackages = listOf("com.a.two", "com.a.three"),
                themeOrder = themeOrder,
                deviceOrder = deviceOrder,
            )

        assertEquals(
            mapOf("com.a.two" to "com.b.beta", "com.a.three" to "com.b.gamma"),
            targets,
        )
    }

    /** 部分包名命中时，命中的保持自身，补位目标已被占用则跳过。 */
    @Test
    fun `部分命中时补位不重复占用`() {
        val deviceOrder =
            listOf(
                "app:com.a.one",
                "app:com.b.beta",
                "special:settings",
            )
        val targets =
            resolveThemeIconTargets(
                sourcePackages = listOf("com.a.one", "com.a.two", "com.a.three"),
                themeOrder = themeOrder,
                deviceOrder = deviceOrder,
            )

        // com.a.one 直接命中；com.a.two 是主题第 2 个应用，补到设备第 2 个应用 com.b.beta；
        // com.a.three 是主题第 3 个应用，设备只有两个应用条目，无法承接。
        assertEquals(
            mapOf("com.a.one" to "com.a.one", "com.a.two" to "com.b.beta"),
            targets,
        )
    }

    /** 主题顺序表缺少该包名时不参与补位。 */
    @Test
    fun `不在顺序表中的包名不补位`() {
        val targets =
            resolveThemeIconTargets(
                sourcePackages = listOf("com.unknown.app"),
                themeOrder = themeOrder,
                deviceOrder = listOf("app:com.b.alpha"),
            )

        assertEquals(emptyMap<String, String>(), targets)
    }

    @Test
    fun specialIconsMatchIdentityAndNeverFillAnAppSlot() {
        val result =
            resolveThemeIconTargets(
                sourcePackages = listOf("special:settings", "special:files", "special:weread"),
                themeOrder = listOf("special:settings", "special:files", "special:weread"),
                deviceOrder = listOf("special:files", "app:example.app", "special:settings"),
            )
        assertEquals(mapOf("special:settings" to "special:settings", "special:files" to "special:files"), result)
    }
}
