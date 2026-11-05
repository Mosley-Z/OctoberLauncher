package com.shuiyu.octoberrain.feature.files.ui

/**
 * 文件页文案。
 *
 * 文案集中在模块内声明，不新增资源文件，避免与宿主资源名前缀约定冲突；
 * 单元测试可直接引用这些常量核对面板标签。
 */
internal object BrowseStrings {
    /** 页面默认标题。 */
    const val TITLE = "十雨文件"

    /** 底栏上一页按钮。 */
    const val PREVIOUS = "上一页"

    /** 底栏下一页按钮。 */
    const val NEXT = "下一页"

    /** 底栏动作按钮，打开操作面板。 */
    const val MENU = "菜单"

    /** 底栏动作按钮，关闭操作面板。 */
    const val CLOSE = "关闭"

    /** 空目录提示。 */
    const val EMPTY = "此目录为空"

    /** 图片查看能力尚未接入时的提示。 */
    const val VIEW_PENDING = "图片查看将在后续版本提供"

    /** 普通文件打开能力尚未接入时的提示。 */
    const val OPEN_PENDING = "打开文件将在后续版本提供"

    /** 存储权限未授予时的说明。 */
    const val PERMISSION = "需要存储权限才能浏览文件"

    /** 打开系统存储设置的按钮。 */
    const val OPEN_SETTINGS = "打开存储设置"

    /** 操作面板标题。 */
    const val PANEL_TITLE = "排序与显示"

    /** 面板排序行标签。 */
    const val SORT_NAME = "按名称"
    const val SORT_TIME = "按时间"
    const val SORT_SIZE = "按大小"
    const val SORT_TYPE = "按类型"

    /** 面板升降序行标签。 */
    const val ORDER = "升降序"

    /** 升序与降序文案。 */
    const val ASCENDING = "升序"
    const val DESCENDING = "降序"

    /** 面板隐藏文件行标签与状态。 */
    const val HIDDEN = "隐藏文件"
    const val ON = "开"
    const val OFF = "关"

    /** 面板视图行标签与状态。 */
    const val VIEW = "视图"
    const val GRID = "网格"
    const val LIST = "列表"

    /** 面板多选行标签。 */
    const val MULTI = "多选"

    /** 多选态顶部标题与动作行按钮。 */
    const val COPY_TO = "复制到"
    const val MOVE_TO = "移动到"
    const val DELETE = "删除"
    const val DONE = "完成"

    /** 选择目标目录时顶栏右侧动作按钮。 */
    const val PICK_HERE = "选择此处"

    /** 选择目标目录时顶栏取消按钮。 */
    const val CANCEL = "取消"

    /** 外部目录选择器未指定标题时的默认标题。 */
    const val PICK_DIR = "选择目录"

    /** 删除确认文案，包含选中数量与不可恢复说明。 */
    const val DELETE_CONFIRM = "确定删除选中的 %d 项？"
    const val DELETE_WARNING = "目录将连同内容一并删除，删除后无法恢复。"

    /** 批量操作完成后的汇总提示。 */
    const val BATCH_RESULT = "成功 %d 项，失败 %d 项"

    /** 选中的目录被复制或移动到自身内部时的提示。 */
    const val INVALID_TARGET = "不能将选中的目录复制或移动到自身内部"

    /** 多选态已选数量文案。 */
    fun selectedCount(count: Int): String = "已选 $count 项"

    /** 删除确认正文，拼接数量与不可恢复说明。 */
    fun deleteMessage(count: Int): String = DELETE_CONFIRM.format(count) + DELETE_WARNING

    /** 批量操作结果提示，成功与失败数量均展示。 */
    fun batchResult(
        done: Int,
        failed: Int,
    ): String = BATCH_RESULT.format(done, failed)

    /** 已选中标记。 */
    const val SELECTED = "已选"

    /** 长按面板的打开与详情行。 */
    const val OPEN = "打开"
    const val DETAIL = "详情"

    /** 缩略图加载中的占位文案。 */
    const val IMAGE_LOADING = "图片"
}
