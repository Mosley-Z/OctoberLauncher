package com.shuiyu.octoberrain.feature.files.ui

import com.shuiyu.octoberrain.feature.files.data.FileSortKey

/**
 * 操作面板的显示状态快照。
 *
 * 只包含面板绘制与命中判断需要的字段，与偏好存储解耦，便于在本地 JVM 单元测试中构造。
 *
 * 参数 sortKey 为当前排序依据；ascending 表示升序；showHidden 表示显示隐藏文件；
 * grid 表示当前为网格视图。
 */
internal data class BrowsePanelState(
    val sortKey: FileSortKey,
    val ascending: Boolean,
    val showHidden: Boolean,
    val grid: Boolean,
)

/** 操作面板行，顺序与绘制顺序一致。 */
internal enum class PanelRow(
    val label: String,
) {
    /** 按名称排序。 */
    NAME(BrowseStrings.SORT_NAME),

    /** 按修改时间排序。 */
    TIME(BrowseStrings.SORT_TIME),

    /** 按文件大小排序。 */
    SIZE(BrowseStrings.SORT_SIZE),

    /** 按扩展名排序。 */
    TYPE(BrowseStrings.SORT_TYPE),

    /** 切换升降序。 */
    ORDER(BrowseStrings.ORDER),

    /** 切换是否显示隐藏文件。 */
    HIDDEN(BrowseStrings.HIDDEN),

    /** 切换网格与列表视图。 */
    VIEW(BrowseStrings.VIEW),

    /** 打开自选文件夹。 */
    FOLDERS(BrowseStrings.FOLDERS),
}

/** 长按条目后弹出的操作面板。 */
internal enum class PressedRow(
    val label: String,
) {
    /** 打开条目。 */
    OPEN(BrowseStrings.OPEN),

    /** 查看详情。 */
    DETAIL(BrowseStrings.DETAIL),

    /** 删除普通文件。 */
    DELETE("删除文件"),
}

/** 面板行触发的动作。 */
internal enum class PanelAction {
    SORT_NAME,
    SORT_TIME,
    SORT_SIZE,
    SORT_TYPE,
    TOGGLE_ORDER,
    TOGGLE_HIDDEN,
    TOGGLE_VIEW,
    MANAGE_FOLDERS,
}

/**
 * 操作面板模型。
 *
 * 行标签、选中标记与命中换算均为纯函数，绘制与触摸共用同一份定义；
 * 触发的偏好写入与页面刷新由浏览页处理，本对象不访问存储。
 */
internal object BrowsePanel {
    /** 操作面板全部行。 */
    val rows: List<PanelRow> = PanelRow.entries

    /** 长按面板全部行。 */
    fun pressedRows(directory: Boolean): List<PressedRow> =
        if (directory) listOf(PressedRow.OPEN, PressedRow.DETAIL) else PressedRow.entries

    /** 取面板行的动作。 */
    fun actionOf(row: PanelRow): PanelAction =
        when (row) {
            PanelRow.NAME -> PanelAction.SORT_NAME
            PanelRow.TIME -> PanelAction.SORT_TIME
            PanelRow.SIZE -> PanelAction.SORT_SIZE
            PanelRow.TYPE -> PanelAction.SORT_TYPE
            PanelRow.ORDER -> PanelAction.TOGGLE_ORDER
            PanelRow.HIDDEN -> PanelAction.TOGGLE_HIDDEN
            PanelRow.VIEW -> PanelAction.TOGGLE_VIEW
            PanelRow.FOLDERS -> PanelAction.MANAGE_FOLDERS
        }

    /** 判断排序行是否为当前排序依据。 */
    fun isSelected(
        row: PanelRow,
        state: BrowsePanelState,
    ): Boolean =
        when (row) {
            PanelRow.NAME -> state.sortKey == FileSortKey.NAME
            PanelRow.TIME -> state.sortKey == FileSortKey.TIME
            PanelRow.SIZE -> state.sortKey == FileSortKey.SIZE
            PanelRow.TYPE -> state.sortKey == FileSortKey.TYPE
            PanelRow.ORDER -> false
            PanelRow.HIDDEN -> state.showHidden
            PanelRow.VIEW -> state.grid
            PanelRow.FOLDERS -> false
        }

    /**
     * 取面板行右侧状态文案。
     *
     * 参数 row 为面板行；state 为当前显示状态。排序行仅在命中时返回已选标记，
     * 其余行返回开关或形态说明，自选文件夹行返回空串。
     */
    fun stateText(
        row: PanelRow,
        state: BrowsePanelState,
    ): String =
        when (row) {
            PanelRow.NAME,
            PanelRow.TIME,
            PanelRow.SIZE,
            PanelRow.TYPE,
            -> if (isSelected(row, state)) BrowseStrings.SELECTED else ""
            PanelRow.ORDER -> if (state.ascending) BrowseStrings.ASCENDING else BrowseStrings.DESCENDING
            PanelRow.HIDDEN -> if (state.showHidden) BrowseStrings.ON else BrowseStrings.OFF
            PanelRow.VIEW -> if (state.grid) BrowseStrings.GRID else BrowseStrings.LIST
            PanelRow.FOLDERS -> ""
        }

    /**
     * 按纵向坐标换算面板行下标。
     *
     * 参数 y 为视图纵坐标；top 为面板首行上边界；rowHeight 为行高；
     * count 为面板行数。命中范围之外返回 -1。
     */
    fun rowAt(
        y: Int,
        top: Int,
        rowHeight: Int,
        count: Int,
    ): Int {
        if (rowHeight <= 0 || count <= 0) return -1
        val index = (y - top) / rowHeight
        return if (y >= top && index in 0 until count) index else -1
    }
}
