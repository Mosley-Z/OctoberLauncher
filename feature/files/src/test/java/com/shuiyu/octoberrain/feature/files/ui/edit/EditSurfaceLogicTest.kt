package com.shuiyu.octoberrain.feature.files.ui.edit

import com.shuiyu.octoberrain.feature.files.data.EditOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑页纯逻辑单元测试。
 *
 * 覆盖裁剪命中优先级、裁剪拖动约束、归一化矩形夹取、页签与档位状态、预览尺寸与导出参数，
 * 不依赖 Android 图形类。
 */
class EditSurfaceLogicTest {
    private val frame = IntRect(100, 100, 300, 200)

    @Test
    fun `四角手柄优先于框内`() {
        assertEquals(CropTarget.TOP_LEFT, CropHitTest.hit(100, 100, frame, 20))
        assertEquals(CropTarget.TOP_RIGHT, CropHitTest.hit(300, 100, frame, 20))
        assertEquals(CropTarget.BOTTOM_LEFT, CropHitTest.hit(100, 200, frame, 20))
        assertEquals(CropTarget.BOTTOM_RIGHT, CropHitTest.hit(300, 200, frame, 20))
    }

    @Test
    fun `框内与框外判定`() {
        assertEquals(CropTarget.INSIDE, CropHitTest.hit(200, 150, frame, 10))
        assertEquals(CropTarget.OUTSIDE, CropHitTest.hit(50, 50, frame, 10))
        assertEquals(CropTarget.OUTSIDE, CropHitTest.hit(350, 150, frame, 10))
    }

    @Test
    fun `手柄半径为非正时按一像素处理`() {
        assertEquals(CropTarget.TOP_LEFT, CropHitTest.hit(100, 100, frame, 0))
        assertEquals(CropTarget.INSIDE, CropHitTest.hit(105, 105, frame, -5))
    }

    @Test
    fun `整体拖动保持尺寸并夹取边界`() {
        val start = NormRect(0.2f, 0.2f, 0.6f, 0.6f)
        val moved = CropDrag.move(start, 0.5f, -0.5f)
        assertEquals(0.4f, moved.width, 0.0001f)
        assertEquals(0.4f, moved.height, 0.0001f)
        assertEquals(1f, moved.right, 0.0001f)
        assertEquals(0f, moved.top, 0.0001f)
    }

    @Test
    fun `手柄拖动按目标比例约束`() {
        val start = NormRect(0.2f, 0.2f, 0.6f, 0.6f)
        val resized = CropDrag.resize(start, CropTarget.BOTTOM_RIGHT, 0.1f, 0f, 1f, 0.05f)
        assertEquals(resized.width, resized.height, 0.0001f)
        assertTrue(resized.isValid)
        assertEquals(0.2f, resized.left, 0.0001f)
        assertEquals(0.2f, resized.top, 0.0001f)
    }

    @Test
    fun `框内拖动不改变裁剪框`() {
        val start = NormRect(0.3f, 0.3f, 0.7f, 0.7f)
        assertEquals(start, CropDrag.resize(start, CropTarget.INSIDE, 0.1f, 0.1f, null, 0.02f))
    }

    @Test
    fun `归一化矩形夹取保证最小边长与边界`() {
        val tiny = NormRect(0.5f, 0.5f, 0.5f, 0.5f).clamp()
        assertTrue(tiny.width >= NormRect.MIN_SIZE)
        assertTrue(tiny.right <= 1f)
        val outside = NormRect(-1f, -1f, 2f, 2f).clamp()
        assertEquals(NormRect.FULL, outside)
    }

    @Test
    fun `页签循环切换`() {
        assertEquals(EditTab.SAVE, EditUiState(tab = EditTab.CROP).cycleTab(-1).tab)
        assertEquals(EditTab.CROP, EditUiState(tab = EditTab.SAVE).cycleTab(1).tab)
    }

    @Test
    fun `档位夹取与编辑状态判定`() {
        val state = EditUiState()
        assertEquals(4, state.withFilterLevel(9).filterLevel)
        assertEquals(0, state.withFilterLevel(-2).filterLevel)
        assertEquals(2, state.withContrastLevel(5).contrastLevel)
        assertFalse(state.hasEdits)
        assertTrue(state.withContrastLevel(0).hasEdits)
        assertTrue(state.withFilterLevel(2).hasEdits)
    }

    @Test
    fun `旋转重置裁剪框`() {
        val state = EditUiState(crop = NormRect(0.2f, 0.2f, 0.8f, 0.8f))
        val rotated = state.withRotation()
        assertEquals(1, rotated.rotationQuarters)
        assertEquals(NormRect.FULL, rotated.crop)
    }

    @Test
    fun `预览解码长边夹取`() {
        assertEquals(PreviewSizing.MAX_PREVIEW_EDGE * 3, PreviewSizing.decodeEdge(5000, 4000))
        assertEquals(32, PreviewSizing.decodeEdge(1, 1))
        assertEquals(720, PreviewSizing.decodeEdge(720, 300))
    }

    @Test
    fun `预览缩放与尺寸`() {
        assertEquals(1f, PreviewSizing.previewScale(100, 50), 0.0001f)
        assertEquals(0.5f, PreviewSizing.previewScale(720, 400), 0.0001f)
        val size = PreviewSizing.previewSize(720, 400)
        assertEquals(360, size[0])
        assertEquals(200, size[1])
        assertEquals(1, PreviewSizing.previewSize(0, 0)[0])
    }

    @Test
    fun `导出操作序列追加滤镜与对比度`() {
        val base = listOf<EditOp>(EditOp.Rotate(1))
        val result = EditExportPlan.ops(base, 9, -1)
        assertEquals(3, result.size)
        assertEquals(EditOp.Filter(4), result[1])
        assertEquals(EditOp.Contrast(0), result[2])
        assertEquals(1, base.size)
    }

    @Test
    fun `另存摘要包含范围百分比与步数`() {
        val summary = EditExportPlan.summary(2, "灰阶", "标准", NormRect(0f, 0f, 0.5f, 0.25f))
        assertTrue(summary.contains("操作 2 步"))
        assertTrue(summary.contains("50% × 25%"))
        assertFalse(EditExportPlan.canSave(EditUiState()))
        assertTrue(EditExportPlan.canSave(EditUiState(operationCount = 1)))
    }
}
