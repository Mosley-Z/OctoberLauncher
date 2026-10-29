package com.shuiyu.octoberrain.feature.files.ui.edit

import com.shuiyu.octoberrain.feature.files.data.FrameMode

/**
 * 裁剪页签的控制条定义。
 *
 * 第一行为纵横比选择，第二行为应用与重置；选中本机尺寸后第二行展开裁剪、拉伸、背景
 * 三种模式，应用按钮按当前比例提交裁剪框或本机尺寸参数。
 */
internal object CropPanel {
    /**
     * 生成裁剪页签的控制条行。
     *
     * @param state 当前界面状态，用于标记选中的纵横比与本机模式。
     * @param onAspect 纵横比选择回调。
     * @param onMode 本机尺寸模式选择回调。
     * @param onApply 应用回调，语义由当前纵横比决定。
     * @param onReset 重置裁剪选择回调。
     * @return 两行按钮定义，矩形由视图布局阶段补充。
     */
    fun rows(
        state: EditUiState,
        onAspect: (CropAspect) -> Unit,
        onMode: (FrameMode) -> Unit,
        onApply: () -> Unit,
        onReset: () -> Unit,
    ): List<List<EditChip>> {
        val aspects =
            CropAspect.entries.map { aspect ->
                EditChip(
                    label = aspect.label,
                    selected = state.aspect == aspect,
                    action = { onAspect(aspect) },
                )
            }
        val actions =
            buildList {
                val native = state.aspect == CropAspect.NATIVE
                if (native) {
                    FrameMode.entries.forEach { mode ->
                        add(
                            EditChip(
                                label = mode.label,
                                selected = state.frameMode == mode,
                                action = { onMode(mode) },
                            ),
                        )
                    }
                }
                val applyLabel = if (native) EditStrings.FRAME_APPLY else EditStrings.CROP_APPLY
                add(EditChip(label = applyLabel, selected = false, action = onApply))
                add(EditChip(label = EditStrings.CROP_RESET, selected = false, action = onReset))
            }
        return listOf(aspects, actions)
    }
}
