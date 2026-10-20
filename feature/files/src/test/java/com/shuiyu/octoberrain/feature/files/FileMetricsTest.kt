package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.core.device.DeviceProfile
import com.shuiyu.octoberrain.core.device.DisplayClass
import com.shuiyu.octoberrain.core.device.HeightClass
import com.shuiyu.octoberrain.core.device.SizeClass
import com.shuiyu.octoberrain.feature.files.ui.FileMetrics
import com.shuiyu.octoberrain.feature.files.ui.FileTokens
import org.junit.Assert.assertEquals
import org.junit.Test

/** 文件页几何测试：按三档目标设备核对 dp 换算、内容区与网格单元格。 */
class FileMetricsTest {
    /** 480×800、219 dpi 的小屏设备。 */
    @Test
    fun `小屏设备几何按密度换算`() {
        val metrics = metricsOf(width = 480, height = 800, dpi = 219)
        val tokens = metrics.tokens
        assertEquals(1.36875f, tokens.density, 0.00001f)
        assertEquals(25, tokens.padH)
        assertEquals(77, tokens.topBarH)
        assertEquals(71, tokens.bottomBarH)
        assertEquals(60, tokens.rowH)
        assertEquals(41, tokens.rowThumb)
        assertEquals(8, tokens.gap)
        assertEquals(22, tokens.cellLabelH)
        assertEquals(16, tokens.slop)
        assertEquals(60, tokens.touchMin)
        assertEquals(2, tokens.stroke)
        assertEquals(77, metrics.contentTop)
        assertEquals(729, metrics.contentBottom)
        assertEquals(652, metrics.contentH)
        assertEquals(10, metrics.listRows)
        assertEquals(101, metrics.cellW)
        assertEquals(134, metrics.cellH)
        assertEquals(156, metrics.cellTotalH)
        assertEquals(16, metrics.pageSize)
    }

    /** 552×768、213 dpi 的横向握持设备。 */
    @Test
    fun `横向设备几何按密度换算`() {
        val metrics = metricsOf(width = 552, height = 768, dpi = 213)
        val tokens = metrics.tokens
        assertEquals(1.33125f, tokens.density, 0.00001f)
        assertEquals(24, tokens.padH)
        assertEquals(75, tokens.topBarH)
        assertEquals(69, tokens.bottomBarH)
        assertEquals(59, tokens.rowH)
        assertEquals(40, tokens.rowThumb)
        assertEquals(8, tokens.gap)
        assertEquals(21, tokens.cellLabelH)
        assertEquals(16, tokens.slop)
        assertEquals(59, tokens.touchMin)
        assertEquals(75, metrics.contentTop)
        assertEquals(699, metrics.contentBottom)
        assertEquals(624, metrics.contentH)
        assertEquals(10, metrics.listRows)
        assertEquals(120, metrics.cellW)
        assertEquals(160, metrics.cellH)
        assertEquals(181, metrics.cellTotalH)
        // 四行网格合计 748 像素，超过内容区 624 像素，P2 需要整页缩放或裁剪
        assertEquals(748, metrics.gridContentH)
    }

    /** 824×1648、320 dpi 的大屏设备。 */
    @Test
    fun `大屏设备几何按密度换算`() {
        val metrics = metricsOf(width = 824, height = 1648, dpi = 320)
        val tokens = metrics.tokens
        assertEquals(2.0f, tokens.density, 0.00001f)
        assertEquals(36, tokens.padH)
        assertEquals(112, tokens.topBarH)
        assertEquals(104, tokens.bottomBarH)
        assertEquals(88, tokens.rowH)
        assertEquals(60, tokens.rowThumb)
        assertEquals(12, tokens.gap)
        assertEquals(32, tokens.cellLabelH)
        assertEquals(24, tokens.slop)
        assertEquals(88, tokens.touchMin)
        assertEquals(112, metrics.contentTop)
        assertEquals(1544, metrics.contentBottom)
        assertEquals(1432, metrics.contentH)
        assertEquals(16, metrics.listRows)
        assertEquals(179, metrics.cellW)
        assertEquals(238, metrics.cellH)
        assertEquals(270, metrics.cellTotalH)
        assertEquals(1116, metrics.gridContentH)
    }

    /** 字号与描边由令牌派生，不出现固定像素。 */
    @Test
    fun `字号与描边由设备密度派生`() {
        val tokens = tokensOf(width = 480, height = 800, dpi = 219)
        assertEquals(23.26875f, tokens.titleText, 0.00001f)
        assertEquals(19.1625f, tokens.bodyText, 0.00001f)
        assertEquals(16.425f, tokens.smallText, 0.00001f)
        assertEquals(2, tokens.stroke)
    }

    /** 内容区高度不足以容纳一行时，列表行数至少为一行。 */
    @Test
    fun `内容区过矮时列表行数保底为一行`() {
        val metrics = metricsOf(width = 480, height = 200, dpi = 219)
        assertEquals(1, metrics.listRows)
    }

    /** 高度小于顶栏加底栏时，内容区下边界不越过上边界。 */
    @Test
    fun `内容区下边界不越过上边界`() {
        val metrics = metricsOf(width = 480, height = 100, dpi = 219)
        assertEquals(metrics.contentTop, metrics.contentBottom)
        assertEquals(0, metrics.contentH)
        assertEquals(1, metrics.listRows)
    }

    /** 网格固定四列四行，每页 16 项。 */
    @Test
    fun `网格保持四列四行`() {
        assertEquals(4, FileMetrics.GRID_COLS)
        assertEquals(4, FileMetrics.GRID_ROWS)
        assertEquals(16, FileMetrics.GRID_PAGE)
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
