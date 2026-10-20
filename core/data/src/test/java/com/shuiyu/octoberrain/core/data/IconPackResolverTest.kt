package com.shuiyu.octoberrain.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图标包解析器中纯逻辑部分的单元测试。
 *
 * 覆盖图标包标识归一化、组件属性解析、索引优先级与 assets 候选文件名；
 * 读取图标包资源与缩放位图依赖 Android 框架，不在本地单测范围内。
 */
class IconPackResolverTest {
    /** 空白与内置标识归一为未启用，其余标识去除首尾空白。 */
    @Test
    fun `图标包标识归一化`() {
        assertNull(normalizePackId(null))
        assertNull(normalizePackId(""))
        assertNull(normalizePackId("   "))
        assertNull(normalizePackId("builtin"))
        assertNull(normalizePackId(" builtin "))
        assertEquals("com.example.pack", normalizePackId("com.example.pack"))
        assertEquals("com.example.pack", normalizePackId("  com.example.pack  "))
    }

    /** 组件属性四种写法都能解析出包名与活动名。 */
    @Test
    fun `解析组件属性`() {
        assertEquals(
            ComponentRef("com.example.app", "com.example.app.Main"),
            parseComponentRef("ComponentInfo{com.example.app/com.example.app.Main}"),
        )
        assertEquals(
            ComponentRef("com.example.app", null),
            parseComponentRef("ComponentInfo{com.example.app}"),
        )
        assertEquals(
            ComponentRef("com.example.app", "com.example.app.Main"),
            parseComponentRef("com.example.app/com.example.app.Main"),
        )
        assertEquals(
            ComponentRef("com.example.app", null),
            parseComponentRef("com.example.app"),
        )
        // 活动名可以省略包名前缀
        assertEquals(
            ComponentRef("com.example.app", ".Main"),
            parseComponentRef("com.example.app/.Main"),
        )
    }

    /** 空值与残缺写法返回 null。 */
    @Test
    fun `非法组件属性返回空`() {
        assertNull(parseComponentRef(null))
        assertNull(parseComponentRef(""))
        assertNull(parseComponentRef("   "))
        assertNull(parseComponentRef("ComponentInfo{}"))
        assertNull(parseComponentRef("ComponentInfo{ }"))
        assertNull(parseComponentRef("/com.example.app.Main"))
    }

    /** 索引同时建立精确映射与包名映射，重复组件保留首条记录。 */
    @Test
    fun `构建索引且首条优先`() {
        val index =
            buildPackIndex(
                sequenceOf(
                    "ComponentInfo{com.example.app/com.example.app.Main}" to "first",
                    "ComponentInfo{com.example.app/com.example.app.Main}" to "second",
                    "ComponentInfo{com.example.app}" to "package_level",
                ),
            )
        assertEquals("first", index.exact["com.example.app/com.example.app.Main"])
        assertEquals("first", index.byPackage["com.example.app"])
    }

    /** 查询优先精确匹配，未命中时回退包名映射。 */
    @Test
    fun `图标查询先精确后包名`() {
        val index =
            buildPackIndex(
                sequenceOf(
                    "ComponentInfo{com.example.app}" to "package_level",
                    "ComponentInfo{com.example.app/com.example.app.Main}" to "exact_level",
                ),
            )
        assertEquals("exact_level", index.drawableFor("com.example.app", "com.example.app.Main"))
        assertEquals("package_level", index.drawableFor("com.example.app", null))
        assertEquals("package_level", index.drawableFor("com.example.app", ""))
        // 启动活动不匹配时回退到包名映射
        assertEquals("package_level", index.drawableFor("com.example.app", "com.example.app.Other"))
        assertNull(index.drawableFor("com.example.other", "com.example.other.Main"))
    }

    /** 缺少组件或图标的条目被忽略，图标名去除首尾空白。 */
    @Test
    fun `忽略残缺条目并整理图标名`() {
        val index =
            buildPackIndex(
                sequenceOf(
                    null to null,
                    "ComponentInfo{com.example.app}" to "",
                    "ComponentInfo{com.example.app}" to "   ",
                    null to "orphan",
                    "com.example.app/com.example.app.Main" to "  padded  ",
                ),
            )
        assertEquals("padded", index.drawableFor("com.example.app", "com.example.app.Main"))
        // 带活动名的条目同时写入包名映射，因此不带活动名查询也命中同一图标
        assertEquals("padded", index.drawableFor("com.example.app", null))
        assertEquals(setOf("com.example.app/com.example.app.Main"), index.exact.keys)
        assertEquals(setOf("com.example.app"), index.byPackage.keys)
    }

    /** 图标包 assets 查找顺序为根目录 png、webp 与 icons 子目录 png、webp。 */
    @Test
    fun `assets 候选文件名顺序`() {
        assertEquals(
            listOf("clock.png", "clock.webp", "icons/clock.png", "icons/clock.webp"),
            assetCandidates("clock"),
        )
        assertEquals(
            listOf("a.png", "a.webp", "icons/a.png", "icons/a.webp"),
            assetCandidates("  a  "),
        )
        assertTrue(assetCandidates("   ").isEmpty())
    }
}
