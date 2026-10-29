package com.shuiyu.octoberrain.feature.files.ui.edit

/**
 * 编辑页文案集合。
 *
 * 集中声明界面文案，避免绘制代码内出现分散的字面量，便于后续整体替换与核对。
 */
internal object EditStrings {
    /** 页面标题。 */
    const val TITLE = "编辑图片"

    /** 顶栏取消。 */
    const val CANCEL = "取消"

    /** 顶栏保存。 */
    const val SAVE = "保存"

    /** 取消确认标题。 */
    const val DISCARD_TITLE = "放弃编辑"

    /** 取消确认正文。 */
    const val DISCARD_MESSAGE = "尚未保存的修改将丢失。"

    /** 取消确认按钮。 */
    const val DISCARD_CONFIRM = "放弃"

    /** 保存确认标题。 */
    const val SAVE_TITLE = "保存编辑结果"

    /** 保存确认正文。 */
    const val SAVE_MESSAGE = "结果将写入 Pictures/十雨桌面，是否继续？"

    /** 保存确认按钮。 */
    const val SAVE_CONFIRM = "保存"

    /** 无修改时的保存提示。 */
    const val NOTHING_TO_SAVE = "当前没有需要保存的修改"

    /** 保存中提示。 */
    const val SAVING = "正在保存…"

    /** 保存成功。 */
    const val SAVED = "已保存到 Pictures/十雨桌面"

    /** 保存失败前缀。 */
    const val SAVE_FAILED = "保存失败："

    /** 读取中。 */
    const val LOADING = "正在读取图片"

    /** 读取失败。 */
    const val LOAD_FAILED = "无法读取图片"

    /** 缺少存储权限。 */
    const val NO_PERMISSION = "缺少存储权限，无法读取图片"

    /** 旋转页按钮。 */
    const val ROTATE_BUTTON = "顺时针旋转 90°"

    /** 旋转页重置按钮。 */
    const val ROTATE_RESET = "撤销全部旋转"

    /** 旋转页撤销按钮。 */
    const val UNDO = "撤销一步"

    /** 操作步数达到上限时的提示。 */
    const val OPERATION_LIMIT = "操作步数已达上限"

    /** 裁剪范围过小时的提示。 */
    const val CROP_TOO_SMALL = "裁剪范围过小"

    /** 裁剪页应用按钮。 */
    const val CROP_APPLY = "应用裁剪"

    /** 本机尺寸应用按钮。 */
    const val FRAME_APPLY = "应用本机尺寸"

    /** 无法读取屏幕尺寸时的提示。 */
    const val NATIVE_UNAVAILABLE = "无法读取本机屏幕尺寸"

    /** 背景灰度标签前缀。 */
    const val GRAY_LABEL = "灰度 "

    /** 裁剪页重置按钮。 */
    const val CROP_RESET = "重置"

    /** 滤镜页滤镜标题。 */
    const val FILTER_TITLE = "滤镜"

    /** 滤镜页对比度标题。 */
    const val CONTRAST_TITLE = "对比度"

    /** 另存页操作提示。 */
    const val SAVE_HINT = "点击底部保存写入文件"

    /** 另存页旋转摘要。 */
    const val ROTATION_SUMMARY = "旋转"

    /** 页码分隔符。 */
    const val SEPARATOR = " · "
}
