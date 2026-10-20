package com.shuiyu.octoberrain.feature.files.ui.edit

/**
 * 旋转页签的控制条定义。
 *
 * 第一行为顺时针旋转，第二行为撤销一步编辑。
 */
internal object RotatePanel {
    /**
     * 生成旋转页签的控制条行。
     *
     * @param onRotate 顺时针旋转 90 度回调。
     * @param onUndo 撤销一步回调。
     * @return 两行按钮定义，矩形由视图布局阶段补充。
     */
    fun rows(
        onRotate: () -> Unit,
        onUndo: () -> Unit,
    ): List<List<EditChip>> =
        listOf(
            listOf(EditChip(label = EditStrings.ROTATE_BUTTON, selected = false, action = onRotate)),
            listOf(EditChip(label = EditStrings.UNDO, selected = false, action = onUndo)),
        )
}
