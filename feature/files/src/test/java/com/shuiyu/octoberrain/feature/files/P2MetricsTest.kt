package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.core.device.DeviceProfile
import com.shuiyu.octoberrain.core.device.DisplayClass
import com.shuiyu.octoberrain.core.device.HeightClass
import com.shuiyu.octoberrain.core.device.SizeClass
import com.shuiyu.octoberrain.feature.files.ui.FileMetrics
import com.shuiyu.octoberrain.feature.files.ui.FileMetrics.Companion.GRID_ROWS
import com.shuiyu.octoberrain.feature.files.ui.FileTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网格绘制高度测试。
 *
 * 核对三档设备在整屏与真实内容区高度下，四行缩略图与标签能否完整落在内容区内。
 */
class P2MetricsTest {
    /** 小屏整屏高度下四行无需压缩。 */
    @Test
    fun `小屏整屏四行保持标称缩略图高度`() {
        val metrics = metricsOf(width = 480, height = 800, dpi = 219)
        assertEquals(134, metrics.gridCellDrawH)
        assertEquals(648, metrics.gridDrawH)
        assertTrue(metrics.gridDrawH <= metrics.contentH)
    }

    /** 横向设备整屏高度下缩略图被压缩到恰好容纳四行。 */
    @Test
    fun `横向设备四行压缩后不超出内容区`() {
        val metrics = metricsOf(width = 552, height = 768, dpi = 213)
        assertEquals(129, metrics.gridCellDrawH)
        assertEquals(624, metrics.gridDrawH)
        assertEquals(metrics.contentH, metrics.gridDrawH)
    }

    /** 大屏设备内容区富余，缩略图保持 3:4。 */
    @Test
    fun `大屏设备四行保持标称缩略图高度`() {
        val metrics = metricsOf(width = 824, height = 1648, dpi = 320)
        assertEquals(238, metrics.gridCellDrawH)
        assertEquals(1116, metrics.gridDrawH)
        assertTrue(metrics.gridDrawH <= metrics.contentH)
    }

    /** 小屏真实内容区高度 753 时按四行反推并压缩缩略图。 */
    @Test
    fun `小屏真实内容区压缩缩略图`() {
        val metrics = metricsOf(width = 480, height = 753, dpi = 219)
        assertEquals(605, metrics.contentH)
        assertEquals(123, metrics.gridCellDrawH)
        assertEquals(604, metrics.gridDrawH)
        assertTrue(metrics.gridDrawH <= metrics.contentH)
    }

    /** 内容区过矮时缩略图高度保底一像素，行数仍为四行。 */
    @Test
    fun `内容区过矮时缩略图高度保底`() {
        val metrics = metricsOf(width = 480, height = 200, dpi = 219)
        assertEquals(1, metrics.gridCellDrawH)
        val expected = GRID_ROWS * (1 + metrics.tokens.cellLabelH) + (GRID_ROWS - 1) * metrics.tokens.gap
        assertEquals(expected, metrics.gridDrawH)
    }

    private fun metricsOf(
        width: Int,
        height: Int,
        dpi: Int,
    ): FileMetrics = FileMetrics(width, height, tokensOf(width, height, dpi))

    private fun tokensOf(
        width: Int,
        height: Int,
        dpi: Int,
    ): FileTokens = FileTokens(profileOf(width, height, dpi))

    private fun profileOf(
        width: Int,
        height: Int,
        dpi: Int,
    ): DeviceProfile {
        val density = dpi / 160f
        val widthDp = (width / density).toInt()
        val heightDp = (height / density).toInt()
        return DeviceProfile(
            widthPx = width,
            heightPx = height,
            widthDp = widthDp,
            heightDp = heightDp,
            density = density,
            sizeClass = SizeClass.fromWidth(widthDp),
            heightClass = HeightClass.fromHeight(heightDp),
            display = DisplayClass.EinkFast,
            hasFrontLight = true,
            statusBarInsetPx = 0,
            navigationInsetPx = 0,
            longEdgePx = maxOf(width, height),
            shortEdgePx = minOf(width, height),
        )
    }
}
