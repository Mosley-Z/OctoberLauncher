package com.shuiyu.octoberrain.feature.files.ui.edit

/**
 * 裁剪页签的控制条定义。
 *
 * 第一行为纵横比选择，第二行为应用裁剪与重置。
 */
internal object CropPanel {
    /**
     * 生成裁剪页签的控制条行。
     *
     * @param state 当前界面状态，用于标记选中的纵横比。
     * @param onAspect 纵横比选择回调。
     * @param onApply 应用裁剪回调。
     * @param onReset 重置裁剪选择回调。
     * @return 两行按钮定义，矩形由视图布局阶段补充。
     */
    fun rows(
        state: EditUiState,
        onAspect: (CropAspect) -> Unit,
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
            listOf(
                EditChip(label = EditStrings.CROP_APPLY, selected = false, action = onApply),
                EditChip(label = EditStrings.CROP_RESET, selected = false, action = onReset),
            )
        return listOf(aspects, actions)
    }
}
