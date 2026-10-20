package com.shuiyu.octoberrain.feature.files.ui.edit

/**
 * 另存页签的控制条定义。
 *
 * 第一行留空，第二行放置保存按钮；保存摘要由视图绘制。
 */
internal object SavePanel {
    /**
     * 生成另存页签的控制条行。
     *
     * @param onSave 保存按钮回调。
     * @return 两行按钮定义，矩形由视图布局阶段补充。
     */
    fun rows(onSave: () -> Unit): List<List<EditChip>> =
        listOf(
            emptyList(),
            listOf(EditChip(label = EditStrings.SAVE, selected = true, action = onSave)),
        )
}
