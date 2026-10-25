package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.BeautifyTheme
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.ThemeLayout
import com.shuiyu.octoberrain.core.common.ThemeScope
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeAppearanceTest {
    private val order = listOf("app:a", "app:b", "special:settings")

    private fun theme(
        scope: ThemeScope = ThemeScope.HOME,
        transparent: Set<String>? = null,
    ) = BeautifyTheme("test", "test", 0, order = listOf("app:a"), scope = scope, transparentIconTargets = transparent)

    @Test fun `old home theme preserves unrelated images and transparency`() {
        val current =
            AppearanceConfig(
                iconOverrides = mapOf("a" to "old-a", "b" to "old-b"),
                transparentIconTargets = setOf("special:settings"),
                showLabels = false,
            )
        val actual = mergeThemeAppearance(current, theme(), mapOf("a" to "new-a"), order)
        assertEquals(mapOf("a" to "new-a", "b" to "old-b"), actual.iconOverrides)
        assertEquals(setOf("special:settings"), actual.transparentIconTargets)
        assertEquals(false, actual.showLabels)
    }

    @Test fun `transparent targets never follow positional replacements`() {
        val actual = mergeThemeAppearance(AppearanceConfig(), theme(transparent = setOf("missing")), emptyMap(), order)
        assertEquals(emptySet<String>(), actual.transparentIconTargets)
    }

    @Test fun `home transparent removes same target image but retains other page`() {
        val current =
            AppearanceConfig(
                iconOverrides = mapOf("a" to "a", "b" to "b"),
                transparentIconTargets = setOf("special:settings"),
            )
        val actual = mergeThemeAppearance(current, theme(transparent = setOf("a")), emptyMap(), order)
        assertEquals(mapOf("b" to "b"), actual.iconOverrides)
        assertEquals(setOf("a", "special:settings"), actual.transparentIconTargets)
    }

    @Test fun `all explicit empty transparency clears previous flags`() {
        val actual =
            mergeThemeAppearance(
                AppearanceConfig(transparentIconTargets = setOf("b")),
                theme(ThemeScope.ALL, emptySet()),
                emptyMap(),
                order,
            )
        assertEquals(emptySet<String>(), actual.transparentIconTargets)
    }

    @Test fun `home layout leaves other page capacities unchanged`() {
        val current = DesktopConfig(columns = 5, rows = 7, listRows = 11)
        val actual =
            applyThemeLayout(
                current,
                ThemeLayout(columns = 3, rows = 4, firstPageRows = 2, listFirstRows = 6),
                ThemeScope.HOME,
            )
        assertEquals(5, actual.columns)
        assertEquals(7, actual.rows)
        assertEquals(11, actual.listRows)
        assertEquals(6, actual.listFirstRows)
    }

    @Test fun `legacy list layout restores old capacities`() {
        val actual = applyThemeLayout(DesktopConfig(), ThemeLayout(rows = 8, firstPageRows = 3), ThemeScope.ALL)
        assertEquals(8, actual.listRows)
        assertEquals(3, actual.listFirstRows)
    }
}
