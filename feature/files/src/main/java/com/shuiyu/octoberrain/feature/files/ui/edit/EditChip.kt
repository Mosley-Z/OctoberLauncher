package com.shuiyu.octoberrain.feature.files.ui.edit

/**
 * 编辑页控制条按钮。
 *
 * @property label 按钮文案。
 * @property selected 是否处于选中态，选中时以墨色填充。
 * @property action 点击回调。
 * @property rect 布局阶段写入的命中矩形。
 */
internal data class EditChip(
    val label: String,
    val selected: Boolean,
    val action: () -> Unit,
    val rect: IntRect = IntRect(0, 0, 0, 0),
)
