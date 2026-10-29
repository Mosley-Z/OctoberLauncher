package com.shuiyu.octoberrain.feature.files.ui.edit

import com.shuiyu.octoberrain.feature.files.data.EditOp
import com.shuiyu.octoberrain.feature.files.data.FrameMode
import com.shuiyu.octoberrain.feature.files.data.FrameSpec
import com.shuiyu.octoberrain.feature.files.ui.GrayKernels
import com.shuiyu.octoberrain.feature.files.ui.GrayParams

/**
 * 导出参数组装。
 *
 * 几何操作由编辑会话维护，滤镜与对比度在保存时追加到末尾，保证 `applyOps` 先做几何变换、
 * 最后统一应用滤镜；本机尺寸参数追加在最后，导出统一使用不透明像素。
 */
object EditExportPlan {
    /**
     * 组装完整操作序列。
     *
     * 参数 base 为会话内已提交的几何操作；filterLevel 与 contrastLevel 为界面选择的档位；
     * frame 为待应用的本机尺寸操作，为空表示不使用本机尺寸。返回新列表，不修改输入；
     * 档位越界时按滤镜核常量夹取。
     */
    fun ops(
        base: List<EditOp>,
        filterLevel: Int,
        contrastLevel: Int,
        frame: EditOp.Frame? = null,
    ): List<EditOp> {
        val level = filterLevel.coerceIn(0, GrayKernels.LEVELS - 1)
        val contrast = contrastLevel.coerceIn(0, GrayKernels.CONTRASTS - 1)
        val result = ArrayList<EditOp>(base.size + 3)
        result.addAll(base)
        result.add(EditOp.Filter(level))
        result.add(EditOp.Contrast(contrast))
        if (frame != null && frame.spec.isValid) result.add(frame)
        return result
    }

    /**
     * 组装滤镜参数。
     *
     * 参数 filterLevel 与 contrastLevel 为界面档位；返回导出使用的不透明参数。
     */
    fun grayParams(
        filterLevel: Int,
        contrastLevel: Int,
    ): GrayParams =
        GrayParams(
            level = filterLevel.coerceIn(0, GrayKernels.LEVELS - 1),
            contrast = contrastLevel.coerceIn(0, GrayKernels.CONTRASTS - 1),
            keepAlpha = false,
        )

    /**
     * 判断当前选择是否可以保存。
     *
     * 参数 state 为编辑页状态；有已提交操作、滤镜非首档或对比度非标准档时允许保存，
     * 避免生成与源图相同的副本。
     */
    fun canSave(state: EditUiState): Boolean = state.hasEdits

    /**
     * 另存页面摘要文案。
     *
     * 参数 operationCount 为已提交的几何操作数；filterLabel 与 contrastLabel 为档位文案；
     * crop 为待应用裁剪框；frame 为待应用的本机尺寸参数。返回两至三行摘要，供界面直接绘制。
     */
    fun summary(
        operationCount: Int,
        filterLabel: String,
        contrastLabel: String,
        crop: NormRect,
        frame: FrameSpec? = null,
    ): String {
        val percent = "${(crop.width * 100f).toInt()}% × ${(crop.height * 100f).toInt()}%"
        val base = "操作 $operationCount 步 · 滤镜 $filterLabel · 对比度 $contrastLabel\n裁剪范围 $percent"
        if (frame == null || !frame.isValid) return base
        return base + "\n本机 ${frame.label} · ${frame.mode.label}" + backgroundNote(frame)
    }

    /** 背景模式的灰度补充说明，其余模式返回空串。 */
    private fun backgroundNote(frame: FrameSpec): String =
        if (frame.mode == FrameMode.BACKGROUND) {
            " 灰度 ${frame.safeGray}"
        } else {
            ""
        }
}
