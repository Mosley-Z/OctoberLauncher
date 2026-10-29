package com.shuiyu.octoberrain.feature.files.ui.edit

import com.shuiyu.octoberrain.feature.files.data.CropRect
import com.shuiyu.octoberrain.feature.files.data.EditOp
import com.shuiyu.octoberrain.feature.files.data.FrameMath
import com.shuiyu.octoberrain.feature.files.data.FrameMode
import com.shuiyu.octoberrain.feature.files.data.FrameSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本机尺寸纯逻辑测试。
 *
 * 覆盖几何换算、状态切换、控制条展开与导出参数组装，验证界面交互依赖的判定。
 */
class NativeFrameTest {
    @Test
    fun resizedNativeCropKeepsPixelRatioAtBoundary() {
        val ratio = 0.6f * 400f / 800f
        val result = CropDrag.resize(NormRect(0.35f, 0f, 0.65f, 1f), CropTarget.BOTTOM_RIGHT, 1f, 1f, ratio, 0.01f)
        assertEquals(0.6f, (result.right - result.left) * 800f / ((result.bottom - result.top) * 400f), 0.001f)
    }

    /** 等比放置时至少一组对边贴画布，另外两边留白。 */
    @Test
    fun fitSticksOneEdgePair() {
        val vertical = FrameMath.fit(width = 480, height = 800, sourceWidth = 800, sourceHeight = 800)
        assertEquals(480, vertical.width)
        assertEquals(480, vertical.height)
        assertEquals(160, vertical.top)
        assertEquals(0, vertical.left)

        val horizontal = FrameMath.fit(width = 480, height = 800, sourceWidth = 800, sourceHeight = 400)
        assertEquals(480, horizontal.width)
        assertEquals(240, horizontal.height)
        assertEquals(280, horizontal.top)
    }

    /** 居中内接裁剪框按像素比例计算并回到归一化坐标。 */
    @Test
    fun centeredCropCentersBothPages() {
        val portrait = FrameMath.centeredCrop(ratio = 0.6f, imageWidth = 800, imageHeight = 800)
        assertEquals(0.2f, portrait.left, 0.001f)
        assertEquals(0f, portrait.top, 0.001f)
        assertEquals(0.8f, portrait.right, 0.001f)
        assertEquals(1f, portrait.bottom, 0.001f)

        val square = FrameMath.centeredCrop(ratio = 1f, imageWidth = 800, imageHeight = 800)
        assertEquals(0f, square.top, 0.001f)
        assertEquals(1f, square.bottom, 0.001f)
    }

    /** 背景灰度越界时夹取到 0 与 255。 */
    @Test
    fun grayClamps() {
        assertEquals(0, FrameMath.clampGray(-5))
        assertEquals(0, FrameMath.clampGray(0))
        assertEquals(255, FrameMath.clampGray(300))
        assertEquals(128, FrameMath.clampGray(128))
    }

    /** 目标尺寸无效时整体无效。 */
    @Test
    fun targetValidity() {
        assertFalse(NativeTarget(0, 800).isValid)
        assertFalse(NativeTarget(480, 0).isValid)
        assertTrue(NativeTarget(480, 800).isValid)
        assertEquals(0.6f, NativeTarget(480, 800).ratio, 0.001f)
        assertEquals("480 × 800", NativeTarget(480, 800).label)
        assertEquals("纵向", NativeTarget(480, 800).orientationLabel)
        assertEquals("横向", NativeTarget(800, 480).orientationLabel)
    }

    /** 进入本机尺寸后待应用参数为空，选择模式后才生成。 */
    @Test
    fun pendingFrameNeedsMode() {
        val target = NativeTarget(480, 800)
        val entered = EditUiState().withNativeTarget(target)
        assertEquals(CropAspect.NATIVE, entered.aspect)
        assertEquals(target, entered.native)
        assertNull(entered.pendingFrame)

        val cropped = entered.withFrameMode(FrameMode.CROP)
        assertEquals(FrameMode.CROP, cropped.pendingFrame?.mode)
        assertTrue(cropped.hasEdits)
    }

    /** 本机尺寸下裁剪框比例取目标比例，其他比例取选项比例。 */
    @Test
    fun effectiveRatioFollowsAspect() {
        val state = EditUiState().withNativeTarget(NativeTarget(480, 800))
        assertEquals(0.6f, state.effectiveRatio()!!, 0.001f)
        assertNull(state.withAspect(CropAspect.FREE).effectiveRatio())
        assertEquals(1f, state.withAspect(CropAspect.SQUARE).effectiveRatio()!!, 0.001f)
    }

    /** 离开本机尺寸清除目标与模式。 */
    @Test
    fun leavingNativeClearsTarget() {
        val state =
            EditUiState()
                .withNativeTarget(NativeTarget(480, 800))
                .withFrameMode(FrameMode.STRETCH)
                .withAspect(CropAspect.SQUARE)
        assertNull(state.native)
        assertNull(state.frameMode)
        assertNull(state.pendingFrame)
    }

    /** 重置选择清除本机尺寸参数，保留已提交操作。 */
    @Test
    fun resetSelectionKeepsOperations() {
        val state =
            EditUiState(operationCount = 2)
                .withNativeTarget(NativeTarget(480, 800))
                .withFrameMode(FrameMode.BACKGROUND)
                .withFrameGray(64)
                .resetSelection()
        assertEquals(2, state.operationCount)
        assertNull(state.native)
        assertNull(state.frameMode)
        assertEquals(FrameMath.MAX_GRAY, state.frameGray)
    }

    /** 背景灰度夹取后写入待应用参数。 */
    @Test
    fun backgroundGrayInFrameSpec() {
        val state =
            EditUiState()
                .withNativeTarget(NativeTarget(480, 800))
                .withFrameMode(FrameMode.BACKGROUND)
                .withFrameGray(999)
        assertEquals(FrameMath.MAX_GRAY, state.pendingFrame?.safeGray)
    }

    /** 本机尺寸展开为三种模式加应用，普通比例只有应用与重置。 */
    @Test
    fun cropPanelRowsExpandNativeModes() {
        val free =
            CropPanel.rows(
                state = EditUiState(),
                onAspect = {},
                onMode = {},
                onApply = {},
                onReset = {},
            )
        assertEquals(2, free[1].size)
        assertEquals(EditStrings.CROP_APPLY, free[1][0].label)

        val native =
            CropPanel.rows(
                state = EditUiState().withNativeTarget(NativeTarget(480, 800)),
                onAspect = {},
                onMode = {},
                onApply = {},
                onReset = {},
            )
        assertEquals(2 + FrameMode.entries.size, native[1].size)
        assertEquals(
            FrameMode.entries.map { it.label },
            native[1].take(FrameMode.entries.size).map { it.label },
        )
        assertEquals(EditStrings.FRAME_APPLY, native[1][FrameMode.entries.size].label)
    }

    /** 导出参数在末尾追加本机尺寸操作，无效参数被忽略。 */
    @Test
    fun exportOpsAppendFrame() {
        val frame = EditOp.Frame(FrameSpec(480, 800, FrameMode.CROP, 255), CropRect.FULL)
        val ops = EditExportPlan.ops(listOf(EditOp.Rotate(1)), filterLevel = 0, contrastLevel = 1, frame = frame)
        assertEquals(4, ops.size)
        assertTrue(ops.last() is EditOp.Frame)

        val invalid = EditOp.Frame(FrameSpec(0, 800, FrameMode.CROP, 255), CropRect.FULL)
        val skipped = EditExportPlan.ops(emptyList(), 0, 1, frame = invalid)
        assertFalse(skipped.last() is EditOp.Frame)
    }

    /** 另存摘要包含本机尺寸与模式，背景模式附带灰度。 */
    @Test
    fun summaryShowsFrame() {
        val crop = EditExportPlan.summary(0, "灰阶", "标准", NormRect.FULL)
        assertTrue(crop.contains("裁剪范围"))

        val background =
            EditExportPlan.summary(
                0,
                "灰阶",
                "标准",
                NormRect.FULL,
                FrameSpec(480, 800, FrameMode.BACKGROUND, 255),
            )
        assertTrue(background.contains("本机 480 × 800"))
        assertTrue(background.contains("背景"))
        assertTrue(background.contains("灰度 255"))
    }

    /** 有本机尺寸参数时允许保存。 */
    @Test
    fun canSaveWithFrame() {
        val state =
            EditUiState()
                .withNativeTarget(NativeTarget(480, 800))
                .withFrameMode(FrameMode.STRETCH)
        assertTrue(EditExportPlan.canSave(state))
        assertFalse(EditExportPlan.canSave(EditUiState()))
    }
}
