package com.shuiyu.octoberrain.feature.files.ui

/**
 * 文件页文案。
 *
 * 文案集中在模块内声明，不新增资源文件，避免与宿主资源名前缀约定冲突；
 * 单元测试可直接引用这些常量核对面板标签。
 */
internal object BrowseStrings {
    /** 页面默认标题。 */
    const val TITLE = "文件"

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

    /** 取消勾选或面板时的提示，用于后续版本。 */
    const val FOLDERS_PENDING = "自选文件夹将在后续版本提供"

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

    /** 面板自选文件夹行标签。 */
    const val FOLDERS = "自选文件夹"

    /** 已选中标记。 */
    const val SELECTED = "已选"

    /** 长按面板的打开与详情行。 */
    const val OPEN = "打开"
    const val DETAIL = "详情"

    /** 缩略图加载中的占位文案。 */
    const val IMAGE_LOADING = "图片"
}
